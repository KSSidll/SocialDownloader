package com.kssidll.socialdownloader.tiktok

import android.util.Log
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.util.asHttps
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

private const val TAG = "TikTokParser"

private const val UNIVERSAL_DATA_ID = "__UNIVERSAL_DATA_FOR_REHYDRATION__"

/**
 * Where the post sits, which depends on which page rendered.
 *
 * A mobile User-Agent - which is what the app sends - gets the share page and the `reflow` key; a
 * desktop one gets the full page and the plain key. Both wrap the same `itemInfo.itemStruct`, so
 * both are read and the app doesn't have to depend on which page it was given.
 */
private val detailScopeKeys = listOf("webapp.reflow.video.detail", "webapp.video-detail")

/**
 * Reads the media out of the `__UNIVERSAL_DATA_FOR_REHYDRATION__` payload TikTok server-renders
 * into every post page.
 *
 * Unlike XHS and Instagram there is no coarser second step behind this one: TikTok's post pages
 * carry no `og:image` or `og:video` at all, under either a mobile or a desktop User-Agent, so this
 * payload is the only thing on the page that names the media. If it stops holding the post, the
 * platform stops working rather than degrading to a cover image.
 *
 * The shape, with the two media fields being mutually exclusive in practice:
 * ```
 * itemStruct = {
 *   video:     { playAddr, downloadAddr, cover, … },              // blank on a photo post
 *   imagePost: { images: [ { imageURL: { urlList: [ … ] } } ] },  // absent on a video post
 * }
 * ```
 * Both are read regardless, so a post carrying the two doesn't come back half-collected.
 */
fun parseTikTokUniversalData(html: String): Media? {
    val item = tiktokPostDetail(html)
        ?.optJSONObject("itemInfo")
        ?.optJSONObject("itemStruct")
    if (item == null) {
        Log.w(TAG, "parseTikTokUniversalData: no itemInfo.itemStruct in the payload")
        return null
    }
    Log.d(TAG, "parseTikTokUniversalData: item ${item.optString("id")}")

    val images = item.optJSONObject("imagePost")
        ?.optJSONArray("images")
        ?.objects()
        .orEmpty()
        .mapNotNull { it.firstImageUrl() }

    val videos = listOfNotNull(item.optJSONObject("video")?.playableUrl())

    Log.d(TAG, "parseTikTokUniversalData: found ${images.size} image(s), ${videos.size} video(s)")
    if (images.isEmpty() && videos.isEmpty()) return null

    return Media(images = images, videos = videos)
}

/**
 * An image's first URL. The others are the same file on sibling CDN hosts - p16, p19 - rather than
 * different sizes or crops, so there is nothing to choose between them.
 */
private fun JSONObject.firstImageUrl(): String? = optJSONObject("imageURL")
    ?.optJSONArray("urlList")
    ?.optString(0)
    ?.takeIf(String::isNotBlank)
    ?.asHttps()

/**
 * `playAddr` first: `downloadAddr` is the watermark-free copy TikTok only mints for posts whose
 * author allows saving, and it arrives as an empty string for everything else - including, as it
 * happens, the video post this was confirmed against.
 *
 * Both are blank on a photo post, where the same object still exists but describes nothing.
 */
private fun JSONObject.playableUrl(): String? =
    optString("playAddr")
        .ifBlank { optString("downloadAddr") }
        .takeIf(String::isNotBlank)
        ?.asHttps()

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * Digs out the object holding the post, which carries both the media and the reason there is none.
 *
 * Shared with [tiktokFailureOf] rather than parsed twice, because on this platform the status and
 * the media come out of the very same payload - there is no separate error page to go and read.
 */
internal fun tiktokPostDetail(html: String): JSONObject? {
    val payload = Jsoup.parse(html)
        .getElementById(UNIVERSAL_DATA_ID)
        ?.data()
        ?.takeIf(String::isNotBlank)
    if (payload == null) {
        Log.w(TAG, "tiktokPostDetail: no $UNIVERSAL_DATA_ID script in ${html.length} chars")
        return null
    }
    Log.d(TAG, "tiktokPostDetail: found the payload (${payload.length} chars)")

    val scope = try {
        JSONObject(payload).optJSONObject("__DEFAULT_SCOPE__")
    } catch (e: Exception) {
        Log.e(TAG, "tiktokPostDetail: could not parse the payload", e)
        return null
    }
    if (scope == null) {
        Log.w(TAG, "tiktokPostDetail: payload held no __DEFAULT_SCOPE__")
        return null
    }

    val detail = detailScopeKeys.firstNotNullOfOrNull { key ->
        scope.optJSONObject(key)?.also { Log.d(TAG, "tiktokPostDetail: read it from $key") }
    }
    if (detail == null) Log.w(TAG, "tiktokPostDetail: none of $detailScopeKeys was present")

    return detail
}

/**
 * Pass-through, for the same reason Instagram's is: the payload names the post's own media
 * directly, so there are no avatars, music covers or comment thumbnails mixed in to screen out.
 */
fun filterTikTok(media: Media?): Media? = media
