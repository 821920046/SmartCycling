package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ui.components.NavigationMapView

/**
 * 地图主界面:输入目的地 → 点“骑行”开始并规划路线,自动跳转骑行数据界面。
 */
@Composable
fun MapScreen(
    routePoints: List<LatLng>,
    status: String,
    onRide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf("") }
    Box(modifier.fillMaxSize()) {
        NavigationMapView(routePoints = routePoints)
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            if (status.isNotBlank()) {
                Text(status, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("输入目的地") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onRide(destination) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("骑行") }
        }
    }
}
