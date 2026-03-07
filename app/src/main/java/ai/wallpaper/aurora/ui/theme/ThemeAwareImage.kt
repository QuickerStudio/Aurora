package ai.wallpaper.aurora.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import ai.wallpaper.aurora.R

/**
 * Returns the appropriate drawable resource based on the current theme.
 *
 * @param isDarkTheme Whether the app is currently in dark theme mode
 * @return The drawable resource ID for the Aurora icon matching the current theme
 */
@Composable
fun getThemeAwareAuroraIcon(isDarkTheme: Boolean = isSystemInDarkTheme()): Int {
    return if (isDarkTheme) {
        R.drawable.aurora_icon_dark
    } else {
        R.drawable.aurora_icon_light
    }
}
