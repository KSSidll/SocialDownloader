package com.kssidll.socialdownloader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A rounder shape scale than the M3 baseline (4/8/12/16/28), following the direction Material 3
 * Expressive takes. Set on the theme so components that pick shapes up implicitly - dialogs, cards,
 * the media carousel to come - stay consistent with the ones that ask for a shape by name.
 */
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
