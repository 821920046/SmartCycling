package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.PoiItem
import com.honglian.smartcycling.core.WheelPreset
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.theme.*

/**
 * 极致高颜值科技 HUD 地图主界面(两步式):
 * 1) 顶部搜索卡片支持输入目的地与规划路线。
 * 2) 集成了“设置中枢”与“历史列表”的科幻毛玻璃悬浮键。
 */
@Composable
fun MapScreen(
    routePoints: List<LatLng>,
    status: String,
    currentWheel: WheelPreset,
    onSearch: (String) -> Unit,
    onStartRide: () -> Unit,
    onSelectWheel: (WheelPreset) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    destination: LatLng? = null,
    mapType: Int = 3,
    suggestions: List<PoiItem> = emptyList(),
    onSuggestionSelected: (PoiItem) -> Unit = {},
    onKeywordChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var showWheelDialog by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val distanceKm = remember(routePoints) { routeDistanceKm(routePoints) }

    Box(modifier.fillMaxSize()) {
        NavigationMapView(
            modifier = Modifier.fillMaxSize(),
            routePoints = routePoints,
            destination = destination,
            mapType = mapType
        )

        // 顶部悬浮控制卡片 (避开状态栏)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBg)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onKeywordChanged(it)
                        },
                        label = { Text("输入目的地") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandCyan,
                            unfocusedBorderColor = DividerNavy,
                            focusedLabelColor = BrandCyan,
                            unfocusedLabelColor = DataLabel,
                            focusedTextColor = SpeedText,
                            unfocusedTextColor = SpeedText
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { focus.clearFocus(); onSearch(query) },
                        enabled = query.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color(0xFF060913)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("规划", fontWeight = FontWeight.Bold)
                    }
                }

                // POI 智能联想下拉(仅在无已规划路线时显示,避免遮挡地图预览)
                if (suggestions.isNotEmpty() && routePoints.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassBg.copy(alpha = 0.95f)),
                    ) {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(4.dp)
                        ) {
                            suggestions.take(8).forEach { poi ->
                                val title = poi.title?.takeIf { it.isNotBlank() } ?: poi.name ?: "未知地点"
                                val address = poi.snippet?.takeIf { it.isNotBlank() }
                                    ?: poi.cityName ?: ""
                                Surface(
                                    onClick = {
                                        focus.clearFocus()
                                        onSuggestionSelected(poi)
                                        query = "" // 清空输入框,避免空回填触发竞态联想
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("📍", fontSize = 14.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = SpeedText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (address.isNotBlank()) {
                                                Text(
                                                    address,
                                                    fontSize = 12.sp,
                                                    color = DataLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showWheelDialog = true }) {
                        Text("⚙ 车轮:${currentWheel.label}", fontSize = 13.sp, color = BrandCyan, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (status.isNotBlank()) {
                            Text(status, fontSize = 12.sp, color = DataLabel, fontWeight = FontWeight.Medium)
                        }
                        
                        // 历史记录悬浮按键
                        IconButton(
                            onClick = onNavigateToHistory,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = DividerNavy, contentColor = BrandCyan)
                        ) {
                            Icon(Icons.Default.History, contentDescription = "骑行历史")
                        }

                        // 设置悬浮按键
                        IconButton(
                            onClick = onNavigateToSettings,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = DividerNavy, contentColor = BrandCyan)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "系统设置")
                        }
                    }
                }
            }
        }

        // 底部开始骑行控制面板 (避开导航栏)
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (routePoints.size >= 2) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GlassBg),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "预计骑行 %.1f km".format(distanceKm),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Button(
                onClick = onStartRide,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF060913)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { 
                Text("开始骑行", fontSize = 18.sp, fontWeight = FontWeight.Black) 
            }
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
