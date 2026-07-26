package com.kssidll.socialdownloader.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "PageFetcher"

/** XHS serves different (often stripped-down) markup to clients that don't look like a real browser. */
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

private val client = OkHttpClient()

/** Follows redirects (e.g. xhslink.cn short links) and returns the final page body, or null on failure. */
suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
    // Shared links are often handed out as plain http:// but Android blocks cleartext traffic by
    // default (targetSdk 28+) - upgrade to https instead of loosening the app's security policy.
    val secureUrl = url.replaceFirst("http://", "https://")
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
