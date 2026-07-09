package com.honglian.smartcycling.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.RingBlue
import com.honglian.smartcycling.ui.theme.RingTrack
import com.honglian.smartcycling.ui.theme.SpeedText
import kotlin.math.roundToInt

/** 环形速度表 + 中心大字。字号/线宽随直径自适应。 */
@Composable
fun SpeedRing(
    speedKmh: Double,
    modifier: Modifier = Modifier,
    maxKmh: Double = 60.0,
    diameterDp: Int = 200,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameterDp.dp)) {
        Canvas(Modifier.size(diameterDp.dp)) {
            val stroke = (diameterDp * 0.07f).dp.toPx()
            drawArc(RingTrack, 0f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val fraction = (speedKmh / maxKmh).coerceIn(0.0, 1.0).toFloat()
            drawArc(RingBlue, -90f, fraction * 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${speedKmh.roundToInt()}",
                fontSize = (diameterDp / 4.2f).sp,
                fontWeight = FontWeight.Bold,
                color = SpeedText,
            )
            Text(text = "km/h", fontSize = (diameterDp / 13f).sp, color = DataLabel)
        }
    }
}
