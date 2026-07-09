package com.honglian.smartcycling.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.BrandGreen
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.RingTrack
import com.honglian.smartcycling.ui.theme.SpeedText
import kotlin.math.roundToInt

/** 通用数值环(速度/踏频共用) + 中心大字。字号/线宽随直径自适应。 */
@Composable
fun SpeedRing(
    value: Double,
    modifier: Modifier = Modifier,
    unit: String = "km/h",
    maxValue: Double = 60.0,
    diameterDp: Int = 200,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameterDp.dp)) {
        Canvas(Modifier.size(diameterDp.dp)) {
            val stroke = (diameterDp * 0.07f).dp.toPx()
            drawArc(RingTrack, 0f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val fraction = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
            drawArc(
                brush = Brush.linearGradient(listOf(BrandCyan, BrandGreen)),
                startAngle = -90f,
                sweepAngle = fraction * 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${value.roundToInt()}",
                fontSize = (diameterDp / 3.4f).sp,
                fontWeight = FontWeight.Bold,
                color = SpeedText,
            )
            Text(
                text = unit,
                fontSize = (diameterDp / 10f).sp,
                fontWeight = FontWeight.Bold,
                color = DataLabel,
            )
        }
    }
}
