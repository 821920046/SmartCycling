package com.honglian.smartcycling.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义化色板。
 *
 * 第一性原理 —— 为什么不再用"霓虹青 + 纯黑"?
 * 这个 App 的主战场是**户外日光下的车把**。OLED 深色 HUD 在强光下对比度骤降,
 * 白色高亮数字反而比霓虹色更可读;而霓虹发光会糊掉细小刻度。
 * 因此设计目标改为:**高对比、低装饰、信息优先**。
 * 深色主题保留(夜骑省电、不刺眼),但同样走"克制 + 高对比"路线。
 *
 * 所有颜色都以**语义**命名(background/surface/textPrimary…),而不是"cyan/green",
 * 这样换肤、换品牌色时只需改这一处,界面代码零改动。
 */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val outlineStrong: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val glassSurface: Color,
    /** 地图/相机类界面上的悬浮控件底色。 */
    val floatingSurface: Color,
    /** 骑行 HUD 的底色与主数字色(与普通页面解耦,以便单独调高对比)。 */
    val hudBackground: Color,
    val hudValue: Color,
    val hudLabel: Color,
    val hudTrack: Color,
)

// ------------------------------------------------------------------ 亮色

private val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEFF4),
    outline = Color(0xFFDCE3EB),
    outlineStrong = Color(0xFFB8C3D1),
    primary = Color(0xFF1B6EF3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EDFE),
    onPrimaryContainer = Color(0xFF0B3E8F),
    success = Color(0xFF12A150),
    warning = Color(0xFFE08700),
    danger = Color(0xFFE5484D),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5B6875),
    textTertiary = Color(0xFF8B96A3),
    scrim = Color(0x66000000),
    glassSurface = Color(0xF2FFFFFF),
    floatingSurface = Color(0xF7FFFFFF),
    hudBackground = Color(0xFFFFFFFF),
    hudValue = Color(0xFF0B0F14),
    hudLabel = Color(0xFF5B6875),
    hudTrack = Color(0xFFE4E9EF),
)

// ------------------------------------------------------------------ 暗色

private val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF0C1015),
    surface = Color(0xFF141A21),
    surfaceVariant = Color(0xFF1C242E),
    outline = Color(0xFF28323D),
    outlineStrong = Color(0xFF3C4A58),
    primary = Color(0xFF4E93FF),
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF16304F),
    onPrimaryContainer = Color(0xFFCFE0FF),
    success = Color(0xFF35D07F),
    warning = Color(0xFFFFB020),
    danger = Color(0xFFFF5A5F),
    textPrimary = Color(0xFFF3F6FA),
    textSecondary = Color(0xFFA6B2C0),
    textTertiary = Color(0xFF6E7B89),
    scrim = Color(0x99000000),
    glassSurface = Color(0xE6141A21),
    floatingSurface = Color(0xF0141A21),
    hudBackground = Color(0xFF0C1015),
    hudValue = Color(0xFFFFFFFF),
    hudLabel = Color(0xFFA6B2C0),
    hudTrack = Color(0xFF28323D),
)

/** 当前生效的色板。 */
val LocalAppPalette = staticCompositionLocalOf { LightPalette }

internal fun paletteFor(dark: Boolean): AppPalette = if (dark) DarkPalette else LightPalette
