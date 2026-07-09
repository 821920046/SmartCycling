package com.honglian.smartcycling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 高对比度配色:骑行数据面板为浅底,文字/圖环均为深色或鲜明蓝,确保清晰可见。
val RingBlue = Color(0xFF2563EB)   // 速度环进度色
val RingTrack = Color(0xFFE5E7EB)  // 速度环底色
val SpeedText = Color(0xFF1D4ED8)  // 中心大字速度
val DataValue = Color(0xFF111827)  // 数据数值(近黑)
val DataLabel = Color(0xFF6B7280)  // 数据标签(中灰)
val DividerNavy = Color(0xFFCBD5E1) // 分隔线
val PanelBg = Color(0xFFF7F8FB)    // 右侧仪表盘背景

private val LightColors = lightColorScheme(primary = RingBlue)
private val DarkColors = darkColorScheme(primary = RingBlue)

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
