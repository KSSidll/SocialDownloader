package com.kssidll.socialdownloader.instagram

import android.util.Log
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.util.asHttps
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

private const val TAG = "InstagramParser"

private const val CONTEXT_JSON_KEY = "\"contextJSON\":"

/**
 * Reads the media out of the embed page's `contextJSON` payload.
 *
 * Worth knowing: this payload is only served to a request that looks like a browser navigation.
 * Without the Accept header the same URL returns a shell with `contextJSON: null`, which reads as
 * "post has no media" rather than as "you asked wrong".
 *
 * The shape is Instagram's GraphQL one:
 * ```
 * gql_data.shortcode_media = {
 *   __typename: GraphSidecar | GraphImage | GraphVideo,
 *   is_video, display_url, dimensions,
 *   edge_sidecar_to_children: { edges: [ { node: { is_video, display_url, … } } ] }
 * }
 * ```
 * A carousel puts each item in `edge_sidecar_to_children`; a single-item post has none and is its
 * own node, so both collapse to "a list of nodes" and are handled the same way.
 */
fun parseInstagramEmbed(html: String): Media? {
    val start = html.indexOf(CONTEXT_JSON_KEY)
    if (start == -1) {
        Log.w(TAG, "parseInstagramEmbed: no contextJSON in ${html.length} chars")
        return null
    }

    val root = decodeContextJson(html.substring(start + CONTEXT_JSON_KEY.length))
    if (root == null) {
        Log.w(TAG, "parseInstagramEmbed: contextJSON was empty or unreadable")
        return null
    }

    // Not acted on yet, but the embed says so plainly and it explains an otherwise empty payload.
    root.optJSONObject("context")?.let { context ->
        if (context.optBoolean("copyright_blocked")) {
            Log.w(TAG, "parseInstagramEmbed: post is flagged copyright_blocked")
        }
    }

    val media = root.optJSONObject("gql_data")?.optJSONObject("shortcode_media")
    if (media == null) {
        Log.w(TAG, "parseInstagramEmbed: no gql_data.shortcode_media")
        return null
    }
    Log.d(TAG, "parseInstagramEmbed: ${media.optString("__typename")} ${media.optString("shortcode")}")

    val images = mutableListOf<String>()
    val videos = mutableListOf<String>()

    childNodesOf(media).forEach { node ->
        // Both branches confirmed against real posts: an image carousel and a video post. A
        // carousel *containing* video hasn't been seen, but it runs this same branch per node, so
        // there is nothing shape-specific left to get wrong.
        val url = if (node.optBoolean("is_video")) {
            node.optString("video_url").takeIf(String::isNotBlank)?.also { videos += it.asHttps() }
        } else {
            // display_url over display_resources: the latter mixes in square crops of the same
            // photo, so "the biggest one" is not reliably the whole picture.
            node.optString("display_url").takeIf(String::isNotBlank)?.also { images += it.asHttps() }
        }

        if (url == null) Log.w(TAG, "parseInstagramEmbed: node ${node.optString("id")} had no URL")
    }

    Log.d(TAG, "parseInstagramEmbed: found ${images.size} image(s), ${videos.size} video(s)")
    if (images.isEmpty() && videos.isEmpty()) return null

    return Media(images = images, videos = videos)
}

/** A carousel's children, or the post itself when it holds a single item. */
private fun childNodesOf(media: JSONObject): List<JSONObject> {
    val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        ?: return listOf(media)

    return (0 until edges.length()).mapNotNull { edges.optJSONObject(it)?.optJSONObject("node") }
}

/**
 * `contextJSON` carries a whole JSON document encoded *as a JSON string*, so it has to be decoded
 * twice. [JSONTokener] is what reads the string literal - it consumes exactly one value and
 * unescapes it, which finding the closing quote by hand would get wrong the moment a caption
 * contains one.
 */
private fun decodeContextJson(afterKey: String): JSONObject? = try {
    (JSONTokener(afterKey).nextValue() as? String)
        ?.takeIf { it.isNotBlank() }
        ?.let { JSONObject(it) }
} catch (e: Exception) {
    Log.e(TAG, "decodeContextJson: could not decode the payload", e)
    null
}

/**
 * Fallback for embeds that arrive without a usable `contextJSON` - it isn't always there, and when
 * it isn't the page still renders the media into a plain `<img class="EmbeddedMediaImage">`.
 *
 * Coarser than the JSON step by nature: the markup holds the one displayed image, so a carousel
 * comes back as its cover alone and a video as its poster frame rather than the video. That is the
 * trade for working at all on a post the JSON step can't see.
 *
 * Anchored on the class name because it is one of the few stable ones Instagram ships - the rest of
 * its markup is generated and changes without notice.
 */
fun parseInstagramEmbeddedImage(html: String): Media? {
    // Jsoup resolves the entity escaping in the attribute, so the query string comes back usable
    // rather than full of &amp;.
    val images = Jsoup.parse(html)
        .select("img.EmbeddedMediaImage")
        .mapNotNull { it.attr("src").takeIf(String::isNotBlank)?.asHttps() }
        .distinct()

    if (images.isEmpty()) {
        Log.w(TAG, "parseInstagramEmbeddedImage: no img.EmbeddedMediaImage in ${html.length} chars")
        return null
    }

    Log.d(TAG, "parseInstagramEmbeddedImage: found ${images.size} image(s)")
    return Media(images = images)
}

/**
 * Last resort, and the only step that reads the post page rather than the embed - the embed carries
 * no `og:` tags at all, so this has nothing to work with there.
 *
 * Coarsest of the three: one cover image for a whole carousel, same as the markup step. Its value
 * is that it keeps working when the embed gives up nothing at all.
 */
fun parseInstagramMetaTags(html: String): Media? {
    val doc = Jsoup.parse(html)

    val images = doc.select("meta[property=og:image]")
        .mapNotNull { it.attr("content").takeIf(String::isNotBlank)?.asHttps() }
        .distinct()

    // TODO: unproven. No video post has been seen to carry og:video - included for symmetry with
    //  the image tag, so drop it if a real video post turns out not to emit one.
    val videos = doc.select("meta[property=og:video], meta[property=og:video:url]")
        .mapNotNull { it.attr("content").takeIf(String::isNotBlank)?.asHttps() }
        .distinct()

    if (images.isEmpty() && videos.isEmpty()) {
        Log.w(TAG, "parseInstagramMetaTags: no og:image/og:video tags found")
        return null
    }

    Log.d(TAG, "parseInstagramMetaTags: found ${images.size} image(s), ${videos.size} video(s)")
    return Media(images = images, videos = videos)
}

/**
 * Pass-through, and now known to be the right answer: the parse steps read the media directly
 * rather than scavenging every CDN URL on the page, so there are no avatars or comment thumbnails
 * mixed in for a filter to remove.
 */
fun filterInstagram(media: Media?): Media? = media
