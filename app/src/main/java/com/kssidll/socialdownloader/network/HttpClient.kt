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

/** One client for the whole app, so page fetches, probes and downloads share a connection pool. */
internal val httpClient = OkHttpClient()
