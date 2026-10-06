package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.PoiItem
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.core.WheelPreset
import com.honglian.smartcycling.offline.OfflineLayerSpec
import com.honglian.smartcycling.offline.OfflineMapView
import com.honglian.smartcycling.offline.toWgs84
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space

/**
 * 地图主界面。
 *
 * 结构(自下而上):
 *  - 底图:按 [mapSource] 在"高德在线引擎"与"离线瓦片引擎"之间切换;
 *  - 顶部悬浮搜索卡:目的地输入 + POI 联想 + 地图引擎/车轮/图层快捷设置;
 *  - 右侧竖排快捷入口:历史、设置、离线地图管理;
 *  - 底部:预计里程 + 开始骑行。
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
    onNavigateToOfflineMaps: () -> Unit,
    destination: LatLng? = null,
    mapType: Int = 3,
    suggestions: List<PoiItem> = emptyList(),
    onSuggestionSelected: (PoiItem) -> Unit = {},
    onKeywordChanged: (String) -> Unit = {},
    mapSource: MapSource = MapSource.ONLINE,
    offlineSpec: OfflineLayerSpec? = null,
    planning: Boolean = false,
    /** 当前位置(WGS-84),仅用于离线底图的"我的位置"标记;在线底图由高德自带定位负责。 */
    currentLocation: LatLng? = null,
    onSwitchSource: (MapSource) -> Unit = {},
    onSelectMapType: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    var query by remember { mutableStateOf("") }
    var showWheelDialog by remember { mutableStateOf(false) }
    var showLayerDialog by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val distanceKm = remember(routePoints) { routeDistanceKm(routePoints) }

    Box(modifier.fillMaxSize().background(palette.background)) {
        // 1) 底图
        if (mapSource == MapSource.OFFLINE && offlineSpec != null) {
            // 高德规划出的路线/目的地是 GCJ-02,而离线底图按 WGS-84 网格渲染 →
            // 必须在此纠偏后再绘制,否则整条路线会偏移 300~600 米。
            val wgsRoute = remember(routePoints) { routePoints.toWgs84() }
            val wgsDest = remember(destination) { destination?.toWgs84() }
            OfflineMapView(
                spec = offlineSpec,
                routePoints = wgsRoute,
                destination = wgsDest,
                currentLocation = currentLocation,
                follow = false,
                routeColor = palette.primary.toArgb(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationMapView(
                modifier = Modifier.fillMaxSize(),
                routePoints = routePoints,
                destination = destination,
                mapType = mapType,
            )
        }

        // 2) 顶部搜索卡
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(Space.md),
            shape = RoundedCornerShape(Radius.lg),
            color = palette.floatingSurface,
            border = BorderStroke(1.dp, palette.outline),
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onKeywordChanged(it)
                        },
                        placeholder = { Text("搜索目的地", style = MaterialTheme.typography.body) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.outline,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Button(
                        onClick = { focus.clearFocus(); onSearch(query) },
                        enabled = query.isNotBlank() && !planning,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.height(56.dp),
                    ) {
                        if (planning) {
                            CircularProgressIndicator(
                                color = palette.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("规划", style = MaterialTheme.typography.subtitle)
                        }
                    }
                }

                // POI 联想
                if (suggestions.isNotEmpty() && routePoints.isEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LazyColumn(Modifier.heightIn(max = 210.dp)) {
                            items(suggestions.take(8)) { poi ->
                                SuggestionRow(poi) {
                                    focus.clearFocus()
                                    onSuggestionSelected(poi)
                                    query = "" // 清空输入框,避免空回填触发竞态联想
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.sm))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    EngineChip(
                        label = "在线",
                        icon = Icons.Outlined.Public,
                        selected = mapSource == MapSource.ONLINE,
                        onClick = { onSwitchSource(MapSource.ONLINE) },
                    )
                    EngineChip(
                        label = "离线",
                        icon = Icons.Outlined.CloudOff,
                        selected = mapSource == MapSource.OFFLINE,
                        enabled = offlineSpec != null,
                        onClick = {
                            if (offlineSpec != null) onSwitchSource(MapSource.OFFLINE)
                            else onNavigateToOfflineMaps()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    IconChip(Icons.Outlined.DirectionsBike) { showWheelDialog = true }
                    IconChip(Icons.Outlined.Tune) { showLayerDialog = true }
                }

                if (status.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.caption,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 3) 右侧快捷入口
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            FloatingAction(Icons.Outlined.History, "骑行历史", onNavigateToHistory)
            FloatingAction(Icons.Outlined.Map, "离线地图", onNavigateToOfflineMaps)
            FloatingAction(Icons.Outlined.Settings, "设置", onNavigateToSettings)
        }

        // 4) 底部开始骑行
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (routePoints.size >= 2) {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = palette.floatingSurface,
                    border = BorderStroke(1.dp, palette.outline),
                    modifier = Modifier.padding(bottom = Space.sm),
                ) {
                    Text(
                        "预计骑行 %.1f km".format(distanceKm),
                        style = MaterialTheme.typography.label,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                    )
                }
            }
            Button(
                onClick = onStartRide,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(Icons.Outlined.DirectionsBike, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Text("开始骑行", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
    if (showLayerDialog) {
        LayerDialog(
            current = mapType,
            onSelect = { onSelectMapType(it); showLayerDialog = false },
            onDismiss = { showLayerDialog = false },
        )
    }
}

@Composable
private fun SuggestionRow(poi: PoiItem, onClick: () -> Unit) {
    val palette = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Place,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = poi.title?.takeIf { it.isNotBlank() } ?: "未知地点",
                style = MaterialTheme.typography.body,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val address = poi.snippet?.takeIf { it.isNotBlank() } ?: poi.cityName ?: ""
            if (address.isNotBlank()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.caption,
                    color = palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EngineChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) palette.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) palette.primary else palette.outline),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.primary else palette.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.label,
                color = when {
                    selected -> palette.onPrimaryContainer
                    enabled -> palette.textSecondary
                    else -> palette.textTertiary
                },
            )
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, onClick: () -> Unit) {
    val palette = AppTheme.palette
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, palette.outline),
        modifier = Modifier.size(34.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun FloatingAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = AppTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = palette.floatingSurface,
        border = BorderStroke(1.dp, palette.outline),
        shadowElevation = 4.dp,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = palette.textPrimary, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun WheelDialog(
    current: WheelPreset,
    onSelect: (WheelPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = palette.primary) } },
        title = { Text("车轮周长标定", style = MaterialTheme.typography.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "用于无 GPS 时按轮转圈数回退计算速度与里程。",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(bottom = Space.sm),
                )
                WheelPreset.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == current, onClick = { onSelect(p) })
                        Spacer(Modifier.width(Space.sm))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.body, color = palette.textPrimary)
                            Text(
                                "周长 ${p.circumferenceMm} mm",
                                style = MaterialTheme.typography.caption,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 图层选择:仅在线引擎生效(离线包自带样式)。 */
@Composable
private fun LayerDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val palette = AppTheme.palette
    val options = listOf(1 to "标准", 2 to "卫星", 3 to "夜间")
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = palette.primary) } },
        title = { Text("地图图层", style = MaterialTheme.typography.title) },
        text = {
            Column {
                options.forEach { (type, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == current, onClick = { onSelect(type) })
                        Spacer(Modifier.width(Space.sm))
                        Text(label, style = MaterialTheme.typography.body, color = palette.textPrimary)
                    }
                }
                Text(
                    "图层切换在「在线」引擎下生效;离线包使用其自带样式。",
                    style = MaterialTheme.typography.caption,
                    color = palette.textTertiary,
                )
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
