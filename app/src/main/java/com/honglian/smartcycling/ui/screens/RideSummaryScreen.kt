package com.honglian.smartcycling.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ui.theme.*

/**
 * 骑行结束成绩总结页。展示本次骑行关键指标,并支持一键分享。
 * state 为 null(异常进入)时直接展示完成按钮回到地图。
 */
@Composable
fun RideSummaryScreen(
    state: RideState?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelBgTop, PanelBgBottom)))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("🏁 骑行完成", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SpeedText)

            if (state == null) {
                Text("本次骑行时长过短,未生成成绩。", color = DataLabel, fontSize = 14.sp)
            } else {
                // 主指标:里程
                Text("%.2f".format(state.distanceKm), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = BrandCyan, fontFamily = FontFamily.Monospace)
                Text("公里", fontSize = 14.sp, color = DataLabel)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatRow("总时长", state.durationText, "平均速度", "%.1f km/h".format(state.avgSpeedKmh))
                        HorizontalDivider(color = DividerNavy)
                        StatRow("最高速度", "%.1f km/h".format(state.maxSpeedKmh), "平均踏频", "${state.avgCadenceRpm.toInt()} rpm")
                        HorizontalDivider(color = DividerNavy)
                        StatRow("消耗热量", "%.0f kcal".format(state.calories), "累计爬升", "%.0f m".format(state.elevationGainM))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state != null) {
                Button(
                    onClick = {
                        val shareText = buildString {
                            append("我用智能骑行完成了一次骑行 🚴\n")
                            append("里程 %.2f km".format(state.distanceKm))
                            append(" · 时长 ${state.durationText}")
                            append(" · 均速 %.1f km/h".format(state.avgSpeedKmh))
                            append(" · 最高 %.1f km/h".format(state.maxSpeedKmh))
                            append(" · 热量 %.0f kcal".format(state.calories))
                            append(" · 爬升 %.0f m".format(state.elevationGainM))
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "分享骑行成绩")) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color(0xFF04121A)),
                ) {
                    Text("📤 分享成绩", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("完成 · 返回地图", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SpeedText)
            }
        }
    }
}

@Composable
private fun StatRow(label1: String, value1: String, label2: String, value2: String) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label1, color = DataLabel, fontSize = 12.sp)
            Text(value1, color = DataValue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.weight(1f)) {
            Text(label2, color = DataLabel, fontSize = 12.sp)
            Text(value2, color = DataValue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * 首次使用引导弹窗。简要介绍核心使用流程,确认后不再提示。
 */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = SpeedText,
        textContentColor = DataLabel,
        title = { Text("欢迎使用智能骑行", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1． 开机自动配对迈金 S314 速度/踏频传感器。")
                Text("2． 在地图搜索目的地,点“开始骑行”进入 turn-by-turn 导航。")
                Text("3． 骑行页左侧导航、右侧仪表盘可拖动缩放;可锁屏防误触。")
                Text("4． 结束后自动生成成绩总结并可一键分享。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("开始骑行", color = BrandCyan, fontWeight = FontWeight.Bold)
            }
        },
    )
}
