package com.kssidll.socialdownloader.xhs

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure
import com.kssidll.socialdownloader.media.FetchOutcome
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.media.deduplicated
import com.kssidll.socialdownloader.network.fetchHtml

private const val TAG = "XhsDownloader"

/** One way of getting media out of a note page. */
private class ParseStep(val name: String, val parse: (html: String) -> Media?)

/**
 * Tried in order, richest first: the JSON step recovers whole galleries, while the meta tag step
 * usually only finds the cover but survives XHS reshuffling its internal state.
 */
private val parseSteps = listOf(
    ParseStep("__INITIAL_STATE__ JSON", ::parseXhsInitialState),
    ParseStep("og meta tag", ::parseXhsMetaTags),
)

/**
 * Fetches the note page, tries each parse strategy in order until one returns something, then
 * screens what came back.
 *
 * Filtering happens here rather than at the call site so callers only ever deal in outcomes, and
 * every platform can screen its own results however it needs to.
 */
suspend fun downloadXhs(url: String): FetchOutcome {
    Log.d(TAG, "downloadXhs: starting for $url")

    val html = fetchHtml(url)
    if (html == null) {
        Log.w(TAG, "downloadXhs: fetch failed for $url")
        return FetchOutcome.Failure(FetchFailure.Unreachable)
    }

    // Stops at the first step that finds anything, so the cheaper fallbacks only run when the
    // richer ones have come up empty.
    val parsed = parseSteps.firstNotNullOfOrNull { step ->
        Log.d(TAG, "downloadXhs: trying the ${step.name} step")
        step.parse(html)
    }

    val media = filterXhs(parsed)?.deduplicated()
    if (media == null) {
        Log.w(TAG, "downloadXhs: every parse step failed, or the filters left nothing, for $url")
        return FetchOutcome.Failure(FetchFailure.NothingFound)
    }

    Log.d(TAG, "downloadXhs: resolved -> $media")
    return FetchOutcome.Success(media)
}
