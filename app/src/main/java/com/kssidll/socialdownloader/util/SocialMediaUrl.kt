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

    /**
     * A TikTok post, video or photo - both live in the same id space, on the same page, behind the
     * same payload, so nothing downstream has to care which it is until the media is in hand.
     *
     * A full link is rebuilt around the canonical form, dropping the handle and the share parameters
     * the share sheet hangs off the end. A short link has no id in it to rebuild from and so keeps
     * the form it arrived in - following the redirect is what resolves the post.
     */
    data class TikTok(override val url: String) : SocialMediaUrl()

    /**
     * A Threads post, reduced to the shortcode every form of its link shares.
     *
     * Threads moved from threads.net to threads.com and still answers on both, under the
     * handle-carrying `/@user/post/` form and the bare `/t/` one alike. They all land on the same
     * page, so the shortcode is the only part of an incoming link worth keeping.
     *
     * Only post links are claimed, for the same reason as [InstagramShortcode]: profiles and the
     * rest have their own shapes and would each need their own extraction, so they fall through as
     * [Unrecognized] rather than being taken on here and failing somewhere less explicable.
     */
    data class Threads(
        override val url: String,
        val shortcode: String,
    ) : SocialMediaUrl()

    data class Unrecognized(override val url: String) : SocialMediaUrl()
}

private val urlRegex = Regex("""https?://\S+""")
private val xhsRegex = Regex("""(xhslink\.cn|xiaohongshu\.com)""")

/**
 * Threads hangs share parameters - `xmt`, `slof` - off a link that is otherwise a handle, the word
 * `post` and the shortcode. The `/t/` form is the same link with the handle left off.
 */
private val threadsShortcodeRegex =
    Regex("""threads\.(?:net|com)/(?:@[\w.]+/post|t)/([A-Za-z0-9_-]+)""")

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

/**
 * TikTok's share sheet hands out `vm.` and `vt.` short links and `tiktok.com/t/` ones. None of them
 * carries the post id - only the redirect knows it - so these are matched to be claimed, not to be
 * taken apart.
 */
private val tiktokShortRegex = Regex("""(?:vm|vt)\.tiktok\.com/|tiktok\.com/t/""")

/**
 * The full form: a handle, `video` or `photo`, then the numeric id. Which of the two path segments
 * a link uses says nothing binding - the id alone decides what comes back, and a photo post answers
 * under `/video/` just as readily.
 */
private val tiktokPostIdRegex = Regex("""tiktok\.com/@[\w.]+/(?:video|photo)/(\d+)""")

/**
 * `@i` is TikTok's own stand-in for an unnamed author: the post resolves through it whoever posted
 * it, which is what lets the handle be dropped rather than carried along. Confirmed against both a
 * photo post and a video post, under `/video/` for each.
 */
private fun tiktokCanonicalUrl(postId: String) = "https://www.tiktok.com/@i/video/$postId"

/**
 * Rebuilt around `/t/`, which resolves a post without needing its handle - it redirects onto the
 * canonical `/@user/post/` URL by itself. Same reasoning as Instagram's: nothing from the incoming
 * link, share parameters included, rides along into the requests made later.
 */
private fun threadsCanonicalUrl(shortcode: String) = "https://www.threads.com/t/$shortcode"

fun parseSocialMediaUrl(text: CharSequence): SocialMediaUrl? {
    Log.d(TAG, "parseSocialMediaUrl: scanning text for a URL")

    val url = urlRegex.find(text)?.value
    if (url == null) {
        Log.w(TAG, "parseSocialMediaUrl: no URL found in text")
        return null
    }
    Log.d(TAG, "parseSocialMediaUrl: found URL $url")

    val instagramShortcode = instagramShortcodeRegex.find(url)?.groupValues?.get(1)
    val tiktokPostId = tiktokPostIdRegex.find(url)?.groupValues?.get(1)
    val threadsShortcode = threadsShortcodeRegex.find(url)?.groupValues?.get(1)

    val result = when {
        xhsRegex.containsMatchIn(url) -> SocialMediaUrl.Xhs(url)

        tiktokPostId != null -> SocialMediaUrl.TikTok(tiktokCanonicalUrl(tiktokPostId))

        // Nothing to rebuild from, so it goes out as it came in and the redirect does the resolving.
        tiktokShortRegex.containsMatchIn(url) -> SocialMediaUrl.TikTok(url)

        instagramShortcode != null -> SocialMediaUrl.InstagramShortcode(
            url = instagramCanonicalUrl(instagramShortcode),
            shortcode = instagramShortcode,
        )

        threadsShortcode != null -> SocialMediaUrl.Threads(
            url = threadsCanonicalUrl(threadsShortcode),
            shortcode = threadsShortcode,
        )

        else -> SocialMediaUrl.Unrecognized(url)
    }
    Log.d(TAG, "parseSocialMediaUrl: classified as $result")

    return result
}
