package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.core.WheelPreset
import com.honglian.smartcycling.ui.components.NavigationMapView

/**
 * 地图主界面(两步式):
 * 1) 顶部搜索栏输入目的地 → 地图上预览路线 + 预计里程。
 * 2) 底部“开始骑行”→ 进入骑行数据界面。
 * 另提供车轮尺寸设置(影响实时速度/里程计算)。
 */
@Composable
fun MapScreen(
    routePoints: List<LatLng>,
    status: String,
    currentWheel: WheelPreset,
    onSearch: (String) -> Unit,
    onStartRide: () -> Unit,
    onSelectWheel: (WheelPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf("") }
    var showWheelDialog by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val distanceKm = remember(routePoints) { routeDistanceKm(routePoints) }

    Box(modifier.fillMaxSize()) {
        NavigationMapView(modifier = Modifier.fillMaxSize(), routePoints = routePoints)

        // 顶部搜索卡片
        Card(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("输入目的地") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { focus.clearFocus(); onSearch(destination) },
                        enabled = destination.isNotBlank(),
                    ) { Text("搜索") }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showWheelDialog = true }) {
                        Text("⚙ 车轮:${currentWheel.label}", fontSize = 13.sp)
                    }
                    if (status.isNotBlank()) {
                        Text(status, fontSize = 13.sp)
                    }
                }
            }
        }

        // 底部开始骑行
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (routePoints.size >= 2) {
                Text(
                    "预计骑行 %.1f km".format(distanceKm),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = onStartRide,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("开始骑行", fontSize = 18.sp) }
        }
    }

    if (showWheelDialog) {
        WheelDialog(
            current = currentWheel,
            onSelect = { onSelectWheel(it); showWheelDialog = false },
            onDismiss = { showWheelDialog = false },
        )
    }
}

@Composable
private fun WheelDialog(
    current: WheelPreset,
    onSelect: (WheelPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("选择车轮尺寸") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "用于根据轮转圈数计算实时速度与里程,请按你的轮胎选择:",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                WheelPreset.values().forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == current, onClick = { onSelect(p) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(p.label, fontSize = 15.sp)
                            Text("周长 ${p.circumferenceMm} mm", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
    )
}

/** 累加折线段长得到路线总里程(km)。 */
private fun routeDistanceKm(points: List<LatLng>): Double {
    if (points.size < 2) return 0.0
    var meters = 0f
    for (i in 1 until points.size) {
        meters += AMapUtils.calculateLineDistance(points[i - 1], points[i])
    }
    return meters / 1000.0
}
