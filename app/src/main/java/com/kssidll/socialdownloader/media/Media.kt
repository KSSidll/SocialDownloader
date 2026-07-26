package com.kssidll.socialdownloader.media

data class Media(
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = images.isEmpty() && videos.isEmpty()
}
