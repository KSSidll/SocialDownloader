package com.kssidll.socialdownloader.tiktok

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure

private const val TAG = "TikTokRestriction"

/** The payload describes a post, whether or not any media could be read out of it. */
private const val STATUS_OK = 0

/**
 * The id resolves to no post: deleted, or never one to begin with. Confirmed against two invented
 * ids, both of which came back with this status and no `itemInfo` at all.
 */
private const val STATUS_NOT_FOUND = 10204

/**
 * Works out why a post page yielded nothing.
 *
 * TikTok answers 200 with a fully rendered page whatever the outcome, and puts the verdict in the
 * same payload the media would have come from - so unlike Instagram there is no second page worth
 * fetching, and unlike a refusal read out of markup there is nothing here to be talked out of.
 */
fun tiktokFailureOf(html: String): FetchFailure {
    val detail = tiktokPostDetail(html)
    if (detail == null) {
        // Either the page isn't a post page or its shape has moved. Both come out as the same
        // silence, and neither is something to name a reason after.
        Log.w(TAG, "tiktokFailureOf: no post detail to read a status from")
        return FetchFailure.NothingFound
    }

    val statusCode = detail.optInt("statusCode", STATUS_OK)
    Log.d(TAG, "tiktokFailureOf: statusCode=$statusCode")

    return when (statusCode) {
        // The post is there and simply gave up nothing we could take - the honest answer, and the
        // one that leaves a parse step being wrong looking like a parse step being wrong.
        STATUS_OK -> FetchFailure.NothingFound

        STATUS_NOT_FOUND -> FetchFailure.Gone

        // TODO: unproven. TikTok has codes for private and region-blocked posts too, but none has
        //  turned up here yet, and mapping numbers to reasons on the strength of a guess would put
        //  wrong wording in front of the user. They land on the catch-all until one shows up in a
        //  log - the status is printed above precisely so it can be recognised when it does.
        else -> FetchFailure.NothingFound
    }
}
