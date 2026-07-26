@file:Suppress("unused")

package com.kssidll.socialdownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kssidll.socialdownloader.ui.theme.schema.DarkColorScheme
import com.kssidll.socialdownloader.ui.theme.schema.LightColorScheme

const val disabledAlpha = 0.38f
const val optionalAlpha = 0.60f

/**
 * @param darkTheme Whether the color scheme should be a dark theme one
 * @param dynamicColor Whether to use dynamic color to build the color scheme
 * @return Color scheme to use
 */
@Composable
fun getColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    return when {
        // dynamic color is available since API 31
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
}

/** @return Whether the app is considered to be in dark theme */
@Composable
fun isAppInDarkTheme(): Boolean {
    return isSystemInDarkTheme()
}

/**
 * Default application theme
 *
 * @param content Content to provide the theme to
 */
@Composable
fun Theme(
    isInDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        getColorScheme(
            darkTheme = isAppInDarkTheme(),
            dynamicColor = isInDynamicColor,
        )

    MaterialTheme(colorScheme = colorScheme, typography = Typography) {
        content()
    }
}
