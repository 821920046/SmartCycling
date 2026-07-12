package com.honglian.smartcycling.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.DataValue
import com.honglian.smartcycling.ui.theme.DividerNavy
import kotlin.math.roundToInt

/** 2x2 数据网格:骑行时长 / 骑行路程 / 平均速度 / 踏频频率。 */
@Composable
fun DataGrid(state: RideState, modifier: Modifier = Modifier) {
    val alpha = if (state.isPaused) 0.6f else 1.0f
    Column(modifier.fillMaxWidth().alpha(alpha)) {
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), "⏱ " + state.durationText, "骑行时长")
            VerticalDivider(color = DividerNavy)
            DataCell(Modifier.weight(1f), "🏁 %.2f km".format(state.distanceKm), "骑行路程")
        }
        HorizontalDivider(color = DividerNavy)
        val cadenceMode = state.sensorMode == SensorMode.CADENCE
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), "📈 %.1f km/h".format(state.avgSpeedKmh), "平均速度")
            VerticalDivider(color = DividerNavy)
            if (cadenceMode) {
                DataCell(Modifier.weight(1f), "🔄 ${state.avgCadenceRpm.roundToInt()} rpm", "平均踏频")
            } else {
                DataCell(Modifier.weight(1f), "🔄 0 rpm", "平均踏频")
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
private fun DataCell(modifier: Modifier, value: String, label: String) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DataValue,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = DataLabel,
            fontWeight = FontWeight.Medium
        )
    }
}

