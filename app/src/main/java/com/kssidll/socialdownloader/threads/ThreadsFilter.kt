package com.kssidll.socialdownloader.threads

import android.util.Log
import com.kssidll.socialdownloader.media.Media

private const val TAG = "ThreadsFilter"

/**
 * URL patterns marking a CDN hit as something other than the post's own media - here, Threads' own
 * static app icon, which `og:image` falls back to when a share link points at a post the page
 * declined to render (an invalid or deleted post redirects to a generic `?error=invalid_post` shell
 * rather than failing outright, and that shell's `og:image` is the app icon, not a photo).
 *
 * The payload step needs no equivalent: it is anchored on the post's own code (see
 * [findThreadsPost] in ThreadsParseSteps.kt), so nothing foreign gets that far in the first place.
 * `rsrc.php` is the tell - it is Meta's static asset bundler, serving site chrome and icons, and no
 * post's own media is ever served through it.
 */
private val rejectPatterns = listOf(
    "staticAsset" to Regex("""rsrc\.php"""),
)

fun filterThreads(media: Media?): Media? {
    if (media == null) {
        Log.d(TAG, "filterThreads: nothing to filter")
        return null
    }

    Log.d(TAG, "filterThreads: starting with ${media.images.size} image(s), ${media.videos.size} video(s)")

    val result = Media(
        images = media.images.filter(::isPostMedia),
        videos = media.videos.filter(::isPostMedia),
    )

    Log.d(TAG, "filterThreads: finished with ${result.images.size} image(s), ${result.videos.size} video(s)")

    return result.takeUnless { it.isEmpty }
}

/** Drops on the first pattern that matches, so every URL gets logged exactly once with its reason. */
private fun isPostMedia(url: String): Boolean {
    val reject = rejectPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(url) }

    if (reject != null) {
        Log.d(TAG, "isPostMedia: dropped $url (${reject.first})")
        return false
    }

    Log.d(TAG, "isPostMedia: kept $url")
    return true
}
