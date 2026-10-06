package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.offline.OfflineLayerSpec
import com.honglian.smartcycling.offline.OfflineMapView
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviMapView
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space
import kotlin.math.min

/**
 * 骑行数据界面(横屏):左侧地图 / 右侧仪表盘。
 *
 * 与旧版的关键差异:
 *  - **不再强制夜间地图**。旧版写死 `mapType = 3`,把用户在设置里选的图层直接吞掉;
 *    户外骑行时浅色底图反而更清晰。现在严格跟随用户设置。
 *  - 支持离线引擎:选择离线地图时直接渲染本地瓦片,不再依赖网络。
 *  - HUD 面板采用"高对比实底 + 表格数字",去掉发光特效,强光下可读性显著提升。
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
    mapSource: MapSource = MapSource.ONLINE,
    offlineSpec: OfflineLayerSpec? = null,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    var showStopConfirm by remember { mutableStateOf(false) }
    val offlineActive = mapSource == MapSource.OFFLINE && offlineSpec != null

    Row(modifier.fillMaxSize().background(palette.background)) {
        // ---------------- 左:地图 ----------------
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                offlineActive -> OfflineMapView(
                    spec = offlineSpec!!,
                    routePoints = routePoints,
                    destination = destination,
                    currentLocation = currentLatLng,
                    follow = true,
                    routeColor = palette.primary.toArgb(),
                    modifier = Modifier.fillMaxSize(),
                )
                destination != null -> NaviMapView(
                    destination = destination,
                    voiceEnabled = voiceEnabled,
                    routePoints = routePoints,
                    startPoint = startPoint,
                    currentLatLng = currentLatLng,
                    mapType = mapType,
                    onExitRequested = { showStopConfirm = true },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> NavigationMapView(
                    modifier = Modifier.fillMaxSize(),
                    routePoints = routePoints,
                    follow = true,
                    followLocation = currentLatLng,
                    mapType = mapType,
                )
            }

            // 语音开关(仅在线导航具备 TTS)
            if (destination != null && !offlineActive) {
                Surface(
                    onClick = onToggleVoice,
                    shape = RoundedCornerShape(Radius.pill),
                    color = palette.floatingSurface,
                    border = BorderStroke(1.dp, palette.outline),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(Space.md),
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (voiceEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                            contentDescription = null,
                            tint = if (voiceEnabled) palette.primary else palette.textTertiary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = if (voiceEnabled) "语音播报" else "已静音",
                            style = MaterialTheme.typography.label,
                            color = if (voiceEnabled) palette.textPrimary else palette.textTertiary,
                        )
                    }
                }
            }

            if (offlineActive) {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = palette.floatingSurface,
                    border = BorderStroke(1.dp, palette.outline),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(Space.md),
                ) {
                    Text(
                        "离线地图 · 无转向语音",
                        style = MaterialTheme.typography.caption,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    )
                }
            }
        }

        // ---------------- 右:仪表盘 ----------------
        Surface(
            color = palette.hudBackground,
            modifier = Modifier
                .width(308.dp)
                .fillMaxHeight()
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val ringDiameter = min(maxWidth.value * 0.78f, 196f).coerceAtLeast(96f)
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val cadenceMode = state.sensorMode == SensorMode.CADENCE
                    SpeedRing(
                        value = if (cadenceMode) state.cadenceRpm else state.speedKmh,
                        unit = if (cadenceMode) "rpm" else "km/h",
                        maxValue = if (cadenceMode) 120.0 else 60.0,
                        diameterDp = ringDiameter.toInt(),
                        accent = palette.primary,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        text = when {
                            state.isPaused -> "自动暂停中"
                            cadenceMode -> "踏频模式"
                            state.speedSource == SpeedSource.SENSOR_WHEEL -> "速度来源 · 传感器"
                            else -> "速度来源 · GPS"
                        },
                        style = MaterialTheme.typography.caption,
                        color = if (state.isPaused) palette.warning else palette.hudLabel,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(Space.md))
                    DataGrid(state)
                    Spacer(Modifier.weight(1f))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Button(
                            onClick = onTogglePause,
                            shape = RoundedCornerShape(Radius.md),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isPaused) palette.primary else palette.surfaceVariant,
                                contentColor = if (state.isPaused) palette.onPrimary else palette.textPrimary,
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                if (state.isPaused) "继续" else "暂停",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Button(
                            onClick = { showStopConfirm = true },
                            shape = RoundedCornerShape(Radius.md),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.danger,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1.25f).height(48.dp),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text("结束骑行", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text("结束本次骑行?", style = MaterialTheme.typography.title) },
            text = {
                Text(
                    "本次轨迹与传感器数据将被保存到本地,并同步至云端(如已配置)。",
                    style = MaterialTheme.typography.body,
                )
            },
            confirmButton = {
                TextButton(onClick = { showStopConfirm = false; onStop() }) {
                    Text("确认结束", color = palette.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("继续骑行", color = palette.primary)
                }
            },
        )
    }
}
