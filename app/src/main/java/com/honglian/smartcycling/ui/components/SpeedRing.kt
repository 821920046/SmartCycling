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
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.RingTrack
import com.honglian.smartcycling.ui.theme.SpeedText
import kotlin.math.roundToInt

/**
 * 环形数值仪表(速度 / 踏频共用)。
 *
 * 视觉原则:去掉发光与霓虹渐变,改用**单一强调色 + 高对比数字**。
 * 户外强光下,可读性来自"对比度与字号",而不是"发光特效"。
 *
 * ⚠️ 配色契约:本组件**只**用在骑行 HUD 里,而骑行 HUD 的容器是**刻意写死的深色**
 * (见 `ui/theme/HudColors.kt`)。因此这里必须使用
 * [SpeedText] / [DataLabel] / [RingTrack] 这类**恒定物理色**,不能读 `AppTheme.palette`
 * —— 否则切到亮色主题后,数字会变成近黑色压在深色玻璃上。
 * 实测(亮色主题 + 默认夜景底图):速度大数字对比度仅 **1.05:1**,等于看不见。
 * 强调色由调用方传入(它才知道当前该用青还是绿)。
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

            // 刻度:每 6° 一根,每 30° 加长加粗。
            // 层级由"长度 + 线宽"承担(0.55/0.30 × 2.2/1.1 dp),alpha 只负责压暗,
            // 因此次刻度仍需保持可感知:alpha=0.25 在横屏高对比面板上实测仅 1.49:1,
            // 等于看不见;提到 0.34 后最差 1.84:1。
            val tickBase = ringRadius + stroke * 0.75f
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val len = if (major) tickZone * 0.55f else tickZone * 0.3f
                rotate(degrees = i * 6f, pivot = center) {
                    drawLine(
                        color = DataLabel.copy(alpha = if (major) 0.55f else 0.34f),
                        start = Offset(center.x, tickBase),
                        end = Offset(center.x, tickBase + len),
                        strokeWidth = if (major) 2.2f.dp.toPx() else 1.1f.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            drawCircle(
                color = RingTrack,
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
                color = SpeedText,
            )
            Text(
                text = unit,
                fontSize = (diameterDp / 12f).sp,
                fontWeight = FontWeight.Medium,
                color = DataLabel,
                letterSpacing = 1.sp,
            )
        }
    }
}
