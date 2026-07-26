package com.kssidll.socialdownloader.network

import okhttp3.OkHttpClient

/**
 * Sites serve different (often stripped-down) markup to clients that don't look like a real
 * browser, and some CDNs turn media requests away on the same basis - so every request the app
 * makes, page or media, goes out behind this.
 */
internal const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

/**
 * What a browser asks for when it navigates to a page.
 *
 * OkHttp sends no Accept header at all, and sites read that absence as "not a browser". Instagram
 * in particular then serves a link-preview page instead of the real one - measurably: the same
 * request with this header added is what turns an age-restricted post from a normal-looking preview
 * into the actual gated page that says so.
 */
internal const val BROWSER_ACCEPT =
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

/**
 * One client for the whole app, so page fetches, probes and downloads share a connection pool - and
 * so they all pick up the same per-host headers.
 */
internal val httpClient = OkHttpClient.Builder()
    .addInterceptor(HostHeaderInterceptor())
    .build()
