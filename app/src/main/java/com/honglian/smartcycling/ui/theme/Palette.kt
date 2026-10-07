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
 *
 * ⚠️ 作用域边界:**本色板只服务"普通页面"**(历史/地图/设置/配对/离线地图)。
 * 这些页面会随主题在亮/暗之间切换,因此必须读语义色。
 * 而"骑行 HUD"与"成绩总结页"是**刻意写死的深色专用表面**,一律使用
 * `HudColors.kt` 里的恒定物理色(`SpeedText` / `DataLabel` / `RingTrack` / `PanelBg*` …)。
 *
 * 历史上本类曾有 `hudValue / hudLabel / hudTrack / hudBackground` 四个字段,
 * 它们随主题变化,却被铺在刻意写死的深色玻璃面板上 —— 亮色主题下数字变成近黑色
 * `#0B0F14` 压在同色深底上,实测对比度仅 **1.05:1**,等于看不见。
 * 现已**删除**这四个字段:让"在深色 HUD 上误用主题色"在**编译期**就不可能发生,
 * 而不是靠注释提醒。
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
    // ------------------------------------------------------------------
    // 亮色下的三个强调色都被当作**正文/标签文字**使用,所以必须按文字标准
    // (WCAG AA >= 4.5:1)校验,不能只按"图标/色块"的 3:1 来选。
    // 实际用途:配对页的蓝牙/定位告警与连接状态、离线地图的导入结果与删除确认、
    // 设置页的"清空数据"、以及对话框里的危险操作按钮。
    //
    // 原值在 App 背景 #F4F6F9 上实测(对白底/浅灰底取最差值):
    //   success #12A150 = 3.11:1   warning #E08700 = 2.54:1   danger #E5484D = 3.61:1
    // 三者全部不达标 —— 即整个亮色主题的强调色都不满足正文可读性。
    // 加深后(最差值 / 白底值):
    //   success #0B7A3D = 5.02 / 5.43
    //   warning #9C5D00 = 4.87 / 5.28
    //   danger  #C93034 = 4.90 / 5.31
    // 只改亮色:暗色下 #35D07F / #FFB020 / #FF5A5F 在深底上是 5.7~9.6:1,本就达标。
    success = Color(0xFF0B7A3D),
    warning = Color(0xFF9C5D00),
    danger = Color(0xFFC93034),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5B6875),
    textTertiary = Color(0xFF8B96A3),
    scrim = Color(0x66000000),
    glassSurface = Color(0xF2FFFFFF),
    floatingSurface = Color(0xF7FFFFFF),
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
)

/** 当前生效的色板。 */
val LocalAppPalette = staticCompositionLocalOf { LightPalette }

internal fun paletteFor(dark: Boolean): AppPalette = if (dark) DarkPalette else LightPalette
