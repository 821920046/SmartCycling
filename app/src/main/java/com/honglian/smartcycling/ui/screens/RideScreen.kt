package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviMapView
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.PanelBg

/**
 * 骑行中数据界面:左导航 / 右仪表盘(横屏)。
 * - 有目的地时左侧为完整 turn-by-turn 导航(转向箭头+语音);否则为跟随地图。
 * - 左上角提供语音开关。
 */
@Composable
fun RideScreen(
    state: RideState,
    routePoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    voiceEnabled: Boolean = true,
    onToggleVoice: () -> Unit = {},
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        // 左:导航
        Box(Modifier.weight(1.3f).fillMaxHeight()) {
            if (destination != null) {
                NaviMapView(
                    destination = destination,
                    voiceEnabled = voiceEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
                Button(
                    onClick = onToggleVoice,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(if (voiceEnabled) "🔊 语音开" else "🔇 语音关", fontSize = 13.sp)
                }
            } else {
                NavigationMapView(
                    modifier = Modifier.fillMaxSize(),
                    routePoints = routePoints,
                    follow = true,
                )
            }
        }
        // 右:数据仪表盘
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(PanelBg)
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            SpeedRing(speedKmh = state.speedKmh, diameterDp = 150)
            Text(
                if (state.speedSource == SpeedSource.SENSOR_WHEEL) "速度来源 · 传感器" else "速度来源 · GPS",
                fontSize = 12.sp,
                color = DataLabel,
            )
            Card(Modifier.fillMaxWidth()) {
                DataGrid(state, Modifier.padding(vertical = 4.dp))
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Text("结束骑行")
            }
        }
    }
}
