package com.kssidll.socialdownloader.xhs

import android.util.Log
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.network.fetchHtml

private const val TAG = "XhsDownloader"

/** Fetches the note page, then tries each parse strategy in order until one returns something. */
suspend fun downloadXhs(url: String): Media? {
    Log.d(TAG, "downloadXhs: starting for $url")

    val html = fetchHtml(url)
    if (html == null) {
        Log.w(TAG, "downloadXhs: fetch failed for $url")
        return null
    }

    Log.d(TAG, "downloadXhs: fetch ok, trying __INITIAL_STATE__ JSON step")
    val jsonResult = parseXhsInitialState(html)
    if (jsonResult != null) {
        Log.d(TAG, "downloadXhs: parsed via __INITIAL_STATE__ JSON -> $jsonResult")
        return jsonResult
    }

    Log.d(TAG, "downloadXhs: JSON step came up empty, trying og meta tag step")
    val metaResult = parseXhsMetaTags(html)
    if (metaResult != null) {
        Log.d(TAG, "downloadXhs: parsed via og meta tags -> $metaResult")
        return metaResult
    }

    Log.w(TAG, "downloadXhs: every parse step failed for $url")
    return null
}
