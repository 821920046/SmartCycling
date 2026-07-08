package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.DividerNavy

/** 骑行中数据界面:左导航 / 右数据(横屏),还原设计图。 */
@Composable
fun RideScreen(
    state: RideState,
    routePoints: List<LatLng> = emptyList(),
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavigationMapView(modifier = Modifier.fillMaxSize(), routePoints = routePoints)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(DividerNavy))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SpeedRing(speedKmh = state.speedKmh)
            Text(
                if (state.speedSource == SpeedSource.SENSOR_WHEEL) "速度来源:传感器" else "速度来源:GPS",
                fontSize = 12.sp,
                color = DataLabel,
            )
            DataGrid(state)
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("结束骑行")
            }
        }
    }
}
