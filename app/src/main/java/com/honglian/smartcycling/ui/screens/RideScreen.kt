package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.honglian.smartcycling.ui.theme.PauseOrange
import com.honglian.smartcycling.ui.theme.GlassBorder
import com.honglian.smartcycling.ui.theme.GlassBg
import com.honglian.smartcycling.ui.theme.SpeedText
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviMapView
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.CardBg
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.DividerNavy
import com.honglian.smartcycling.ui.theme.PanelBg
import com.honglian.smartcycling.ui.theme.PanelBgBottom
import com.honglian.smartcycling.ui.theme.PanelBgTop
import com.honglian.smartcycling.ui.theme.StopRed
import kotlin.math.min

/**
 * 骑行中数据界面:左导航 / 右仪表盘(横屏)。
 * - 有目的地时左侧为完整 turn-by-turn 导航(转向箭头+语音,带真实起点);否则为跟随地图。
 * - 左上角提供语音开关(悬浮、置顶、加大点击区域)。
 * - 右侧仪表盘按屏幕高度自适应速度环尺寸,保证整屏可见、无需滚动。
 */
@Composable
fun RideScreen(
    state: RideState,

    routePoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    startPoint: LatLng? = null,
    currentLatLng: LatLng? = null,
    voiceEnabled: Boolean = true,
    onToggleVoice: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onStop: () -> Unit,
    mapType: Int = 3,
    modifier: Modifier = Modifier,
) {
    var showStopConfirm by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // 1. 底层：沉浸式全屏地图
        if (destination != null) {
            NaviMapView(
                destination = destination,
                voiceEnabled = voiceEnabled,
                routePoints = routePoints,
                startPoint = startPoint,
                currentLatLng = currentLatLng,
                mapType = 3, // 强行夜间模式！赛博朋克必须黑底
                onExitRequested = { showStopConfirm = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavigationMapView(
                modifier = Modifier.fillMaxSize(),
                routePoints = routePoints,
                follow = true,
                followLocation = currentLatLng,
                mapType = 3,
            )
        }

        // 2. 左上角：悬浮语音开关
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

        // 3. 右侧：悬浮数据仪表盘 (Glassmorphism HUD)
        BoxWithConstraints(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp) // 固定合理宽度
                .padding(vertical = 16.dp, horizontal = 16.dp)
                .background(Color(0x8804121A), RoundedCornerShape(24.dp))
                .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .safeDrawingPadding()
                .zIndex(2f),
        ) {
            val ringDiameter = min(maxWidth.value * 0.82f, 200f).coerceAtLeast(80f)
            
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cadenceMode = state.sensorMode == SensorMode.CADENCE
                    SpeedRing(
                        value = if (cadenceMode) state.cadenceRpm else state.speedKmh,
                        unit = if (cadenceMode) "rpm" else "km/h",
                        maxValue = if (cadenceMode) 120.0 else 60.0,
                        diameterDp = ringDiameter.toInt(),
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
                        fontWeight = FontWeight.Bold
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                    ) {
                        DataGrid(state, Modifier.padding(vertical = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    
                    // 双重操作控制按钮组合
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onTogglePause,
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.isPaused) BrandCyan else PauseOrange,
                            contentColor = Color(0xFF060913),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.isPaused) "▶ 恢复" else "⏸ 暂停",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Surface(
                            onClick = { showStopConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            color = StopRed,
                            contentColor = Color.White,
                            modifier = Modifier.weight(1.3f).height(46.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("结束骑行", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 自动暂停时的半透明磨砂遮罩罩在数据区
                if (state.isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x33000000), RoundedCornerShape(24.dp))
                            .clickable(onClick = onTogglePause)
                    )
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
            text = { Text("本次骑行的轨迹与传感器数据将被封盘存入数据库并同步至云端。") },
            confirmButton = {
                TextButton(onClick = { showStopConfirm = false; onStop() }) {
                    Text("确认结束", color = StopRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("继续骑行", color = BrandCyan)
                }
            }
        )
    }
}

