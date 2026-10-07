package com.honglian.smartcycling.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.R
import com.honglian.smartcycling.core.Units
import com.honglian.smartcycling.ride.AltitudeGauge
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.BrandGreen
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.PauseOrange
import com.honglian.smartcycling.ui.theme.SpeedText
import kotlin.math.PI
import kotlin.math.sin

/**
 * 实时海拔水柱。
 *
 * ## 为什么是"水柱"而不是又一个数字格
 * 累计爬升是"净结果",看不出此刻在爬还是在放;而海拔是个**连续变化的物理量**,
 * 用一根会涨落的水柱来表达,扫一眼就知道"在爬 / 在放 / 到顶了",不需要读数字。
 * 所以这里同时给两样东西:一根动的水柱(趋势感知)+ 一个大号数字(精确读数)。
 *
 * ## 数据契约
 * - 海拔与量程下限都来自 [RideState];`null` 表示还没拿到有效高程读数
 *   (刚进骑行页、GPS 未定位、或设备不提供高程),此时水柱不画,数字显示 `--`,
 *   控件本身仍在 —— 让用户看得出"功能在,只是在等定位",而不是以为界面坏了。
 * - 量程的推导全部在 [AltitudeGauge] 里(纯函数、有单测),这里只负责画。
 *
 * ## 已知精度限制(如实记录)
 * `Location.getAltitude()` 返回的是**相对 WGS84 椭球**的高度,不是海拔(大地水准面高)。
 * 在中国大陆,两者的差值(大地水准面差距)大致在 -10 ~ -40 米量级,也就是说
 * 本控件显示的数值可能比地图上的海拔标注系统性偏高十几到几十米。
 * 这不是 bug,是 Android 定位 API 的口径;要修正需要引入大地水准面模型(体积很大),
 * 对一个骑行 App 不划算。**相对变化(爬升、放坡)完全不受影响**,因为该偏差在短距离内近似恒定。
 *
 * ## 配色
 * 骑行 HUD 是刻意常驻深色的专用表面(见 HudColors 的说明),因此这里直接用固定的
 * 深色玻璃底与霓虹强调色,不跟随亮/暗主题 —— 跟随主题会让白底卡片压在夜间地图上刺眼。
 */
@Composable
fun AltitudeColumn(state: RideState, modifier: Modifier = Modifier) {
    val units = AppTheme.units
    val altitudeM = state.currentAltitudeM
    val baseM = state.altitudeBaseM

    val window = if (altitudeM != null && baseM != null) {
        AltitudeGauge.window(baseM, altitudeM)
    } else {
        null
    }
    val target = if (window != null && altitudeM != null) window.fraction(altitudeM) else 0f
    val fill by animateFloatAsState(
        targetValue = target,
        // 900ms 明显慢于 1Hz 的数据刷新:把"量程翻倍"那一跳抹成一次平滑换挡,
        // 而不是让水面瞬间从满格砸到半程。
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "altitudeFill",
    )

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // 水柱高度按屏高取比例后上下夹紧:横屏屏高只有 300~400dp,纯比例会矮到看不清;
    // 竖屏屏高 800dp 左右,纯比例又会顶到底部的悬浮仪表盘。
    val columnHeight = (screenHeightDp * 0.22f).coerceIn(110f, 190f).dp
    // 水面波动的相位绑在海拔上:爬升时水面在动,停车时水面也停 ——
    // 比挂一个无限循环动画省电得多(骑行 App 的屏幕常亮几小时,帧率就是电量)。
    val waterPhase = ((altitudeM ?: 0.0) * 0.02).toFloat()

    Column(
        modifier
            .width(96.dp)
            .background(Color(0x9904121A), RoundedCornerShape(18.dp))
            .border(1.dp, BrandCyan.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.altitude_title),
            fontSize = 11.sp,
            color = DataLabel,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (altitudeM == null) "--" else "%.0f".format(Units.elevation(altitudeM, units)),
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = SpeedText,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                Units.elevationUnit(units),
                fontSize = 11.sp,
                color = DataLabel,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        // 量程上限:必须显示。否则"量程翻倍换挡"时水面突然回落会被当成故障。
        Text(
            text = if (window == null) "--" else Units.elevationText(window.topM, units),
            fontSize = 9.sp,
            color = DataLabel.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(3.dp))
        Canvas(Modifier.fillMaxWidth().height(columnHeight)) {
            val cornerRadius = 9.dp.toPx()
            val outline = Path().apply {
                addRoundRect(
                    RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerRadius, cornerRadius)),
                )
            }
            clipPath(outline) {
                drawRect(color = Color(0x33FFFFFF))
                if (fill > 0f) {
                    val waterTop = size.height * (1f - fill)
                    val amp = 2.5.dp.toPx()
                    val twoPi = (2.0 * PI).toFloat()
                    val steps = 12
                    val water = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, waterTop)
                        for (i in 1..steps) {
                            val x = size.width * i / steps
                            lineTo(x, waterTop + amp * sin(waterPhase + i.toFloat() / steps * twoPi))
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(
                        path = water,
                        // 渐变锚在水体本身(而不是整根轨道):无论水多高,液面永远是最亮的青色,
                        // 这样"水面在哪"在任何水位下都能一眼看到。
                        brush = Brush.verticalGradient(
                            colors = listOf(BrandCyan, BrandGreen),
                            startY = waterTop,
                            endY = size.height,
                        ),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(0f, waterTop),
                        end = Offset(size.width, waterTop),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            // 描边画在最后:clipPath 会把内容裁到圆角内,边框必须压在裁切线之上才不会被啃掉半像素。
            drawPath(outline, color = BrandCyan.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (window == null) "--" else Units.elevationText(window.baseM, units),
            fontSize = 9.sp,
            color = DataLabel.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(5.dp))
        AltitudeTrendRow(state.altitudeTrend)
    }
}

/** 海拔趋势小标:箭头 + 文字。趋势是三态值,不做任何推断,拿到什么显示什么。 */
@Composable
private fun AltitudeTrendRow(trend: Int) {
    val arrow: String
    val labelRes: Int
    val tint: Color
    when (trend) {
        1 -> {
            arrow = "▲"
            labelRes = R.string.altitude_trend_up
            tint = BrandCyan
        }
        -1 -> {
            arrow = "▼"
            labelRes = R.string.altitude_trend_down
            tint = PauseOrange
        }
        else -> {
            arrow = "—"
            labelRes = R.string.altitude_trend_flat
            tint = DataLabel
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(labelRes),
            fontSize = 9.sp,
            color = tint,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
