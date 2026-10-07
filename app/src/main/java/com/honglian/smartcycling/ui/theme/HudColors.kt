package com.honglian.smartcycling.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 骑行 HUD / 成绩页专用的高对比深色配色。
 *
 * 背景:应用主体已改用 [AppPalette] 语义色板(亮/暗双主题)。但"骑行中仪表盘"与
 * "成绩总结页"是一块**刻意保持深色**的专用表面 —— 骑行时屏幕常处于户外强光或夜间,
 * 这两处的设计目标是"最高对比度的数字呈现",因此保留独立的物理色常量,
 * 而不是套用随主题变化的语义色。
 *
 * 命名沿用历史实现,便于与既有 HUD 组件(DataGrid / SpeedRing / TurnBanner)对接。
 */

val BrandCyan = Color(0xFF00F0FF)   // 霓虹青:主强调色
val BrandGreen = Color(0xFF00FF88)  // 霓虹绿:达标/次要强调

/**
 * 速度环"空轨道"色。
 *
 * 为什么是**半透明白**而不是不透明深藏青?
 * 速度环所在的 HUD 面板本身是半透明的(alpha 0x66~0x88),压在底图上,
 * 用户看到的是**合成后**的颜色。不透明轨道色(原为 `#152233`)在底图偏亮时
 * 会与合成后的面板色撞在一起 —— 实测在"亮夜景"底图 + 竖屏面板上对比度仅
 * **1.05:1**,空轨道等于消失,圆环看起来只剩一段弧。
 *
 * 半透明白对背后的任何颜色都能自适应,且与 `RideScreen` 里既有的
 * `0x14FFFFFF` / `0x33FFFFFF` / `0x59FFFFFF` 轨道与底衬做法一致。
 * 实测 `0x59FFFFFF` 在"5 种 HUD 面板 × 3 种夜景底图"共 15 种组合下
 * 最差 **3.18:1**,满足 WCAG 2.1 SC 1.4.11(非文字对比 ≥ 3:1)。
 */
val RingTrack = Color(0x59FFFFFF)

val SpeedText = Color(0xFFFFFFFF)   // 大字纯白
val DataValue = Color(0xFFE0F2FE)   // 亮天蓝数据值
val DataLabel = Color(0xFF94A3B8)   // 灰蓝数据标签
val DividerNavy = Color(0xFF1E293B) // 分隔线

val PanelBg = Color(0xFF060913)      // 主面板底色
val PanelBgTop = Color(0xFF0D1527)   // 渐变顶
val PanelBgBottom = Color(0xFF04060C) // 渐变底

val GlassBg = Color(0x990A1224)      // 磨砂玻璃卡片
val GlassBorder = Color(0x3300F0FF)  // 霓虹青边框
val CardBg = Color(0xFF0F172A)       // 深蓝卡片底

val StopRed = Color(0xFFFF3B30)      // 结束/危险
val PauseOrange = Color(0xFFFF9500)  // 暂停

val RadarCenter = Color(0xFF00F0FF)
val RadarEdge = Color(0x0000F0FF)
