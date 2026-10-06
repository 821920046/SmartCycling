package com.honglian.smartcycling.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * 环形数值仪表(速度 / 踏频共用)。
 *
 * 视觉原则:去掉发光与霓虹渐变,改用**单一强调色 + 高对比数字**。
 * 户外强光下,可读性来自"对比度与字号",而不是"发光特效"。
 */
@Composable
fun SpeedRing(
    value: Double,
    unit: String,
    maxValue: Double,
    diameterDp: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val animated by animateFloatAsState(
        targetValue = value.toFloat().coerceIn(0f, maxValue.toFloat()),
        animationSpec = tween(durationMillis = 320),
        label = "ring",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameterDp.dp)) {
        Canvas(Modifier.size(diameterDp.dp)) {
            val stroke = (diameterDp * 0.055f).dp.toPx()
            val tickZone = (diameterDp * 0.085f).dp.toPx()
            val ringRadius = (size.minDimension - stroke) / 2f - tickZone
            val center = Offset(size.width / 2f, size.height / 2f)

            // 刻度:每 6° 一根,每 30° 加长加粗
            val tickBase = ringRadius + stroke * 0.75f
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val len = if (major) tickZone * 0.55f else tickZone * 0.3f
                rotate(degrees = i * 6f, pivot = center) {
                    drawLine(
                        color = palette.hudLabel.copy(alpha = if (major) 0.55f else 0.25f),
                        start = Offset(center.x, tickBase),
                        end = Offset(center.x, tickBase + len),
                        strokeWidth = if (major) 2.2f.dp.toPx() else 1.1f.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            drawCircle(
                color = palette.hudTrack,
                radius = ringRadius,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            val fraction = (animated / maxValue).coerceIn(0.0, 1.0).toFloat()
            if (fraction > 0.001f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = fraction * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animated.roundToInt()}",
                fontSize = (diameterDp / 3.3f).sp,
                fontWeight = FontWeight.Bold,
                color = palette.hudValue,
            )
            Text(
                text = unit,
                fontSize = (diameterDp / 12f).sp,
                fontWeight = FontWeight.Medium,
                color = palette.hudLabel,
                letterSpacing = 1.sp,
            )
        }
    }
}
