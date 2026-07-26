package com.kssidll.socialdownloader.network

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "RequestHeaders"

/** Carried by every request the app makes, whatever it is for. */
private val commonHeaders = mapOf("User-Agent" to MOBILE_USER_AGENT)

/**
 * Extra headers a particular host wants.
 *
 * Keyed on host rather than passed down from the caller because the media layer is deliberately
 * platform-blind - [saveMedia][com.kssidll.socialdownloader.media.saveMedia] takes a URL and
 * nothing else. Matching here keeps it that way while still letting a site's CDN be sent whatever
 * it insists on.
 *
 * Suffix matched, so `scontent-fra3-2.cdninstagram.com` picks up the same rule as `instagram.com`.
 */
private class HostRule(val suffixes: List<String>, val headers: Map<String, String>) {
    fun matches(host: String) = suffixes.any { host == it || host.endsWith(".$it") }
}

private val hostRules = listOf(
    // TODO: unproven. Instagram's CDN is widely said to want a Referer, but nothing here has needed
    //  it yet - the age-restricted path works without it, and no downloadable post has been tested.
    //  If a media fetch turns out to work without this, drop the rule rather than keep it for luck.
    HostRule(
        suffixes = listOf("instagram.com", "cdninstagram.com", "fbcdn.net"),
        headers = mapOf("Referer" to "https://www.instagram.com/"),
    ),
)

/** Everything that should be sent to [host], defaults first and host rules layered on top. */
internal fun requestHeadersFor(host: String): Map<String, String> =
    commonHeaders + hostRules.filter { it.matches(host) }.flatMap { it.headers.entries }
        .associate { it.key to it.value }

/**
 * Same, for callers holding a URL string rather than a host - notably the frame probe, which goes
 * through the platform's own media stack instead of OkHttp and so has to be handed its headers.
 */
internal fun requestHeadersForUrl(url: String): Map<String, String> =
    requestHeadersFor(url.toHttpUrlOrNull()?.host.orEmpty())

/**
 * Applies [requestHeadersFor] to everything going through the client.
 *
 * Only fills in headers a caller hasn't already set, so anything specific to one request - a Range,
 * or a deliberately different User-Agent - still wins.
 */
internal class HostHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        val applied = requestHeadersFor(request.url.host)
            .filterKeys { request.header(it) == null }

        applied.forEach { (name, value) -> builder.header(name, value) }

        if (applied.isNotEmpty()) {
            Log.d(TAG, "intercept: added ${applied.keys} for ${request.url.host}")
        }

        return chain.proceed(builder.build())
    }
}
