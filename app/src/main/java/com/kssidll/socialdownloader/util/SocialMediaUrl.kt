package com.kssidll.socialdownloader.util

import android.util.Log

private const val TAG = "SocialMediaUrl"

sealed class SocialMediaUrl {
    /**
     * The form to fetch, which isn't always the form found in the text. XHS keeps its short link,
     * because following the redirect is what resolves the note; Instagram is rebuilt into its
     * canonical post URL, because the same post arrives under a handful of different ones.
     */
    abstract val url: String

    data class Xhs(override val url: String) : SocialMediaUrl()

    /**
     * An Instagram post, reel or IGTV link, reduced to the shortcode all of those forms share.
     *
     * Only shortcode links are claimed. Profiles, stories and the rest have their own shapes and
     * would each need their own extraction, so they fall through as [Unrecognized] rather than
     * being taken on here and failing further down where the reason is harder to explain.
     */
    data class InstagramShortcode(
        override val url: String,
        val shortcode: String,
    ) : SocialMediaUrl()

    data class Threads(override val url: String) : SocialMediaUrl()

    data class Unrecognized(override val url: String) : SocialMediaUrl()
}

private val urlRegex = Regex("""https?://\S+""")
private val xhsRegex = Regex("""(xhslink\.cn|xiaohongshu\.com)""")
private val threadsRegex = Regex("""threads\.(net|com)""")

/**
 * Instagram serves one post under several paths and hosts - `/p/`, `/reel/`, `/reels/` and `/tv/`,
 * on instagram.com or the instagr.am shortener - and hangs share parameters like `?igsh=` off the
 * end. All of that is decoration around a single shortcode.
 */
private val instagramShortcodeRegex =
    Regex("""(?:instagram\.com|instagr\.am)/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")

/**
 * Rebuilt from the shortcode rather than trimmed down from the original, so nothing from the
 * incoming link can ride along into the requests made later.
 */
private fun instagramCanonicalUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/"

fun parseSocialMediaUrl(text: CharSequence): SocialMediaUrl? {
    Log.d(TAG, "parseSocialMediaUrl: scanning text for a URL")

    val url = urlRegex.find(text)?.value
    if (url == null) {
        Log.w(TAG, "parseSocialMediaUrl: no URL found in text")
        return null
    }
    Log.d(TAG, "parseSocialMediaUrl: found URL $url")

    val instagramShortcode = instagramShortcodeRegex.find(url)?.groupValues?.get(1)

    val result = when {
        xhsRegex.containsMatchIn(url) -> SocialMediaUrl.Xhs(url)

        instagramShortcode != null -> SocialMediaUrl.InstagramShortcode(
            url = instagramCanonicalUrl(instagramShortcode),
            shortcode = instagramShortcode,
        )

        threadsRegex.containsMatchIn(url) -> SocialMediaUrl.Threads(url)

        else -> SocialMediaUrl.Unrecognized(url)
    }
    Log.d(TAG, "parseSocialMediaUrl: classified as $result")

    return result
}
