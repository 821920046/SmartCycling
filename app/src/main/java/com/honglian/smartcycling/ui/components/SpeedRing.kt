package com.honglian.smartcycling.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.BrandGreen
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.RingTrack
import com.honglian.smartcycling.ui.theme.SpeedText
import kotlin.math.roundToInt


/** 极致科幻霓虹数值仪表盘(速度/踏频共用) */
@Composable
fun SpeedRing(
    value: Double,
    modifier: Modifier = Modifier,
    unit: String = "km/h",
    maxValue: Double = 60.0,
    diameterDp: Int = 200,
) {
    // 平滑数值动画，消除数据跳跃造成的闪烁感
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "RingValue"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameterDp.dp)) {
        Canvas(Modifier.size(diameterDp.dp)) {
            val center = size / 2.0f
            val strokeWidth = (diameterDp * 0.06f).dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2.0f

            // 1. 绘制科幻外圈刻度尺 (24根刻度线, 每15度一根)
            val tickCount = 24
            val tickLength = (diameterDp * 0.04f).dp.toPx()
            val tickStroke = 2.dp.toPx()
            for (i in 0 until tickCount) {
                val angle = i * (360f / tickCount)
                rotate(angle) {
                    drawLine(
                        color = Color(0x2600F0FF), // 弱发光青
                        start = androidx.compose.ui.geometry.Offset(center.x, strokeWidth * 0.5f),
                        end = androidx.compose.ui.geometry.Offset(center.x, strokeWidth * 0.5f + tickLength),
                        strokeWidth = tickStroke
                    )
                }
            }

            // 2. 绘制深色底轨道
            drawCircle(
                color = RingTrack,
                radius = radius,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )

            // 计算进度比例
            val fraction = (animatedValue / maxValue).coerceIn(0.0f, 1.0f)
            val sweepAngle = fraction * 360f

            if (sweepAngle > 0f) {
                val brush = Brush.sweepGradient(
                    colors = listOf(BrandCyan, BrandGreen, BrandCyan),
                    center = center
                )

                // 3. 绘制底层发光霓虹晕 (较粗，半透明)
                drawArc(
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(strokeWidth * 1.5f, cap = StrokeCap.Round),
                    alpha = 0.25f
                )

                // 4. 绘制前台流光主轨道 (标准粗度，全亮)
                drawArc(
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        
        // 中心数字及单位
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedValue.roundToInt()}",
                fontSize = (diameterDp / 3.2f).sp,
                fontWeight = FontWeight.Black,
                color = SpeedText,
                fontFamily = FontFamily.Monospace // 等宽字体防止跳动
            )
            Text(
                text = unit.uppercase(),
                fontSize = (diameterDp / 11f).sp,
                fontWeight = FontWeight.Bold,
                color = DataLabel,
                letterSpacing = 1.sp
            )
        }
    }
}

