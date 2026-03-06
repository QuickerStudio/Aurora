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
 * Classic 主题 - 深紫罗兰 + 柔和金色（优雅经典）
 */
val ClassicThemeColors = ThemeColors(
    primary = Color(0xFF7C4DFF),        // 明亮紫罗兰
    secondary = Color(0xFFFFD54F),      // 柔和金色

    background = Color(0xFF1A1625),     // 深紫色背景
    surface = Color(0xFF2D2438),        // 紫色表面

    topBarBackground = Color(0xFF2D2438),
    topBarContent = Color(0xFFFFD54F),  // 金色文字

    drawerBackground = Color(0xFF1A1625),
    drawerSurface = Color(0xFF2D2438),

    cardBorder = Color(0xFFFFD54F),     // 金色边框
    cardBackground = Color(0xFF2D2438),

    buttonBackground = Color(0xFF7C4DFF),
    buttonContent = Color(0xFFFFFFFF),

    onBackground = Color(0xFFE8E3F0),
    onSurface = Color(0xFFE8E3F0),
    onPrimary = Color(0xFFFFFFFF)
)

/**
 * Modern 主题 - 青绿色 + 珊瑚橙（现代活力）
 */
val ModernThemeColors = ThemeColors(
    primary = Color(0xFF26C6DA),        // 明亮青色
    secondary = Color(0xFFFF7043),      // 珊瑚橙

    background = Color(0xFF0D1F1F),     // 深青色背景
    surface = Color(0xFF1A3333),        // 青色表面

    topBarBackground = Color(0xFF1A3333),
    topBarContent = Color(0xFFFF7043),  // 橙色文字

    drawerBackground = Color(0xFF0D1F1F),
    drawerSurface = Color(0xFF1A3333),

    cardBorder = Color(0xFFFF7043),     // 橙色边框
    cardBackground = Color(0xFF1A3333),

    buttonBackground = Color(0xFF26C6DA),
    buttonContent = Color(0xFF000000),

    onBackground = Color(0xFFE0F2F1),
    onSurface = Color(0xFFE0F2F1),
    onPrimary = Color(0xFF000000)
)

/**
 * Elegant 主题 - 薰衣草紫 + 玫瑰金（优雅浪漫）
 */
val ElegantThemeColors = ThemeColors(
    primary = Color(0xFFB39DDB),        // 薰衣草紫
    secondary = Color(0xFFF8BBD0),      // 柔和玫瑰粉

    background = Color(0xFF1F1A28),     // 深紫罗兰背景
    surface = Color(0xFF332D3E),        // 紫色表面

    topBarBackground = Color(0xFF332D3E),
    topBarContent = Color(0xFFF8BBD0),  // 玫瑰粉文字

    drawerBackground = Color(0xFF1F1A28),
    drawerSurface = Color(0xFF332D3E),

    cardBorder = Color(0xFFF8BBD0),     // 玫瑰粉边框
    cardBackground = Color(0xFF332D3E),

    buttonBackground = Color(0xFFB39DDB),
    buttonContent = Color(0xFF000000),

    onBackground = Color(0xFFF3E5F5),
    onSurface = Color(0xFFF3E5F5),
    onPrimary = Color(0xFF000000)
)

/**
 * Vibrant 主题 - 玫瑰红 + 薄荷绿（活力清新）
 */
val VibrantThemeColors = ThemeColors(
    primary = Color(0xFFEC407A),        // 玫瑰红
    secondary = Color(0xFF80CBC4),      // 薄荷绿

    background = Color(0xFF1F0D14),     // 深玫瑰背景
    surface = Color(0xFF331A24),        // 玫瑰色表面

    topBarBackground = Color(0xFF331A24),
    topBarContent = Color(0xFF80CBC4),  // 薄荷绿文字

    drawerBackground = Color(0xFF1F0D14),
    drawerSurface = Color(0xFF331A24),

    cardBorder = Color(0xFF80CBC4),     // 薄荷绿边框
    cardBackground = Color(0xFF331A24),

    buttonBackground = Color(0xFFEC407A),
    buttonContent = Color(0xFFFFFFFF),

    onBackground = Color(0xFFFCE4EC),
    onSurface = Color(0xFFFCE4EC),
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
