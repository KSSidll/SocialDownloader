package com.kssidll.socialdownloader.ui

import androidx.compose.ui.tooling.preview.Preview

/**
 * The preview renderer only ships layoutlib up to API 36, so previews fail to render at the
 * project's compileSdk of 37. Pinning them one level down keeps them working; raise this once the
 * renderer catches up.
 */
private const val PREVIEW_API_LEVEL = 36

/**
 * Baseline `@Preview` for this app's composables - apply this instead of `@Preview` directly so the
 * shared settings only have to change in one place.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Preview(
    showBackground = true,
    apiLevel = PREVIEW_API_LEVEL,
)
annotation class DefaultPreview
