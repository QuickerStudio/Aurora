package ai.wallpaper.aurora.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    selectedTheme: String? = null,
    followSystemTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 如果跟随系统主题
        followSystemTheme -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        // 手动选择主题
        else -> {
            when (selectedTheme) {
                "classic" -> darkColorScheme(
                    primary = ClassicPrimary,
                    secondary = ClassicSecondary,
                    background = ClassicBackground,
                    surface = ClassicSurface
                )
                "modern" -> darkColorScheme(
                    primary = ModernPrimary,
                    secondary = ModernSecondary,
                    background = ModernBackground,
                    surface = ModernSurface
                )
                "elegant" -> darkColorScheme(
                    primary = ElegantPrimary,
                    secondary = ElegantSecondary,
                    background = ElegantBackground,
                    surface = ElegantSurface
                )
                "vibrant" -> darkColorScheme(
                    primary = VibrantPrimary,
                    secondary = VibrantSecondary,
                    background = VibrantBackground,
                    surface = VibrantSurface
                )
                else -> if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}