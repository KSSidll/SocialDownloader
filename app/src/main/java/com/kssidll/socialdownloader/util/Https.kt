package com.kssidll.socialdownloader.util

/**
 * Upgrades a cleartext URL to https.
 *
 * XHS hands out plain `http://` freely - both in shared links and in the CDN media URLs embedded in
 * a note - while Android blocks cleartext traffic by default from targetSdk 28 on. Upgrading each
 * URL beats loosening the app's network security policy for every request it makes.
 */
fun String.asHttps(): String = replaceFirst("http://", "https://")
