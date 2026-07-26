package com.kssidll.socialdownloader.xhs

import android.util.Log
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.util.asHttps
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

private const val TAG = "XhsParser"

private val cdnUrlPattern = Regex("""xhscdn\.com""")
private val videoHintPattern = Regex("""\.mp4(\?|$)|/stream/|sns-video""")

/**
 * XHS note pages embed the note data as a `window.__INITIAL_STATE__ = {...}` JS object literal
 * inside a <script> tag (server-rendered for SEO). It isn't strict JSON - it contains literal
 * `undefined` tokens - so we patch that before parsing.
 *
 * The exact schema isn't pinned down here, this recursively scans the parsed tree for any string
 * values pointing at XHS's CDN, which is agnostic to the exact nesting and more resilient to the
 * internal shape shifting.
 */
fun parseXhsInitialState(html: String): Media? {
    Log.d(TAG, "parseXhsInitialState: scanning ${html.length} chars of HTML for script tags")

    val scripts = Jsoup.parse(html).select("script")
    Log.d(TAG, "parseXhsInitialState: found ${scripts.size} script tags")

    val scriptContent = scripts.map { it.data() }.firstOrNull { "__INITIAL_STATE__" in it }
    if (scriptContent == null) {
        Log.w(TAG, "parseXhsInitialState: no __INITIAL_STATE__ script tag found")
        return null
    }
    Log.d(TAG, "parseXhsInitialState: found __INITIAL_STATE__ script (${scriptContent.length} chars)")

    val assignIndex = scriptContent.indexOf('=', scriptContent.indexOf("__INITIAL_STATE__"))
    if (assignIndex == -1) {
        Log.w(TAG, "parseXhsInitialState: no '=' assignment found in the script")
        return null
    }

    val jsonText = scriptContent.substring(assignIndex + 1)
        .trim()
        .removeSuffix(";")
        .replace("undefined", "null")
    Log.d(TAG, "parseXhsInitialState: extracted ${jsonText.length} chars of JSON")

    val root = try {
        JSONObject(jsonText)
    } catch (e: Exception) {
        Log.e(TAG, "parseXhsInitialState: failed to parse JSON", e)
        return null
    }
    Log.d(TAG, "parseXhsInitialState: JSON parsed ok, scanning for xhscdn.com URLs")

    val urls = mutableListOf<String>()
    collectCdnUrls(root, urls)
    val distinctUrls = urls.distinct()
    Log.d(TAG, "parseXhsInitialState: found ${distinctUrls.size} distinct CDN URLs")

    val videos = distinctUrls.filter { videoHintPattern.containsMatchIn(it) }
    val images = distinctUrls - videos.toSet()
    Log.d(TAG, "parseXhsInitialState: classified ${images.size} image(s), ${videos.size} video(s)")

    if (images.isEmpty() && videos.isEmpty()) {
        Log.w(TAG, "parseXhsInitialState: JSON parsed but no xhscdn.com URLs found in it")
        return null
    }

    return Media(images = images, videos = videos)
}

private fun collectCdnUrls(node: Any?, sink: MutableList<String>) {
    when (node) {
        is JSONObject -> node.keys().forEach { key -> collectCdnUrls(node.opt(key), sink) }
        is JSONArray -> (0 until node.length()).forEach { collectCdnUrls(node.opt(it), sink) }
        // Normalised on the way in, so the distinct() downstream also collapses http/https pairs.
        is String -> if (node.startsWith("http") && cdnUrlPattern.containsMatchIn(node)) {
            sink += node.asHttps()
        }
    }
}

/**
 * Falls back to the standard Open Graph meta tags (og:image / og:video) present in the page head.
 * Coarser than the JSON step (usually only the cover image + one video, no multi-image galleries)
 * but far less likely to break if XHS changes its internal state schema.
 */
fun parseXhsMetaTags(html: String): Media? {
    Log.d(TAG, "parseXhsMetaTags: scanning ${html.length} chars of HTML for og: meta tags")

    val doc = Jsoup.parse(html)

    val images = doc.select("meta[property=og:image]")
        .mapNotNull { it.attr("content").takeIf(String::isNotBlank)?.asHttps() }
        .distinct()
    Log.d(TAG, "parseXhsMetaTags: found ${images.size} og:image tag(s)")

    val video = doc.select("meta[property=og:video], meta[property=og:video:url]")
        .map { it.attr("content") }
        .firstOrNull(String::isNotBlank)
        ?.asHttps()
    Log.d(TAG, "parseXhsMetaTags: found og:video tag = $video")

    if (images.isEmpty() && video == null) {
        Log.w(TAG, "parseXhsMetaTags: no og:image/og:video meta tags found")
        return null
    }

    return Media(images = images, videos = listOfNotNull(video))
}
