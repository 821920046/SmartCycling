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
val RingBlue = Color(0xFF00FF88)
val RingTrack = Color(0xFF152233)   // 速度环空轨道

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
