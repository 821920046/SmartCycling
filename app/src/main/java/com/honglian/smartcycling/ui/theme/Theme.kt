package com.honglian.smartcycling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RingBlue = Color(0xFF9DB4FF)
val RingTrack = Color(0xFFE6E9F5)
val SpeedText = Color(0xFFB3C0F0)
val DataValue = Color(0xFF9AA3B2)
val DataLabel = Color(0xFFB8BEC9)
val DividerNavy = Color(0xFF1B2A4A)

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
