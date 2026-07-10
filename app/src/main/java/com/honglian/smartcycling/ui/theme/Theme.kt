package com.honglian.smartcycling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌霓虹科技 HUD 配色系统
val BrandCyan = Color(0xFF00F0FF)   // 极致霓虹青
val BrandGreen = Color(0xFF00FF88)  // 极致霓虹绿
val RingBlue = Color(0xFF00FF88)
val RingTrack = Color(0xFF152233)   // 速度环空轨道深色

val SpeedText = Color(0xFFFFFFFF)   // 大字纯白
val DataValue = Color(0xFFE0F2FE)   // 亮天蓝数据值
val DataLabel = Color(0xFF94A3B8)   // 灰蓝数据标签
val DividerNavy = Color(0xFF1E293B)  // 分隔线

val PanelBg = Color(0xFF060913)       // 主面板底色(深邃太空蓝黑)
val PanelBgTop = Color(0xFF0D1527)    // 渐变顶
val PanelBgBottom = Color(0xFF04060C) // 渐变底

// 磨砂玻璃色值定义
val GlassBg = Color(0x990A1224)       // 60% 透明深邃卡片
val GlassBorder = Color(0x3300F0FF)   // 20% 透明霓虹青边框
val CardBg = Color(0xFF0F172A)        // 深蓝卡片底
val StopRed = Color(0xFFFF3B30)       // 醒目红
val PauseOrange = Color(0xFFFF9500)   // 暂停橙

// 雷达扫描色
val RadarCenter = Color(0xFF00F0FF)
val RadarEdge = Color(0x0000F0FF)

private val LightColors = lightColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF060913),
    surface = Color(0xFFF1F5F9),
    onSurface = Color(0xFF0F172A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF060913),
    surface = GlassBg,
    onSurface = Color(0xFFF8FAFC),
    background = PanelBg,
    onBackground = Color(0xFFF8FAFC)
)

@Composable
fun SmartCyclingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

