package com.kssidll.socialdownloader.media

/**
 * What a platform downloader came back with.
 *
 * Downloaders used to answer `Media?`, which made "couldn't reach the page", "parsed nothing" and
 * "the post is age restricted" all the same null. Naming the reason is what lets the status area
 * say something true rather than falling back on "couldn't find any media".
 */
sealed interface FetchOutcome {
    data class Success(val media: Media) : FetchOutcome

    data class Failure(val reason: FetchFailure) : FetchOutcome
}

/** Why a downloader came away with nothing. Platform-neutral - the UI maps these to wording. */
enum class FetchFailure {
    /** The page never arrived: no network, a refused request, a non-success response. */
    Unreachable,

    /** The page arrived and said the post is gated behind an age check. */
    AgeRestricted,

    /** The page arrived and said it needs an account to view. */
    LoginRequired,

    /** The post is deleted, or the link never pointed at one. */
    Gone,

    /** The page arrived and gave up no reason - it simply held nothing we could download. */
    NothingFound,
}
