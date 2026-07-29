package com.kssidll.socialdownloader.threads

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.FetchOutcome
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.deduplicated
import com.kssidll.socialdownloader.network.fetchHtml
import com.kssidll.socialdownloader.util.SocialMediaUrl

private const val TAG = "ThreadsDownloader"

/**
 * One way of getting media out of a post page.
 *
 * Takes the shortcode as well as the HTML, unlike the other platforms': the richest step needs it
 * to tell the post's own media from the rest of the page's, and handing it to both steps is what
 * lets them sit in one list. It can be null - a share link that the page then declined to name
 * leaves the payload step with nothing to anchor on, and the meta tag step never wanted it.
 */
private class ParseStep(
    val name: String,
    val parse: (html: String, shortcode: String?) -> Media?,
)

/**
 * Tried in order, richest first: the payload step recovers a whole carousel and the real video
 * files, while the meta tag step finds only the cover but survives Meta reshuffling the payload.
 */
private val parseSteps = listOf(
    ParseStep("post payload JSON", ::parseThreadsPayload),
    ParseStep("og meta tag") { html, _ -> parseThreadsMetaTags(html) },
)

/**
 * Fetches the post page and reads the post out of it.
 *
 * One page, unlike Instagram's two: Threads renders the media and the reason there is none into the
 * same page, so there is nothing a second fetch would add. Its media is served straight off the CDN
 * as well - no session to carry across, the way TikTok needs one.
 */
suspend fun downloadThreads(link: SocialMediaUrl.Threads): FetchOutcome {
    Log.d(TAG, "downloadThreads: starting for ${link.url}")

    val html = fetchHtml(link.url)
    if (html == null) {
        Log.w(TAG, "downloadThreads: fetch failed for ${link.url}")
        return FetchOutcome.Failure(FetchFailure.Unreachable)
    }

    // A share link knows no code, and the redirect it was followed through is what settled which
    // post this page is - so the page is asked, rather than the link that reached it.
    val shortcode = link.shortcode ?: canonicalThreadsShortcode(html)
    Log.d(TAG, "downloadThreads: anchoring on $shortcode")

    val parsed = parseSteps.firstNotNullOfOrNull { step ->
        Log.d(TAG, "downloadThreads: trying the ${step.name} step")
        step.parse(html, shortcode)
    }

    val media = filterThreads(parsed)?.deduplicated()
    if (media == null) {
        val reason = threadsFailureOf(html)
        Log.w(TAG, "downloadThreads: nothing to download for ${link.url}, reporting $reason")
        return FetchOutcome.Failure(reason)
    }

    Log.d(TAG, "downloadThreads: resolved -> $media")
    return FetchOutcome.Success(media)
}
