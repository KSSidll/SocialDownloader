package com.kssidll.socialdownloader.network

import android.util.Log
import com.kssidll.socialdownloader.util.asHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "PageFetcher"

/**
 * Sites serve different (often stripped-down) markup to clients that don't look like a real
 * browser, and some CDNs turn media requests away on the same basis - so every request the app
 * makes, page or media, goes out behind this.
 */
internal const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

private val client = OkHttpClient()

/** Follows redirects (e.g. xhslink.cn short links) and returns the final page body, or null on failure. */
suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
    val secureUrl = url.asHttps()
    Log.d(TAG, "fetchHtml: requesting $secureUrl")

    val request = Request.Builder()
        .url(secureUrl)
        .header("User-Agent", MOBILE_USER_AGENT)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "fetchHtml: got HTTP ${response.code} for ${response.request.url}")

            if (!response.isSuccessful) {
                Log.w(TAG, "fetchHtml: HTTP ${response.code} for $secureUrl")
                return@withContext null
            }

            val body = response.body?.string()
            Log.d(TAG, "fetchHtml: body length ${body?.length ?: 0}")
            body
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchHtml: request failed for $secureUrl", e)
        null
    }
}

/**
 * Asks how large a resource is without pulling its body down.
 *
 * Uses a single-byte ranged GET rather than HEAD: media CDNs commonly refuse HEAD on signed URLs -
 * XHS answers 404 - while they have to honour range requests for playback to seek at all. A server
 * that ignores the range and answers 200 still reports the full size in Content-Length, and the
 * body is closed unread either way.
 *
 * Returns null whenever the server declines to say, which is a normal answer rather than a failure.
 */
suspend fun fetchContentLength(url: String): Long? = withContext(Dispatchers.IO) {
    val secureUrl = url.asHttps()

    val request = Request.Builder()
        .url(secureUrl)
        .header("User-Agent", MOBILE_USER_AGENT)
        .header("Range", "bytes=0-0")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            // "bytes 0-0/603660" - the total after the slash is the whole resource, whereas
            // Content-Length on a 206 is just the one byte we asked for.
            val fromRange = response.header("Content-Range")
                ?.substringAfter('/', "")
                ?.toLongOrNull()

            val length = (fromRange ?: response.header("Content-Length")?.toLongOrNull())
                ?.takeIf { it > 0 }

            Log.d(TAG, "fetchContentLength: HTTP ${response.code}, length $length for $secureUrl")
            length
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetchContentLength: request failed for $secureUrl", e)
        null
    }
}
