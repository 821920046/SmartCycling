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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                value = state.durationText,
                label = "骑行时长",
                alpha = dim,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Place,
                value = "%.2f km".format(state.distanceKm),
                label = "骑行路程",
                alpha = dim,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.TrendingUp,
                value = "%.1f km/h".format(state.avgSpeedKmh),
                label = "平均速度",
                alpha = dim,
            )
            if (cadenceMode) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Autorenew,
                    value = "${state.avgCadenceRpm.roundToInt()} rpm",
                    label = "平均踏频",
                    alpha = dim,
                )
            } else {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Speed,
                    value = "%.1f km/h".format(state.maxSpeedKmh),
                    label = "最高速度",
                    alpha = dim,
                )
            }
        }
        HorizontalDivider(color = DividerNavy)
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), "🔥 %.0f kcal".format(state.calories), "消耗热量")
            VerticalDivider(color = DividerNavy)
            DataCell(Modifier.weight(1f), "⛰ %.0f m".format(state.elevationGainM), "累计爬升")
        }
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
