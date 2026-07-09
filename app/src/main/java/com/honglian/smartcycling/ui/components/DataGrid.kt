package com.honglian.smartcycling.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** 2x2 数据网格:骑行时长 / 骑行路程 / 平均速度 / 踏频频率。紧凑排版,保证仪表盘整屏可见。 */
@Composable
fun DataGrid(state: RideState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), state.durationText, "骑行时长")
            DataCell(Modifier.weight(1f), "%.2f km".format(state.distanceKm), "骑行路程")
        }
        HorizontalDivider(color = DividerNavy)
        val cadenceMode = state.sensorMode == SensorMode.CADENCE
        Row(Modifier.fillMaxWidth()) {
            DataCell(Modifier.weight(1f), "%.1f km/h".format(state.avgSpeedKmh), "平均速度")
            if (cadenceMode) {
                // 踏频模式:右下角显示平均踏频
                DataCell(Modifier.weight(1f), "${state.avgCadenceRpm.roundToInt()} rpm", "平均踏频")
            } else {
                // 速度模式:无踏频传感器,频率固定为 0
                DataCell(Modifier.weight(1f), "0 rpm", "踏频频率")
            }
        }
    }
}

@Composable
private fun DataCell(modifier: Modifier, value: String, label: String) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DataValue, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 11.sp, color = DataLabel)
    }
}
