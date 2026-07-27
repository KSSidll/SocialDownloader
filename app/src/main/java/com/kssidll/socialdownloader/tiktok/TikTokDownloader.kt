package com.kssidll.socialdownloader.tiktok

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.FetchOutcome
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.deduplicated
import com.kssidll.socialdownloader.network.fetchHtml

private const val TAG = "TikTokDownloader"

/** One way of getting media out of a post page. */
private class ParseStep(val name: String, val parse: (html: String) -> Media?)

/**
 * Only the one, where the other platforms have a fallback behind their richest step: TikTok's post
 * pages carry no og: tags, so either the rehydration payload holds the post or nothing on the page
 * does. Kept in the same shape as the others anyway, so adding a step is an edit to this list.
 */
private val parseSteps = listOf(
    ParseStep("__UNIVERSAL_DATA_FOR_REHYDRATION__ JSON", ::parseTikTokUniversalData),
)

/**
 * Fetches the post page - following the redirect a vm./vt. short link starts out as - and reads the
 * post out of it.
 *
 * This request does more than hand back HTML. TikTok signs its video URLs against the session that
 * served the page, so it is this fetch that mints the cookie the download later has to present; the
 * shared client's jar is what carries it across. Photos need none of that and are downloadable
 * straight off the CDN.
 *
 * Failures are read from the page rather than inferred from an empty parse, so "that post no longer
 * exists" can be told apart from "we couldn't read this one".
 */
suspend fun downloadTikTok(url: String): FetchOutcome {
    Log.d(TAG, "downloadTikTok: starting for $url")

    val html = fetchHtml(url)
    if (html == null) {
        Log.w(TAG, "downloadTikTok: fetch failed for $url")
        return FetchOutcome.Failure(FetchFailure.Unreachable)
    }

    val parsed = parseSteps.firstNotNullOfOrNull { step ->
        Log.d(TAG, "downloadTikTok: trying the ${step.name} step")
        step.parse(html)
    }

    val media = filterTikTok(parsed)?.deduplicated()
    if (media == null) {
        val reason = tiktokFailureOf(html)
        Log.w(TAG, "downloadTikTok: nothing to download for $url, reporting $reason")
        return FetchOutcome.Failure(reason)
    }

    Log.d(TAG, "downloadTikTok: resolved -> $media")
    return FetchOutcome.Success(media)
}
