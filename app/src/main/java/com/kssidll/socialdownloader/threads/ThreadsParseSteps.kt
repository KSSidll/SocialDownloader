package com.kssidll.socialdownloader.threads

import android.util.Log
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.util.asHttps
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

private const val TAG = "ThreadsParser"

/**
 * Reads the media out of the JSON payloads Threads server-renders into its post pages.
 *
 * The shape is Instagram's, which is no surprise given whose codebase Threads grew out of:
 * ```
 * post = {
 *   code, media_type,
 *   image_versions2: { candidates: [ { width, height, url } ] },
 *   video_versions:  [ { type, url } ],
 *   carousel_media:  [ { … the same two fields per item … } ],
 * }
 * ```
 * A carousel puts each item in `carousel_media`; a single-media post has none and is its own node,
 * so both collapse to "a list of nodes" and are handled the same way.
 */
fun parseThreadsPayload(html: String, shortcode: String): Media? {
    val post = findThreadsPost(html, shortcode)
    if (post == null) {
        Log.w(TAG, "parseThreadsPayload: no post carrying code $shortcode in ${html.length} chars")
        return null
    }
    Log.d(TAG, "parseThreadsPayload: media_type ${post.optString("media_type")} for $shortcode")

    val images = mutableListOf<String>()
    val videos = mutableListOf<String>()

    childNodesOf(post).forEach { node ->
        val video = node.firstVideoUrl()

        // A video node carries image_versions2 as well, holding its poster frame - collecting both
        // would put the same item in the picker twice, the second time as a still nobody asked for.
        val url = video?.also { videos += it } ?: node.largestImageUrl()?.also { images += it }

        if (url == null) Log.w(TAG, "parseThreadsPayload: node ${node.optString("pk")} had no URL")
    }

    Log.d(TAG, "parseThreadsPayload: found ${images.size} image(s), ${videos.size} video(s)")
    if (images.isEmpty() && videos.isEmpty()) return null

    return Media(images = images, videos = videos)
}

/** A carousel's items, or the post itself when it holds a single piece of media. */
private fun childNodesOf(post: JSONObject): List<JSONObject> =
    post.optJSONArray("carousel_media")?.objects() ?: listOf(post)

/**
 * The first encoding of a node's video, or null on a node that has none - which is what tells an
 * image node from a video one, since only a video carries this at all.
 *
 * First rather than best because there is no better to pick: the entries carry a `type` (101, 102,
 * 103) and a URL and nothing else, no dimensions to rank on, and all three came back byte for byte
 * the same size when fetched. They are the same video behind three URLs.
 */
private fun JSONObject.firstVideoUrl(): String? = optJSONArray("video_versions")
    ?.objects()
    ?.firstNotNullOfOrNull { it.optString("url").takeIf(String::isNotBlank) }
    ?.asHttps()

/**
 * The largest of the candidates, which are one photo at descending sizes with a run of square crops
 * behind them - nineteen of them on the post this was written against.
 *
 * Ranked on area rather than taken from the front: the list isn't ordered by size the whole way
 * down, and the crops are the same photo with its edges gone, so landing on one would quietly hand
 * over less of the picture than the post actually holds.
 */
private fun JSONObject.largestImageUrl(): String? = optJSONObject("image_versions2")
    ?.optJSONArray("candidates")
    ?.objects()
    ?.maxByOrNull { it.optInt("width").toLong() * it.optInt("height") }
    ?.optString("url")
    ?.takeIf(String::isNotBlank)
    ?.asHttps()

/**
 * Finds the post the link points at, by the shortcode the link already handed us.
 *
 * Anchored on the code, and that is the whole point of this step. A post page also carries related
 * posts and a slice of the feed - ten distinct photos on the page this was written against, only
 * four of them the post's own - and their media URLs are indistinguishable from the post's: same
 * CDN host, same `/v/t51.82787-15/` path, same seventeen query parameters, differing only in the
 * media id. There is no pattern to screen them out by, the way XHS's avatars and comment images can
 * be screened out. The code separates them exactly, and nothing else does.
 *
 * The walk is recursive for the reason the XHS one is: the payload sits under generated
 * `require`/`__bbox` nesting that Meta reshuffles at will, and none of that is worth encoding here.
 */
private fun findThreadsPost(html: String, shortcode: String): JSONObject? {
    val blocks = Jsoup.parse(html).select("script[type=application/json]")

    // The page runs past a megabyte across those blocks, and all but a handful cannot hold the post
    // at all - a substring check costs nothing next to parsing one that was never going to match.
    val candidates = blocks.map { it.data() }.filter { shortcode in it }
    Log.d(TAG, "findThreadsPost: ${candidates.size} of ${blocks.size} block(s) mention $shortcode")

    return candidates.firstNotNullOfOrNull { block ->
        val root = try {
            JSONObject(block)
        } catch (e: Exception) {
            Log.w(TAG, "findThreadsPost: a block wasn't readable as JSON", e)
            return@firstNotNullOfOrNull null
        }

        findPostNode(root, shortcode)
    }
}

/**
 * Depth-first search for the media-bearing node carrying this code.
 *
 * The first hit is taken because the page repeats the post: six copies of it on one page, in the
 * post slot, among the related posts and again in the feed - and every copy held the same media.
 */
private fun findPostNode(node: Any?, shortcode: String): JSONObject? {
    when (node) {
        is JSONObject -> {
            if (node.optString("code") == shortcode && node.holdsMedia()) return node

            node.keys().forEach { key ->
                findPostNode(node.opt(key), shortcode)?.let { return it }
            }
        }

        is JSONArray -> (0 until node.length()).forEach { index ->
            findPostNode(node.opt(index), shortcode)?.let { return it }
        }
    }

    return null
}

/**
 * Tells the post itself from the other places its code turns up - permalinks, analytics blobs and
 * the like, none of which carry anything to download.
 */
private fun JSONObject.holdsMedia(): Boolean = has("carousel_media") || has("image_versions2")

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * Falls back to the `og:image` tag every post page carries.
 *
 * Coarsest by a wide margin, same as Instagram's: one cover image for a whole carousel, and a
 * poster frame rather than the video for a video post. It earns its keep by needing nothing of the
 * payload's shape - only the one tag Threads renders for link previews.
 *
 * Images only, deliberately: a Threads post page emits no `og:video`, so there is nothing here for
 * a video branch to read and no reason to write one on spec.
 */
fun parseThreadsMetaTags(html: String): Media? {
    val images = Jsoup.parse(html)
        .select("meta[property=og:image]")
        .mapNotNull { it.attr("content").takeIf(String::isNotBlank)?.asHttps() }
        .distinct()

    if (images.isEmpty()) {
        Log.w(TAG, "parseThreadsMetaTags: no og:image tag found")
        return null
    }

    Log.d(TAG, "parseThreadsMetaTags: found ${images.size} image(s)")
    return Media(images = images)
}

/**
 * Pass-through, for the same reason Instagram's and TikTok's are: the steps above read the post's
 * own media directly rather than scavenging the page, so nothing foreign gets as far as a filter.
 *
 * Which is exactly why the payload step is anchored on the code - see [findThreadsPost]. Screening
 * afterwards is not an option here; there would be nothing to screen on.
 */
fun filterThreads(media: Media?): Media? = media
