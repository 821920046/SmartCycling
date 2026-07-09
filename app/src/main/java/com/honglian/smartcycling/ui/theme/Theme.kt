package com.honglian.smartcycling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 深色骑行码表 HUD 配色:与夜间地图、青绿渐变 logo 保持一致,整体和谐沉稳。
val BrandCyan = Color(0xFF22D3EE)  // 品牌青(渐变起)
val BrandGreen = Color(0xFF34D399) // 品牌绿(渐变止)
val RingBlue = Color(0xFF34D399)   // 兼容旧引用:强调色回退为品牌绿
val RingTrack = Color(0xFF1C2632)  // 速度环底色(深)
val SpeedText = Color(0xFFF3FBF8)  // 中心大字(亮)
val DataValue = Color(0xFFEEF3F8)  // 数据数值(亮)
val DataLabel = Color(0xFF8595A4)  // 数据标签(中灰)
val DividerNavy = Color(0xFF212C3A) // 分隔线(深)
val PanelBg = Color(0xFF0B0F17)    // 面板底色(近黑,贴合夜间地图)
val PanelBgTop = Color(0xFF111A28) // 面板渐变顶
val PanelBgBottom = Color(0xFF080B12) // 面板渐变底
val CardBg = Color(0xFF141E2C)     // 数据卡背景
val StopRed = Color(0xFFE2504E)    // 结束骑行按钮

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
