package com.kssidll.socialdownloader.util

import android.util.Log

private const val TAG = "SocialMediaUrl"

sealed class SocialMediaUrl {
    abstract val url: String

    data class Xhs(override val url: String) : SocialMediaUrl()
    data class Instagram(override val url: String) : SocialMediaUrl()
    data class Threads(override val url: String) : SocialMediaUrl()
    data class Unrecognized(override val url: String) : SocialMediaUrl()
}

private val urlRegex = Regex("""https?://\S+""")
private val xhsRegex = Regex("""(xhslink\.cn|xiaohongshu\.com)""")
private val instagramRegex = Regex("""instagram\.com""")
private val threadsRegex = Regex("""threads\.(net|com)""")

fun parseSocialMediaUrl(text: CharSequence): SocialMediaUrl? {
    Log.d(TAG, "parseSocialMediaUrl: scanning text for a URL")

    val url = urlRegex.find(text)?.value
    if (url == null) {
        Log.w(TAG, "parseSocialMediaUrl: no URL found in text")
        return null
    }
    Log.d(TAG, "parseSocialMediaUrl: found URL $url")

    val result = when {
        xhsRegex.containsMatchIn(url) -> SocialMediaUrl.Xhs(url)
        instagramRegex.containsMatchIn(url) -> SocialMediaUrl.Instagram(url)
        threadsRegex.containsMatchIn(url) -> SocialMediaUrl.Threads(url)
        else -> SocialMediaUrl.Unrecognized(url)
    }
    Log.d(TAG, "parseSocialMediaUrl: classified as $result")

    return result
}
