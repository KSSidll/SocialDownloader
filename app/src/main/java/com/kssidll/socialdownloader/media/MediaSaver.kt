package com.kssidll.socialdownloader.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.kssidll.socialdownloader.network.MOBILE_USER_AGENT
import com.kssidll.socialdownloader.network.httpClient
import com.kssidll.socialdownloader.util.asHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

private const val TAG = "MediaSaver"

/** Everything the app saves lands in one album, so it is easy to find and easy to clear out. */
private const val ALBUM_NAME = "SocialDownloader"

/**
 * Streams a URL into the device's media collections and returns the new item, or null if it could
 * not be saved.
 *
 * Goes through MediaStore rather than writing a raw path: from API 29 on, an app needs no storage
 * permission at all for media it creates this way, and the item shows up in the gallery by itself.
 *
 * [expectVideo] only decides where to file the item when the server doesn't say - the response's
 * own Content-Type wins, since it is the one source that knows what the bytes actually are.
 */
suspend fun saveMedia(context: Context, url: String, expectVideo: Boolean): Uri? =
    withContext(Dispatchers.IO) {
        val secureUrl = url.asHttps()
        Log.d(TAG, "saveMedia: requesting $secureUrl")

        val request = Request.Builder()
            .url(secureUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "saveMedia: HTTP ${response.code} for $secureUrl")
                    return@withContext null
                }

                val body = response.body
                if (body == null) {
                    Log.w(TAG, "saveMedia: no body for $secureUrl")
                    return@withContext null
                }

                // XHS image URLs end in things like "!h5_1080jpg", so the path is no help - the
                // declared content type is what the extension and collection get derived from.
                val mimeType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
                    ?: if (expectVideo) "video/mp4" else "image/jpeg"

                val isVideo = mimeType.startsWith("video/")
                val displayName = fileNameFor(secureUrl, mimeType)
                Log.d(TAG, "saveMedia: storing as $displayName ($mimeType)")

                val target = insertPending(context, displayName, mimeType, isVideo)
                if (target == null) {
                    Log.w(TAG, "saveMedia: MediaStore refused an entry for $displayName")
                    return@withContext null
                }

                try {
                    val written = context.contentResolver.openOutputStream(target)?.use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("no output stream for $target")

                    publish(context, target)
                    Log.d(TAG, "saveMedia: wrote $written bytes to $target")
                    target
                } catch (e: Exception) {
                    // The entry is still pending, so nothing else has seen it - drop it rather than
                    // leaving a truncated file in the user's gallery.
                    Log.e(TAG, "saveMedia: failed writing $secureUrl, discarding $target", e)
                    context.contentResolver.delete(target, null, null)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveMedia: request failed for $secureUrl", e)
            null
        }
    }

/**
 * Creates the row up front with IS_PENDING set, so nothing else picks the file up until the body
 * has finished copying.
 */
private fun insertPending(
    context: Context,
    displayName: String,
    mimeType: String,
    isVideo: Boolean,
): Uri? {
    val collection = if (isVideo) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    val parentDirectory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$parentDirectory/$ALBUM_NAME")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    return context.contentResolver.insert(collection, values)
}

private fun publish(context: Context, uri: Uri) {
    val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
    context.contentResolver.update(uri, values, null, null)
}

/**
 * Builds a filename from the URL's last path segment, stripped of anything a file name shouldn't
 * carry, with the extension taken from the declared type. MediaStore resolves collisions itself, so
 * saving the same post twice yields a second file rather than an overwrite.
 */
private fun fileNameFor(url: String, mimeType: String): String {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        ?: mimeType.substringAfter('/')

    val base = Uri.parse(url).lastPathSegment
        ?.substringBefore('!')
        ?.substringBeforeLast('.')
        ?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        ?.takeIf { it.isNotBlank() }
        ?.take(64)
        ?: "media"

    return "$base.$extension"
}
