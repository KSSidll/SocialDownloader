package com.kssidll.socialdownloader.xhs

import android.util.Log
import com.kssidll.socialdownloader.media.Media

private const val TAG = "XhsFilter"

/**
 * URL patterns marking a CDN hit as something other than the note's own media - profile pictures,
 * placeholders, backup copies, thumbnail style directives, images pulled in from the comments, and
 * the HEVC encode of a video we already have in H.264.
 */
private val rejectPatterns = listOf(
    "avatar" to Regex("""sns-avatar"""),
    "na" to Regex("""sns-na"""),
    "bak" to Regex("""sns-bak"""),
    "styleDirective" to Regex("""!style_"""),
    "comment" to Regex("""/comment/"""),
    "hevc" to Regex("""_309\.mp4"""),
)

fun filterXhs(media: Media?): Media? {
    if (media == null) {
        Log.d(TAG, "filterXhs: nothing to filter")
        return null
    }

    Log.d(TAG, "filterXhs: starting with ${media.images.size} image(s), ${media.videos.size} video(s)")

    val result = Media(
        images = media.images.filter(::isNoteMedia),
        videos = media.videos.filter(::isNoteMedia),
    )

    Log.d(TAG, "filterXhs: finished with ${result.images.size} image(s), ${result.videos.size} video(s)")

    return result.takeUnless { it.isEmpty }
}

/** Drops on the first pattern that matches, so every URL gets logged exactly once with its reason. */
private fun isNoteMedia(url: String): Boolean {
    val reject = rejectPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(url) }

    if (reject != null) {
        Log.d(TAG, "isNoteMedia: dropped $url (${reject.first})")
        return false
    }

    Log.d(TAG, "isNoteMedia: kept $url")
    return true
}
