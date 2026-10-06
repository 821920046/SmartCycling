package com.honglian.smartcycling.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.R
import com.honglian.smartcycling.core.Units
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.DividerNavy
import kotlin.math.roundToInt

/**
 * 骑行数据网格(2×2)。
 * 每格为"图标 + 数值 + 标签",数值统一使用表格数字保证跳动时不抖动。
 */
@Composable
fun DataGrid(state: RideState, modifier: Modifier = Modifier) {
    val cadenceMode = state.sensorMode == SensorMode.CADENCE
    val dim = if (state.isPaused) 0.45f else 1f
    val units = AppTheme.units

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                value = state.durationText,
                label = stringResource(R.string.ride_stat_duration),
                alpha = dim,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Place,
                value = Units.distanceText(state.distanceKm, units),
                label = stringResource(R.string.ride_stat_distance),
                alpha = dim,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.TrendingUp,
                value = Units.speedText(state.avgSpeedKmh, units),
                label = stringResource(R.string.summary_avg_speed),
                alpha = dim,
            )
            if (cadenceMode) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Autorenew,
                    value = "${state.avgCadenceRpm.roundToInt()} rpm",
                    label = stringResource(R.string.ride_stat_avg_cadence),
                    alpha = dim,
                )
            } else {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Speed,
                    value = Units.speedText(state.maxSpeedKmh, units),
                    label = stringResource(R.string.summary_max_speed),
                    alpha = dim,
                )
            }
        }
        HorizontalDivider(color = DividerNavy)
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), "🔥 %.0f kcal".format(state.calories), stringResource(R.string.summary_calories))
            VerticalDivider(color = DividerNavy)
            DataCell(Modifier.weight(1f), "⛰ " + Units.elevationText(state.elevationGainM, units), stringResource(R.string.summary_elevation_gain))
        }
        // 心率带为可选外设:未连接时不占用版面,连接后自动展开一行。
        if (state.hasHeartRate) {
            HorizontalDivider(color = DividerNavy)
            Row(Modifier.fillMaxWidth()) {
                DataCell(Modifier.weight(1f), "❤ %d bpm".format(state.heartRateBpm), stringResource(R.string.ride_stat_live_hr))
                VerticalDivider(color = DividerNavy)
                DataCell(Modifier.weight(1f), "❤ %.0f bpm".format(state.avgHeartRateBpm), stringResource(R.string.summary_avg_hr))
            }
        }
    }
}

/**
 * 底部两栏小指标:大数值 + 小标签,水平居中。
 * (原先该函数在合并开发线时丢失,导致 DataGrid 无法编译,此处补回。)
 */
@Composable
private fun DataCell(modifier: Modifier, text: String, label: String) {
    val palette = AppTheme.palette
    Column(
        modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = palette.hudValue,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = palette.hudLabel,
        )
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val palette = AppTheme.palette
    Column(modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.hudLabel.copy(alpha = 0.9f * alpha),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = palette.hudLabel.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = palette.hudValue.copy(alpha = alpha),
        )
    }
}
