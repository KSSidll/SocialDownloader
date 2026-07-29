package com.kssidll.socialdownloader

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

/**
 * Exists for one reason: to hand Coil an image loader that keeps nothing on disk.
 *
 * Coil's default loader writes every image it fetches into the app's cache directory, bounded by a
 * share of whatever the volume has free - gigabytes on a roomy phone, against a 7 MB app. Nothing
 * ever reads it back. A shared link is a post the app has not seen before, so the strip it fills is
 * new every time, and the download button fetches the file itself rather than lifting bytes out of
 * Coil's cache - see `saveMedia`, which makes its own request.
 *
 * The memory cache is deliberately left as it is: that one is what makes scrolling the strip back
 * and forth free, it holds thumbnails scaled to the strip rather than the originals, and it goes
 * away with the process.
 */
class SocialDownloaderApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Null rather than a disabled policy - no cache to construct is also no directory to
            // create.
            .diskCache(null)
            .build()
}
