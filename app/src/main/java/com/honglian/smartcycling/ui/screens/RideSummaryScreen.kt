package com.honglian.smartcycling.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.R
import com.honglian.smartcycling.core.UnitSystem
import com.honglian.smartcycling.core.Units
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.ride.LapSplit
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ui.theme.*

/**
 * 骑行结束成绩总结页。展示本次骑行关键指标,并支持一键分享成绩 / 导出 GPX 轨迹。
 * state 为 null(异常进入)时直接展示完成按钮回到地图。
 *
 * 单位制:所有数值经 [Units] 换算后再渲染 —— 页面本身不关心公制还是英制,
 * 只从 [AppTheme.units] 取当前偏好。
 */
@Composable
fun RideSummaryScreen(
    state: RideState?,
    onDone: () -> Unit,
    onExportGpx: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val units = AppTheme.units

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
            Text(stringResource(R.string.summary_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SpeedText)

            if (state == null) {
                Text(stringResource(R.string.summary_too_short), color = DataLabel, fontSize = 14.sp)
            } else {
                // 主指标:里程(数值与单位分开渲染,便于用大字号突出数值)
                Text(
                    "%.2f".format(Units.distance(state.distanceKm, units)),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandCyan,
                    fontFamily = FontFamily.Monospace,
                )
                Text(Units.distanceUnit(units), fontSize = 14.sp, color = DataLabel)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatRow(
                            stringResource(R.string.summary_total_duration), state.durationText,
                            stringResource(R.string.summary_avg_speed), Units.speedText(state.avgSpeedKmh, units),
                        )
                        HorizontalDivider(color = DividerNavy)
                        StatRow(
                            stringResource(R.string.summary_max_speed), Units.speedText(state.maxSpeedKmh, units),
                            stringResource(R.string.summary_avg_cadence), "${state.avgCadenceRpm.toInt()} rpm",
                        )
                        HorizontalDivider(color = DividerNavy)
                        StatRow(
                            stringResource(R.string.summary_calories), "%.0f kcal".format(state.calories),
                            stringResource(R.string.summary_elevation_gain), Units.elevationText(state.elevationGainM, units),
                        )
                        // 心率带为可选外设:本次有心率数据才多展示一行。
                        if (state.avgHeartRateBpm > 0.0 || state.maxHeartRateBpm > 0) {
                            HorizontalDivider(color = DividerNavy)
                            StatRow(
                                stringResource(R.string.summary_avg_hr),
                                "%.0f bpm".format(state.avgHeartRateBpm),
                                stringResource(R.string.summary_max_hr),
                                "%d bpm".format(state.maxHeartRateBpm),
                            )
                        }
                    }
                }

                // 分圈明细:仅在本次开启了自动分圈、且至少产生过一圈时展示。
                if (state.laps.isNotEmpty()) {
                    LapTable(laps = state.laps, units = units)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state != null) {
                // 分享文案在组合期一次性取好:onClick 是普通 lambda,内部不能调用 @Composable 的 stringResource。
                val shareText = stringResource(
                    R.string.summary_share_text,
                    Units.distanceText(state.distanceKm, units),
                    state.durationText,
                    Units.speedText(state.avgSpeedKmh, units),
                    Units.speedText(state.maxSpeedKmh, units),
                    state.calories,
                    Units.elevationText(state.elevationGainM, units),
                )
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, context.getString(R.string.summary_share_chooser)),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color(0xFF04121A)),
                ) {
                    Text(stringResource(R.string.summary_share), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onExportGpx,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.summary_export_gpx), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandCyan)
                }
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.summary_done), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SpeedText)
            }
        }
    }
}

/** 分圈表格:表头 + 每圈一行(圈号 / 距离 / 用时 / 均速)。 */
@Composable
private fun LapTable(laps: List<LapSplit>, units: UnitSystem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBg),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = BrandCyan,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.laps_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpeedText,
                )
            }
            Spacer(Modifier.height(10.dp))
            laps.forEachIndexed { i, lap ->
                if (i > 0) HorizontalDivider(color = DividerNavy)
                LapRow(lap = lap, units = units)
            }
        }
    }
}

@Composable
private fun LapRow(lap: LapSplit, units: UnitSystem) {
    val label = stringResource(R.string.lap_number, lap.index) +
        if (lap.isComplete) "" else " · " + stringResource(R.string.lap_in_progress)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1.25f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (lap.isComplete) DataValue else BrandGreen,
            maxLines = 1,
        )
        Text(
            Units.distanceText(lap.distanceKm, units),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = DataValue,
            maxLines = 1,
        )
        Text(
            lapDurationText(lap.durationSec),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = DataLabel,
            maxLines = 1,
        )
        Text(
            Units.speedText(lap.avgSpeedKmh, units),
            modifier = Modifier.weight(1.2f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = BrandCyan,
            maxLines = 1,
        )
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

/** 分圈用时:不足 1 小时用 mm:ss,超过用 h:mm:ss。 */
private fun lapDurationText(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
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
        title = { Text(stringResource(R.string.onboarding_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.onboarding_step1))
                Text(stringResource(R.string.onboarding_step2))
                Text(stringResource(R.string.onboarding_step3))
                Text(stringResource(R.string.onboarding_step4))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_start), color = BrandCyan, fontWeight = FontWeight.Bold)
            }
        },
    )
}

/**
 * 断点续记恢复弹窗:上次骑行未正常收尾(闪退 / 被系统回收)时,启动后提示用户如何处理。
 *
 * 三个出口对应三种真实诉求:
 *  - 继续骑行:人还在车上,只是想接着记 —— 复用同一条记录,不产生第二条。
 *  - 保存并结束:已经骑完但没来得及点结束 —— 直接收尾成一条完整记录。
 *  - 丢弃:那次是误触发 / 数据无意义 —— 连同轨迹点一起删掉。
 * 弹窗不可点击外部关闭:必须显式选择,避免"随手关掉"后残留记录永远悬在库里。
 */
@Composable
fun RideRecoveryDialog(
    ride: RideEntity,
    onResume: () -> Unit,
    onFinalize: () -> Unit,
    onDiscard: () -> Unit,
) {
    val units = AppTheme.units
    AlertDialog(
        onDismissRequest = { /* 必须显式选择 */ },
        containerColor = CardBg,
        titleContentColor = SpeedText,
        textContentColor = DataLabel,
        title = { Text(stringResource(R.string.recover_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.recover_text), fontSize = 14.sp)
                Text(
                    stringResource(
                        R.string.recover_detail,
                        Units.distanceText(ride.distanceKm, units),
                        durationText(ride.durationSec),
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCyan,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.recover_resume), color = BrandCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onFinalize) {
                Text(stringResource(R.string.recover_finalize), color = DataValue)
            }
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.recover_discard), color = StopRed)
            }
        },
    )
}

private fun durationText(sec: Long): String =
    "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
