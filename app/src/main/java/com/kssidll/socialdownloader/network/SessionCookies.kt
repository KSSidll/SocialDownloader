package com.kssidll.socialdownloader.network

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SessionCookies"

/**
 * Keeps the cookies a page fetch was handed, so the media fetch that follows is sent them too.
 *
 * Needed because TikTok signs its video URLs against the session that minted them: a `playAddr`
 * answers 403 unless the request carries the `tt_chain_token` cookie the post page set. The signed
 * URL says as much itself - it ends in `tk=tt_chain_token`, naming the cookie it is bound to.
 *
 * Measured, on a real video post: page fetch, then the same URL requested four ways - with neither
 * the cookie nor a Referer, with only one of the two, and with both. Only the last one is served.
 *
 * In memory only, and deliberately so: none of this is a login, it is a few minutes of session
 * state, and letting it go with the process is one less thing anyone has to think about clearing.
 */
internal class SessionCookieJar : CookieJar {
    /**
     * Keyed on the cookie's own domain rather than on the host that sent it, so one set for
     * `.tiktok.com` is offered to `v16-webapp-prime.tiktok.com` as well - which is the whole point,
     * since the page and the video it names are served from different hosts.
     */
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return

        cookies.forEach { cookie ->
            store.getOrPut(cookie.domain) { ConcurrentHashMap() }[cookie.name] = cookie
        }

        Log.d(TAG, "saveFromResponse: stored ${cookies.map { it.name }} from ${url.host}")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookiesFor(url)

    /**
     * Everything held that is still live and that this URL's host, path and scheme qualify for.
     *
     * Public beyond [loadForRequest] because the frame probe needs the same list as a header - it
     * goes through the platform's media stack rather than OkHttp, so this jar never gets asked.
     */
    fun cookiesFor(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()

        // matches() is what applies the domain, path and secure rules, so nothing is offered to a
        // host that wasn't meant to see it.
        return store.values
            .flatMap { it.values }
            .filter { it.expiresAt > now && it.matches(url) }
    }
}

/** One jar for the whole app, for the same reason there is one client - see [httpClient]. */
internal val sessionCookieJar = SessionCookieJar()
