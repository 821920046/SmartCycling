package com.honglian.smartcycling.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviBannerInfo
import com.honglian.smartcycling.ui.components.NaviVoiceGuide
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.CardBg
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.GlassBorder
import com.honglian.smartcycling.ui.theme.PauseOrange
import com.honglian.smartcycling.ui.theme.SpeedText
import com.honglian.smartcycling.ui.theme.StopRed
import kotlin.math.roundToInt

/**
 * 骑行中数据界面（横竖屏自适应）：
 * - 横屏：全屏地图 + 可自由拖动/缩放的右侧悬浮仪表盘（保留原设计）。
 * - 竖屏：上方地图 + 下方停靠式仪表盘（大速度环 + 时长/里程 + 2×3 数据网格 + 控制按钮）。
 * - 控制按钮（暂停/恢复、结束骑行、锁屏）在两种方向下均常驻可见。
 */
@Composable
fun RideScreen(
    state: RideState,
    routePoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    startPoint: LatLng? = null,
    currentLatLng: LatLng? = null,
    traveledPoints: List<LatLng> = emptyList(),
    voiceEnabled: Boolean = true,
    onToggleVoice: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onStop: () -> Unit,
    mapType: Int = 3,
    highContrast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showStopConfirm by remember { mutableStateOf(false) }
    // turn-by-turn 转向卡数据（来自 headless 导航引擎）
    var naviInfo by remember { mutableStateOf<NaviBannerInfo?>(null) }
    // 可见路线：初始用规划路线，导航引擎算路/偏航重算后用引擎真实路线覆盖
    var liveRoute by remember(routePoints) { mutableStateOf(routePoints) }
    // 锁屏防误触：锁定后拦截地图触摸，暂停/结束按钮失效，长按锁按钮解锁
    var locked by rememberSaveable { mutableStateOf(false) }
    // 横屏悬浮仪表盘的位置与缩放（仅横屏使用，跨重建保持）
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var scale by rememberSaveable { mutableStateOf(1f) }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        // ===== 竖屏：上地图 + 下停靠仪表盘 =====
        Column(modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NavigationMapView(
                    modifier = Modifier.fillMaxSize(),
                    routePoints = liveRoute,
                    traveledPoints = traveledPoints,
                    destination = destination,
                    follow = true,
                    mapType = 3,
                )
                if (destination != null) {
                    NaviVoiceGuide(
                        destination = destination,
                        startPoint = startPoint,
                        currentLatLng = currentLatLng,
                        routePoints = liveRoute,
                        enabled = voiceEnabled,
                        onNaviInfo = { naviInfo = it },
                        onRoutePath = { path -> if (path.isNotEmpty()) liveRoute = path },
                    )
                    VoiceToggleButton(
                        voiceEnabled = voiceEnabled,
                        onToggleVoice = onToggleVoice,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .safeDrawingPadding()
                            .padding(12.dp),
                    )
                    naviInfo?.let { info ->
                        TurnBanner(
                            info = info,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .safeDrawingPadding()
                                .padding(top = 10.dp, start = 8.dp, end = 8.dp)
                                .widthIn(max = 460.dp),
                        )
                    }
                }
                // 锁定时遮罩地图（仅拦截地图触摸，下方仪表盘/按钮不受影响）
                if (locked) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { } },
                    )
                }
            }
            PortraitDashboard(
                state = state,
                locked = locked,
                highContrast = highContrast,
                onTogglePause = { if (!locked) onTogglePause() },
                onStopRequest = { if (!locked) showStopConfirm = true },
                onLock = { locked = true },
                onUnlock = { locked = false },
            )
        }
    } else {
        // ===== 横屏：全屏地图 + 右侧可拖动缩放悬浮仪表盘（原设计） =====
        val cadenceMode = state.sensorMode == SensorMode.CADENCE
        Box(modifier.fillMaxSize().background(Color.Black)) {
            NavigationMapView(
                modifier = Modifier.fillMaxSize(),
                routePoints = liveRoute,
                traveledPoints = traveledPoints,
                destination = destination,
                follow = true,
                mapType = 3,
            )
            if (destination != null) {
                NaviVoiceGuide(
                    destination = destination,
                    startPoint = startPoint,
                    currentLatLng = currentLatLng,
                    routePoints = liveRoute,
                    enabled = voiceEnabled,
                    onNaviInfo = { naviInfo = it },
                    onRoutePath = { path -> if (path.isNotEmpty()) liveRoute = path },
                )
                VoiceToggleButton(
                    voiceEnabled = voiceEnabled,
                    onToggleVoice = onToggleVoice,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(16.dp)
                        .zIndex(2f),
                )
            }
            naviInfo?.let { info ->
                if (destination != null) {
                    TurnBanner(
                        info = info,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = 12.dp)
                            .widthIn(max = 460.dp)
                            .zIndex(4f),
                    )
                }
            }

            // 可拖动 + 可缩放的悬浮数据仪表盘（Glassmorphism HUD）
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .safeDrawingPadding()
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .padding(12.dp)
                    .width(300.dp)
                    .background(if (highContrast) Color(0xF3020A12) else Color(0x8804121A), RoundedCornerShape(24.dp))
                    .border(1.dp, BrandCyan.copy(alpha = if (highContrast) 0.9f else 0.5f), RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            offsetX += pan.x * scale
                            offsetY += pan.y * scale
                            scale = (scale * zoom).coerceIn(0.6f, 2.6f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            offsetX = 0f
                            offsetY = 0f
                            scale = 1f
                        })
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(3f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "✥ 拖动 · 双指缩放 · 双击复位",
                    fontSize = 10.sp,
                    color = DataLabel,
                    fontWeight = FontWeight.Medium,
                )
                SpeedRing(
                    value = if (cadenceMode) state.cadenceRpm else state.speedKmh,
                    unit = if (cadenceMode) "rpm" else "km/h",
                    maxValue = if (cadenceMode) 120.0 else 60.0,
                    diameterDp = 150,
                )
                Text(
                    speedSourceLabel(state, cadenceMode),
                    fontSize = 11.sp,
                    color = if (state.isPaused) PauseOrange else DataLabel,
                    fontWeight = FontWeight.Bold,
                )
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (highContrast) Color(0x59FFFFFF) else Color(0x33FFFFFF)),
                ) {
                    DataGrid(state, Modifier.padding(vertical = 4.dp))
                }
            }

            // 常驻控制按钮（底部右侧）
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .zIndex(6f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    text = if (state.isPaused) "▶ 恢复" else "⏸ 暂停",
                    bg = if (state.isPaused) BrandCyan else PauseOrange,
                    fg = Color(0xFF060913),
                    hPadding = 18.dp,
                    onClick = { if (!locked) onTogglePause() },
                )
                ControlButton(
                    text = "■ 结束骑行",
                    bg = StopRed,
                    fg = Color.White,
                    hPadding = 24.dp,
                    onClick = { if (!locked) showStopConfirm = true },
                )
            }

            // 锁屏防误触按钮（左下角）：点按锁定，长按解锁
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .zIndex(7f)
                    .background(if (locked) StopRed else Color(0xAA0B1622), RoundedCornerShape(14.dp))
                    .border(1.dp, BrandCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (!locked) locked = true },
                            onLongPress = { if (locked) locked = false },
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    if (locked) "🔒 已锁定 · 长按解锁" else "🔓 锁屏",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(5f)
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
    }

    // 误触确认对话框（横竖屏共用）
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = CardBg,
            titleContentColor = SpeedText,
            textContentColor = DataLabel,
            title = { Text("确认结束本次骑行？", fontWeight = FontWeight.Bold) },
            text = { Text("本次骑行的轨迹与传感器数据将被存入本地数据库并同步至云端中控。") },
            confirmButton = {
                TextButton(onClick = { showStopConfirm = false; onStop() }) {
                    Text("确认结束", color = StopRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("继续骑行", color = BrandCyan)
                }
            },
        )
    }
}

