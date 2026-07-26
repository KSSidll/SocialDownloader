package com.kssidll.socialdownloader.instagram

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.FetchOutcome
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.deduplicated
import com.kssidll.socialdownloader.network.fetchHtml
import com.kssidll.socialdownloader.util.SocialMediaUrl

private const val TAG = "InstagramDownloader"

/** One way of getting media out of an embed page. */
private class ParseStep(val name: String, val parse: (html: String) -> Media?)

/** Richest first: the JSON payload carries every carousel item, the markup only the cover. */
private val embedParseSteps = listOf(
    ParseStep("contextJSON", ::parseInstagramEmbed),
    ParseStep("embedded image tag", ::parseInstagramEmbeddedImage),
)

private fun embedUrl(shortcode: String) =
    "https://www.instagram.com/p/$shortcode/embed/captioned/"

/**
 * Two pages, each good at one thing.
 *
 * The embed carries the media but is silent when it has none - an empty one is indistinguishable
 * from a post that doesn't exist. The post page is the opposite: harder to pull media out of, but
 * it says why there is nothing to take. So the embed is asked first, and the post page is only
 * fetched to explain a miss.
 */
suspend fun downloadInstagram(link: SocialMediaUrl.InstagramShortcode): FetchOutcome {
    Log.d(TAG, "downloadInstagram: starting for ${link.shortcode}")

    val embedHtml = fetchHtml(embedUrl(link.shortcode))
    if (embedHtml != null) {
        val parsed = embedParseSteps.firstNotNullOfOrNull { step ->
            Log.d(TAG, "downloadInstagram: trying the ${step.name} step")
            step.parse(embedHtml)
        }

        val media = filterInstagram(parsed)?.deduplicated()
        if (media != null) {
            Log.d(TAG, "downloadInstagram: resolved from the embed -> $media")
            return FetchOutcome.Success(media)
        }
    }

    Log.d(TAG, "downloadInstagram: embed gave nothing, asking the post page why")
    val pageHtml = fetchHtml(link.url)
    if (pageHtml == null) {
        Log.w(TAG, "downloadInstagram: post page unreachable for ${link.url}")
        return FetchOutcome.Failure(FetchFailure.Unreachable)
    }

    val reason = instagramFailureOf(pageHtml)
    Log.d(TAG, "downloadInstagram: post page says $reason")

    // A refusal outranks anything scraped off the same page. An age-gated post still serves an
    // og:image of its cover, so reading the tags first would quietly hand over the very media the
    // gate exists to withhold - NothingFound is the only verdict open to being talked out of.
    if (reason != FetchFailure.NothingFound) {
        return FetchOutcome.Failure(reason)
    }

    Log.d(TAG, "downloadInstagram: no refusal given, trying the og meta tag step on the post page")
    val fromMetaTags = filterInstagram(parseInstagramMetaTags(pageHtml))?.deduplicated()
    if (fromMetaTags != null) {
        Log.d(TAG, "downloadInstagram: resolved from the post page -> $fromMetaTags")
        return FetchOutcome.Success(fromMetaTags)
    }

    return FetchOutcome.Failure(reason)
}
