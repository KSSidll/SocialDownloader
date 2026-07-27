package com.kssidll.socialdownloader.threads

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure

private const val TAG = "ThreadsRestriction"

/**
 * What Threads renders a missing post under - Barcelona being the name the codebase behind Threads
 * still goes by internally. It sits where Instagram's `PolarisErrorRoot` does and reads the same
 * way, so the same caution applies: it marks an error page, not a particular reason for one.
 *
 * Confirmed by elimination, on a page fetched for a made-up shortcode: thirteen occurrences there,
 * none at all on either real post fetched. That page also carried no `og:image`, where both real
 * ones did.
 */
private val errorRootRegex = Regex("""Barcelona404ErrorRoot""")

/**
 * Works out why a post page yielded nothing.
 *
 * Threads answers 200 with a rendered page either way, so the markup is the only signal. Only
 * reached once every parse step has come up empty - which, since the last of them only needs an
 * `og:image`, already rules out anything that rendered as a post.
 */
fun threadsFailureOf(html: String): FetchFailure {
    val hasErrorRoot = errorRootRegex.containsMatchIn(html)
    Log.d(TAG, "threadsFailureOf: errorRoot=$hasErrorRoot")

    // TODO: unproven as anything more specific than "the post didn't render". A private or
    //  login-gated post has never been seen here, and Threads may well put one under this same
    //  root - in which case "no longer exists" is the wrong thing to tell the user about it. Check
    //  against one before splitting this into branches on a guess.
    if (hasErrorRoot) return FetchFailure.Gone

    // No refusal, no error page, and nothing readable either - which is the parse steps' problem
    // rather than the post's, and shouldn't be dressed up as the post's.
    return FetchFailure.NothingFound
}
