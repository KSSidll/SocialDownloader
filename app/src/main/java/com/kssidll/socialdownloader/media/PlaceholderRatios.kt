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
 * Measured off a real post of each kind, the same way the XHS pair was: a photo post reported its
 * images as 1440x2156, 1440x2164 and 1440x2200 - near enough 2:3 for all three - and a video post
 * came back 576x1024.
 */
private val tiktokRatios = PlaceholderRatios(image = 2f / 3f, video = 9f / 16f)

// TODO: unproven. These are the platform's documented portrait formats, not anything measured -
//  unlike the XHS pair above, which came from real notes. Instagram in particular mixes 1:1, 4:5
//  and 9:16 in a way a single guess can't cover, so measure real posts of each kind once the
//  Instagram parser returns media. Wrong values only cost a bigger jump when the real size lands.
private val instagramRatios = PlaceholderRatios(image = 4f / 5f, video = 9f / 16f)

/**
 * Measured, but across a spread rather than onto a number: two real posts gave images at 2:3, 2:3,
 * 3:2, 3:2 and 4:5, so no single guess is right for more than some of them and 4:5 is picked for
 * sitting between the portrait ones. Both videos seen were 4:5 - which is why this pair doesn't
 * repeat Instagram's 9:16, guessed above and measured here.
 */
private val threadsRatios = PlaceholderRatios(image = 4f / 5f, video = 4f / 5f)

val SocialMediaUrl.placeholderRatios: PlaceholderRatios
    get() = when (this) {
        is SocialMediaUrl.Xhs -> xhsRatios
        is SocialMediaUrl.TikTok -> tiktokRatios
        is SocialMediaUrl.InstagramShortcode -> instagramRatios
        is SocialMediaUrl.Threads -> threadsRatios
        is SocialMediaUrl.Unrecognized -> PlaceholderRatios.Default
    }
