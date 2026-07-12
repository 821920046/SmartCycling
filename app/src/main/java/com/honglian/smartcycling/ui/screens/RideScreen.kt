package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * 骑行中数据界面(横屏):全屏实时地图 + 可自由拖动/缩放的悬浮仪表盘 + 常驻控制按钮。
 * - 仪表盘:单指拖动到任意位置、双指捉合缩放、双击复位;位置与缩放跨重建/横竖屏保持。
 * - 控制按钮(暂停/恢复、结束骑行)固定于底部、始终置顶可见,不受仪表盘拖动/自动暂停遮罩影响。
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
    modifier: Modifier = Modifier,
) {
    var showStopConfirm by remember { mutableStateOf(false) }
    // turn-by-turn 转向卡数据(来自 headless 导航引擎)
    var naviInfo by remember { mutableStateOf<NaviBannerInfo?>(null) }
    // 可见路线:初始用规划路线,导航引擎算路/偏航重算后用引擎真实路线覆盖
    var liveRoute by remember(routePoints) { mutableStateOf(routePoints) }

    // 悬浮仪表盘的位置与缩放(跨重建/横竖屏保持)。默认 (0,0) 即靠右居中。
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var scale by rememberSaveable { mutableStateOf(1f) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // 1. 底层:全屏实时跟随地图 + 已规划路线
        NavigationMapView(
            modifier = Modifier.fillMaxSize(),
            routePoints = liveRoute,
            traveledPoints = traveledPoints,
            destination = destination,
            follow = true,
            mapType = 3,
        )
        // turn-by-turn 语音诱导(无界面 headless 引擎;失败只影响语音,不影响地图)
        if (destination != null) {
            NaviVoiceGuide(
                destination = destination,
                startPoint = startPoint,
                currentLatLng = currentLatLng,
                enabled = voiceEnabled,
                onNaviInfo = { naviInfo = it },
                onRoutePath = { path -> if (path.isNotEmpty()) liveRoute = path },
            )
        }

        // 2. 左上角:悬浮语音开关
        if (destination != null) {
            Surface(
                onClick = onToggleVoice,
                shape = RoundedCornerShape(22.dp),
                color = if (voiceEnabled) BrandCyan else Color(0xAA37424F),
                contentColor = if (voiceEnabled) Color(0xFF04121A) else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .zIndex(2f),
            ) {
                Text(
                    if (voiceEnabled) "🔊 语音开" else "🔇 语音关",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // 2b. 顶部居中:turn-by-turn 转向卡(转向图标 + 路名 + 当前段剩余 + 全程剩余/ETA)
        naviInfo?.let { info ->
            if (destination != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF20B1622),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = 12.dp)
                        .widthIn(max = 460.dp)
                        .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .zIndex(4f),
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
        }

        // 3. 可拖动 + 可缩放的悬浮数据仪表盘(Glassmorphism HUD)
        val cadenceMode = state.sensorMode == SensorMode.CADENCE
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .safeDrawingPadding()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0.5f) // 以右中为缩放锚点,默认不出屏
                }
                .padding(12.dp)
                .width(300.dp)
                .background(Color(0x8804121A), RoundedCornerShape(24.dp))
                .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // pan 为本地(已缩放)坐标,乘 scale 换算为屏幕位移,拖动手感与手指一致。
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
                when {
                    state.isPaused -> "⏱ 自动暂停中"
                    cadenceMode -> "踏频 · 实时 rpm"
                    state.speedSource == SpeedSource.SENSOR_WHEEL -> "速度来源 · 传感器"
                    else -> "速度来源 · GPS"
                },
                fontSize = 11.sp,
                color = if (state.isPaused) PauseOrange else DataLabel,
                fontWeight = FontWeight.Bold,
            )
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
            ) {
                DataGrid(state, Modifier.padding(vertical = 4.dp))
            }
        }

        // 4. 常驻控制按钮(底部右侧,始终可见且置顶,不受仪表盘拖动/缩放影响)
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp)
                .zIndex(6f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onTogglePause,
                shape = RoundedCornerShape(14.dp),
                color = if (state.isPaused) BrandCyan else PauseOrange,
                contentColor = Color(0xFF060913),
                modifier = Modifier.height(50.dp),
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.isPaused) "▶ 恢复" else "⏸ 暂停",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                onClick = { showStopConfirm = true },
                shape = RoundedCornerShape(14.dp),
                color = StopRed,
                contentColor = Color.White,
                modifier = Modifier.height(50.dp),
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("■ 结束骑行", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 误触确认对话框
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

/** 将高德转向 iconType 映射为简单方向箭头(仅视觉提示,未知类型回退直行)。 */
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

/** 距离格式化:≥01km 显示 km,否则 m。 */
private fun fmtDistance(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "$meters m"

/** 时长格式化:≥60 分显示小时+分,否则分。 */
private fun fmtDuration(seconds: Int): String {
    val m = seconds / 60
    return if (m >= 60) "%d小时%d分".format(m / 60, m % 60) else "$m 分"
}
