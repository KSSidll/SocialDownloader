package com.kssidll.socialdownloader.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.kssidll.socialdownloader.network.MOBILE_USER_AGENT
import com.kssidll.socialdownloader.network.fetchContentLength
import com.kssidll.socialdownloader.util.asHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "MediaProbe"

/** Ceiling on the decoded frame's height - a strip thumbnail never needs more than this. */
private const val THUMBNAIL_MAX_HEIGHT = 1080

/**
 * What could be learned about a piece of media by looking at the file itself. Every field is
 * optional: a source may refuse range requests, omit Content-Length, or hand back something the
 * platform can't decode. Images only ever fill in [sizeBytes] - Coil draws them, so there is
 * nothing else worth reading off the file.
 */
data class MediaProbe(
    val thumbnail: Bitmap? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
) {
    val isEmpty: Boolean get() = thumbnail == null && durationMs == null && sizeBytes == null
}

/**
 * Reads a poster frame and duration out of the video file itself, and its size off the response
 * headers.
 *
 * Deliberately reads the file rather than the page that linked to it. Every platform hands us an
 * mp4 URL, so this behaves the same whatever the source, where anything lifted out of a page's own
 * embedded JSON only ever works for the one site that emits that shape.
 */
suspend fun probeVideo(url: String): MediaProbe = coroutineScope {
    Log.d(TAG, "probeVideo: starting for $url")

    // The HEAD request and the frame decode don't depend on each other, so let them overlap.
    val size = async { fetchContentLength(url) }
    val frame = async { extractFrame(url) }

    val (thumbnail, durationMs) = frame.await()
    MediaProbe(thumbnail = thumbnail, durationMs = durationMs, sizeBytes = size.await())
        .also { Log.d(TAG, "probeVideo: finished for $url -> ${it.describe()}") }
}

private fun MediaProbe.describe(): String =
    "thumbnail=${thumbnail?.let { "${it.width}x${it.height}" }}, duration=$durationMs, size=$sizeBytes"

/**
 * Asks only how big an image is. There is no frame to pull and no duration to read - Coil is
 * already fetching and drawing the picture itself, so this exists purely so an image can report its
 * size the same way a video does.
 */
suspend fun probeImage(url: String): MediaProbe =
    MediaProbe(sizeBytes = fetchContentLength(url))
        .also { Log.d(TAG, "probeImage: finished for $url -> ${it.describe()}") }

/**
 * Pulls the first keyframe and the duration in a single pass.
 *
 * The retriever seeks with range requests, so this reads the container header and one keyframe
 * rather than dragging the whole video down for a thumbnail.
 */
private suspend fun extractFrame(url: String): Pair<Bitmap?, Long?> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()

    try {
        retriever.setDataSource(url.asHttps(), mapOf("User-Agent" to MOBILE_USER_AGENT))

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

        val width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
        val height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()

        val thumbnail = if (width != null && height != null && width > 0 && height > 0) {
            // Scale coming out of the decoder rather than decoding full resolution and shrinking
            // after. getScaledFrameAtTime wants both dimensions, so derive the width from the
            // source ratio - handing it a fixed box would stretch the frame.
            val targetHeight = height.coerceAtMost(THUMBNAIL_MAX_HEIGHT)
            val targetWidth = (width.toFloat() * targetHeight / height).roundToInt().coerceAtLeast(1)

            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight,
            )
        } else {
            Log.d(TAG, "extractFrame: no frame dimensions reported, decoding unscaled")
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

        thumbnail to durationMs
    } catch (e: Exception) {
        // Unreadable containers, refused range requests and expired signed URLs all land here, and
        // none of them are worth failing the whole picker over - the item just shows no preview.
        Log.w(TAG, "extractFrame: could not read $url", e)
        null to null
    } finally {
        retriever.release()
    }
}
