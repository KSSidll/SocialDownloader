package com.kssidll.socialdownloader.media

data class Media(
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = images.isEmpty() && videos.isEmpty()

    /**
     * The one thing this holds, or null if it holds anything other than exactly one - of either
     * kind, since a lone video is as unambiguous as a lone image.
     */
    val singleUrl: String? get() = (images + videos).singleOrNull()
}

/**
 * Drops repeated URLs within each list, keeping the first of each and the original order.
 *
 * The parse steps already deduplicate what they collect, so in practice this changes nothing - it
 * is here so a future step that forgets to can't put the same item in the picker twice. Kept within
 * each kind deliberately: a URL appearing as both an image and a video means a parse step got
 * something wrong, and quietly merging the two would hide that rather than surface it.
 */
fun Media.deduplicated(): Media = Media(
    images = images.distinct(),
    videos = videos.distinct(),
)
