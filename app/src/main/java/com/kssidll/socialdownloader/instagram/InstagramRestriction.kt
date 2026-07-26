package com.kssidll.socialdownloader.instagram

import android.util.Log
import com.kssidll.socialdownloader.media.FetchFailure

private const val TAG = "InstagramRestriction"

/**
 * Instagram gates a post in one of two ways, and a page carrying one carries nothing of the other:
 *
 * - **as data.** An error page whose props hold the gate:
 *   `"failure_reason":"MA","restricted_age":18`. The wording the browser shows - "People under 18
 *   can't see this content" - is rendered from that field and never appears in the HTML, so there
 *   is no text here to match. These pages have no `og:image` either.
 * - **as text.** A normally rendered post page, `og:image` and all, with "Age-restricted content"
 *   written into the markup.
 *
 * Hence both checks. Dropping either one lets a whole class of gated post through.
 */
private val restrictedAgeRegex = Regex(""""restricted_age"\s*:\s*(\d+)""")

/**
 * Matched on the text rather than the markup around it because Instagram's class names are
 * generated and change without notice, which makes them useless to anchor on. Deliberately loose
 * between the words so an escaped hyphen or an entity still matches - the phrase is specific enough
 * that the slack costs nothing.
 */
private val ageRestrictedTextRegex =
    Regex("""age.{0,10}?restricted.{0,6}?content""", RegexOption.IGNORE_CASE)

/**
 * Marks an error page of any kind - a gated post produces one too, so on its own it says only that
 * the post did not render, never why. It has to stay below the age checks.
 */
private val errorRootRegex = Regex("""PolarisErrorRoot""")

// TODO: unproven. No response has carried this yet - it was taken from a desktop-User-Agent page we
//  no longer request. Confirm against a private post, and drop the branch if it never appears.
private val loginRedirectRegex = Regex("""accounts/login""")

/**
 * Present on a post page that rendered at all - including the text-gated variant, so this says the
 * link points at something real, not that its media is reachable.
 */
private val postMetaRegex = Regex("""property="og:(image|video)"""")

/**
 * Works out why a post page yielded nothing.
 *
 * Only reached once the embed has come up empty, since the embed is silent about its reasons and
 * this page isn't.
 */
fun instagramFailureOf(html: String): FetchFailure {
    // "MA" - mature audience - rides along in failure_reason, but the age itself is the clearer
    // signal and the one that survives a rename of the reason codes.
    val restrictedAge = restrictedAgeRegex.find(html)?.groupValues?.get(1)
    if (restrictedAge != null) {
        Log.d(TAG, "instagramFailureOf: restricted_age=$restrictedAge")
        return FetchFailure.AgeRestricted
    }

    if (ageRestrictedTextRegex.containsMatchIn(html)) {
        Log.d(TAG, "instagramFailureOf: found the age-restricted wording")
        return FetchFailure.AgeRestricted
    }

    val hasErrorRoot = errorRootRegex.containsMatchIn(html)
    val hasPostMeta = postMetaRegex.containsMatchIn(html)
    Log.d(TAG, "instagramFailureOf: errorRoot=$hasErrorRoot postMeta=$hasPostMeta")

    return when {
        // The page describes a post and didn't refuse us, so we simply failed to pull anything out
        // of it - the honest answer while the parse step is a stub.
        hasPostMeta -> FetchFailure.NothingFound

        loginRedirectRegex.containsMatchIn(html) -> FetchFailure.LoginRequired

        // TODO: unproven as a *deletion* signal. An error root is known to appear for age
        //  restriction, which the checks above take first - whether a genuinely deleted post looks
        //  any different here has never been seen. Confirm against one before trusting the wording
        //  this produces.
        hasErrorRoot -> FetchFailure.Gone

        else -> FetchFailure.NothingFound
    }
}
