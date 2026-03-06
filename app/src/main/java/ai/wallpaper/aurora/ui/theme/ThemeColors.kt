package ai.wallpaper.aurora.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题颜色配置
 * 每个主题包含完整的UI颜色方案
 */
data class ThemeColors(
    // 主色调
    val primary: Color,
    val secondary: Color,

    // 背景色
    val background: Color,
    val surface: Color,

    // 顶部栏
    val topBarBackground: Color,
    val topBarContent: Color,

    // 侧边栏
    val drawerBackground: Color,
    val drawerSurface: Color,

    // 视频卡片
    val cardBorder: Color,
    val cardBackground: Color,

    // 按钮
    val buttonBackground: Color,
    val buttonContent: Color,

    // 文字
    val onBackground: Color,
    val onSurface: Color,
    val onPrimary: Color
)

/**
 * Classic 主题 - 紫色与金色撞色
 */
val ClassicThemeColors = ThemeColors(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFFFFD700),  // 金色

    background = Color(0xFF1A0F2E),
    surface = Color(0xFF2D1F4A),

    topBarBackground = Color(0xFF2D1F4A),
    topBarContent = Color(0xFFFFD700),

    drawerBackground = Color(0xFF1A0F2E),
    drawerSurface = Color(0xFF2D1F4A),

    cardBorder = Color(0xFFFFD700),
    cardBackground = Color(0xFF2D1F4A),

    buttonBackground = Color(0xFF6200EE),
    buttonContent = Color(0xFFFFFFFF),

    onBackground = Color(0xFFE1E1E1),
    onSurface = Color(0xFFE1E1E1),
    onPrimary = Color(0xFFFFFFFF)
)

/**
 * Modern 主题 - 青色与橙色撞色
 */
val ModernThemeColors = ThemeColors(
    primary = Color(0xFF03DAC5),
    secondary = Color(0xFFFF6B35),  // 橙色

    background = Color(0xFF0F2A2A),
    surface = Color(0xFF1F3D3D),

    topBarBackground = Color(0xFF1F3D3D),
    topBarContent = Color(0xFFFF6B35),

    drawerBackground = Color(0xFF0F2A2A),
    drawerSurface = Color(0xFF1F3D3D),

    cardBorder = Color(0xFFFF6B35),
    cardBackground = Color(0xFF1F3D3D),

    buttonBackground = Color(0xFF03DAC5),
    buttonContent = Color(0xFF000000),

    onBackground = Color(0xFFE1E1E1),
    onSurface = Color(0xFFE1E1E1),
    onPrimary = Color(0xFF000000)
)

/**
 * Elegant 主题 - 紫罗兰与玫瑰金撞色
 */
val ElegantThemeColors = ThemeColors(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFFE6A8D7),  // 玫瑰金

    background = Color(0xFF251A30),
    surface = Color(0xFF3A2F45),

    topBarBackground = Color(0xFF3A2F45),
    topBarContent = Color(0xFFE6A8D7),

    drawerBackground = Color(0xFF251A30),
    drawerSurface = Color(0xFF3A2F45),

    cardBorder = Color(0xFFE6A8D7),
    cardBackground = Color(0xFF3A2F45),

    buttonBackground = Color(0xFFBB86FC),
    buttonContent = Color(0xFF000000),

    onBackground = Color(0xFFE1E1E1),
    onSurface = Color(0xFFE1E1E1),
    onPrimary = Color(0xFF000000)
)

/**
 * Vibrant 主题 - 粉红与青绿撞色
 */
val VibrantThemeColors = ThemeColors(
    primary = Color(0xFFCF6679),
    secondary = Color(0xFF00E5CC),  // 青绿色

    background = Color(0xFF2A0F1A),
    surface = Color(0xFF3F1F2F),

    topBarBackground = Color(0xFF3F1F2F),
    topBarContent = Color(0xFF00E5CC),

    drawerBackground = Color(0xFF2A0F1A),
    drawerSurface = Color(0xFF3F1F2F),

    cardBorder = Color(0xFF00E5CC),
    cardBackground = Color(0xFF3F1F2F),

    buttonBackground = Color(0xFFCF6679),
    buttonContent = Color(0xFFFFFFFF),

    onBackground = Color(0xFFE1E1E1),
    onSurface = Color(0xFFE1E1E1),
    onPrimary = Color(0xFFFFFFFF)
)

/**
 * 根据主题ID获取主题颜色配置
 */
fun getThemeColors(themeId: String): ThemeColors {
    return when (themeId) {
        "classic" -> ClassicThemeColors
        "modern" -> ModernThemeColors
        "elegant" -> ElegantThemeColors
        "vibrant" -> VibrantThemeColors
        else -> ClassicThemeColors
    }
}
