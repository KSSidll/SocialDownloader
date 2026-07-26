package com.kssidll.socialdownloader.media

import com.kssidll.socialdownloader.util.SocialMediaUrl

/**
 * The shape a slot takes before its media has been measured - an item still loading, or a video
 * whose file gave up no frame.
 *
 * Resolved per platform *and* per media type, because the two don't agree even within one source:
 * XHS note images come through 3:4 while its videos are 9:16, so any single guess is wrong for one
 * of them. Getting it close matters only for how much the slot jumps when the real size lands.
 */
data class PlaceholderRatios(
    val image: Float,
    val video: Float,
) {
    fun ratioFor(isVideo: Boolean): Float = if (isVideo) video else image

    companion object {
        /** For a source we have nothing measured for - social media skews portrait. */
        val Default = PlaceholderRatios(image = 3f / 4f, video = 9f / 16f)
    }
}

/** Measured off real notes: images arrive 3:4, videos 9:16. */
private val xhsRatios = PlaceholderRatios(image = 3f / 4f, video = 9f / 16f)

/**
 * Unverified. Neither downloader exists yet, so these are the platforms' documented portrait
 * formats rather than anything observed - worth measuring when those land.
 */
private val instagramRatios = PlaceholderRatios(image = 4f / 5f, video = 9f / 16f)
private val threadsRatios = PlaceholderRatios(image = 4f / 5f, video = 9f / 16f)

val SocialMediaUrl.placeholderRatios: PlaceholderRatios
    get() = when (this) {
        is SocialMediaUrl.Xhs -> xhsRatios
        is SocialMediaUrl.Instagram -> instagramRatios
        is SocialMediaUrl.Threads -> threadsRatios
        is SocialMediaUrl.Unrecognized -> PlaceholderRatios.Default
    }