/** 竖屏停靠式仪表盘：大速度环 + 时长/里程摘要 + 2×3 数据网格 + 控制按钮。 */
@Composable
private fun PortraitDashboard(
    state: RideState,
    locked: Boolean,
    highContrast: Boolean,
    onTogglePause: () -> Unit,
    onStopRequest: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val cadenceMode = state.sensorMode == SensorMode.CADENCE
    Surface(
        color = if (highContrast) Color(0xF6020A12) else Color(0xF00A1622),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 速度环 + 实时摘要
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SpeedRing(
                    value = if (cadenceMode) state.cadenceRpm else state.speedKmh,
                    unit = if (cadenceMode) "rpm" else "km/h",
                    maxValue = if (cadenceMode) 120.0 else 60.0,
                    diameterDp = 132,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        speedSourceLabel(state, cadenceMode),
                        fontSize = 13.sp,
                        color = if (state.isPaused) PauseOrange else DataLabel,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("⏱ " + state.durationText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SpeedText)
                    Text("🏁 %.2f km".format(state.distanceKm), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SpeedText)
                }
            }
            // 2×3 数据网格
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (highContrast) Color(0x59FFFFFF) else Color(0x22FFFFFF)),
            ) {
                DataGrid(state, Modifier.padding(vertical = 4.dp))
            }
            // 控制按钮行：锁屏 / 暂停恢复 / 结束
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 锁屏：点按锁定，长按解锁
                Box(
                    Modifier
                        .height(54.dp)
                        .background(if (locked) StopRed else Color(0xAA0B1622), RoundedCornerShape(14.dp))
                        .border(1.dp, BrandCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (!locked) onLock() },
                                onLongPress = { if (locked) onUnlock() },
                            )
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (locked) "🔒" else "🔓", fontSize = 20.sp)
                }
                // 暂停/恢复
                Surface(
                    onClick = onTogglePause,
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.isPaused) BrandCyan else PauseOrange,
                    contentColor = Color(0xFF060913),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.isPaused) "▶ 恢复" else "⏸ 暂停",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // 结束骑行
                Surface(
                    onClick = onStopRequest,
                    shape = RoundedCornerShape(14.dp),
                    color = StopRed,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("■ 结束", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 左上角语音开关悬浮按钮。 */
@Composable
private fun VoiceToggleButton(
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggleVoice,
        shape = RoundedCornerShape(22.dp),
        color = if (voiceEnabled) BrandCyan else Color(0xAA37424F),
        contentColor = if (voiceEnabled) Color(0xFF04121A) else Color.White,
        modifier = modifier,
    ) {
        Text(
            if (voiceEnabled) "🔊 语音开" else "🔇 语音关",
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 顶部 turn-by-turn 转向卡：转向图标 + 路名 + 当前段剩余 + 全程剩余/ETA。 */
@Composable
private fun TurnBanner(info: NaviBannerInfo, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF20B1622),
        contentColor = Color.White,
        modifier = modifier.border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(turnIcon(info.iconType), fontSize = 30.sp, color = BrandCyan)
            Column(Modifier.weight(1f)) {
                Text(
                    fmtDistance(info.segRemainMeters) + " 后",
                    fontSize = 13.sp,
                    color = DataLabel,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    info.nextRoad.ifBlank { "沿当前道路" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("剩余", fontSize = 11.sp, color = DataLabel)
                Text(
                    fmtDistance(info.routeRemainMeters) + " · " + fmtDuration(info.routeRemainSeconds),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCyan,
                )
            }
        }
    }
}

/** 横屏常驻控制按钮。 */
@Composable
private fun ControlButton(
    text: String,
    bg: Color,
    fg: Color,
    hPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
        modifier = Modifier.height(50.dp),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = hPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 速度来源/状态文字。 */
private fun speedSourceLabel(state: RideState, cadenceMode: Boolean): String = when {
    state.isPaused -> "⏱ 自动暂停中"
    cadenceMode -> "踏频 · 实时 rpm"
    state.speedSource == SpeedSource.SENSOR_WHEEL -> "速度来源 · 传感器"
    else -> "速度来源 · GPS"
}

/** 将高德转向 iconType 映射为简单方向箭头（仅视觉提示，未知类型回退直行）。 */
private fun turnIcon(type: Int): String = when (type) {
    2 -> "↰"
    3 -> "↱"
    4 -> "↖"
    5 -> "↗"
    6 -> "↙"
    7 -> "↘"
    8, 9 -> "↺"
    else -> "↑"
}

/** 距离格式化：≥1km 显示 km，否则 m。 */
private fun fmtDistance(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "$meters m"

/** 时长格式化：≥60 分显示小时+分，否则分。 */
private fun fmtDuration(seconds: Int): String {
    val m = seconds / 60
    return if (m >= 60) "%d小时%d分".format(m / 60, m % 60) else "$m 分"
}
