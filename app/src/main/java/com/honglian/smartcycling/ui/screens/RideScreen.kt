package com.honglian.smartcycling.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.R
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.AltitudeColumn
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviBannerInfo
import com.honglian.smartcycling.ui.components.NaviVoiceGuide
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.core.Units
import com.honglian.smartcycling.offline.OfflineLayerSpec
import com.honglian.smartcycling.offline.OfflineMapView
import com.honglian.smartcycling.offline.toWgs84
import com.honglian.smartcycling.ui.theme.AppTheme
import androidx.compose.ui.graphics.toArgb
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.BrandGreen
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
 * - 竖屏：全屏地图 + 底部可拖动、双指缩放、双击复位的悬浮仪表盘。
 * - 横屏：全屏地图 + 右侧可拖动、双指缩放、双击复位的悬浮仪表盘。
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
    mapSource: MapSource = MapSource.ONLINE,
    offlineSpec: OfflineLayerSpec? = null,
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
    // 竖屏悬浮仪表盘：可拖动、可缩放，且在屏幕旋转后保留位置与缩放比例。
    var portraitOffsetX by rememberSaveable { mutableStateOf(0f) }
    var portraitOffsetY by rememberSaveable { mutableStateOf(0f) }
    var portraitScale by rememberSaveable { mutableStateOf(1f) }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val units = AppTheme.units
    if (isPortrait) {
        // ===== 竖屏：全屏地图 + 可缩放悬浮仪表盘 =====
        // 地图始终铺满全屏；仪表盘只是一层 HUD，不再占据底部布局高度或制造黑色空白。
        Box(modifier.fillMaxSize().background(Color.Black)) {
            RideBaseMap(
                mapSource = mapSource,
                offlineSpec = offlineSpec,
                routePoints = liveRoute,
                traveledPoints = traveledPoints,
                destination = destination,
                currentLatLng = currentLatLng,
                mapType = mapType,
            )
            if (destination != null) {
                NaviVoiceGuide(
                    destination = destination,
                    startPoint = startPoint,
                    currentLatLng = currentLatLng,
                    routePoints = liveRoute,
                    enabled = voiceEnabled,
                    units = units,
                    onNaviInfo = { naviInfo = it },
                    onRoutePath = { path -> if (path.isNotEmpty()) liveRoute = path },
                )
                VoiceToggleButton(
                    voiceEnabled = voiceEnabled,
                    onToggleVoice = onToggleVoice,
                    modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp).zIndex(4f),
                )
                naviInfo?.let { info ->
                    TurnBanner(
                        info = info,
                        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding()
                            .padding(top = 10.dp, start = 8.dp, end = 8.dp).widthIn(max = 460.dp).zIndex(5f),
                    )
                }
            }
            // 底部悬浮 HUD：单指拖动，双指缩放，双击复位；始终覆盖地图而非挤压地图。
            Box(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 10.dp, vertical = 10.dp)
                    .offset { IntOffset(portraitOffsetX.roundToInt(), portraitOffsetY.roundToInt()) }
                    .graphicsLayer {
                        scaleX = portraitScale
                        scaleY = portraitScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .widthIn(max = 380.dp).fillMaxWidth(0.96f)
                    .zIndex(8f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            portraitOffsetX += pan.x
                            portraitOffsetY += pan.y
                            portraitScale = (portraitScale * zoom).coerceIn(0.58f, 1.15f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            portraitOffsetX = 0f
                            portraitOffsetY = 0f
                            portraitScale = 1f
                        })
                    },
            ) {
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
            // 实时海拔水柱:固定锚在左上角,刻意**不**参与仪表盘的拖动/缩放 ——
            // 它是"一眼扫过"的仪表,被用户误拖到屏幕外就失去意义了。
            // top 取 80dp:竖屏时转向卡几乎横贯整屏(高约 62dp + 上边距 10dp),
            // 这个值给它留出 8dp 净空,同时避开左上角的语音开关。
            AltitudeColumn(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(top = 80.dp, start = 8.dp)
                    .zIndex(6f),
            )
            if (locked) {
                Box(Modifier.fillMaxSize().zIndex(7f).pointerInput(Unit) { detectTapGestures { } })
            }
        }
    } else {
        // ===== 横屏：全屏地图 + 右侧可拖动缩放悬浮仪表盘（原设计） =====
        val cadenceMode = state.sensorMode == SensorMode.CADENCE
        Box(modifier.fillMaxSize().background(Color.Black)) {
            RideBaseMap(
                mapSource = mapSource,
                offlineSpec = offlineSpec,
                routePoints = liveRoute,
                traveledPoints = traveledPoints,
                destination = destination,
                currentLatLng = currentLatLng,
                mapType = mapType,
            )
            if (destination != null) {
                NaviVoiceGuide(
                    destination = destination,
                    startPoint = startPoint,
                    currentLatLng = currentLatLng,
                    routePoints = liveRoute,
                    enabled = voiceEnabled,
                    units = units,
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
                    .widthIn(max = 300.dp)
                    .width(264.dp)
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
                    stringResource(R.string.ride_hud_hint),
                    fontSize = 10.sp,
                    color = DataLabel,
                    fontWeight = FontWeight.Medium,
                )
                SpeedRing(
                    value = if (cadenceMode) state.cadenceRpm else Units.speedValue(state.speedKmh, units),
                    unit = if (cadenceMode) "rpm" else Units.speedUnit(units),
                    // 满量程也要跟着换算,否则切到英制后指针永远打不满(60 km/h = 37 mph)。
                    maxValue = if (cadenceMode) 120.0 else Units.speedValue(60.0, units),
                    diameterDp = 150,
                    // 双主题重构后 SpeedRing 的强调色改为由调用方传入(原来是写死的青→绿渐变),
                    // 这里用 HUD 主强调色。
                    accent = BrandCyan,
                )
                Text(
                    speedSourceLabel(state, cadenceMode),
                    fontSize = 11.sp,
                    color = if (state.isPaused) PauseOrange else DataLabel,
                    fontWeight = FontWeight.Bold,
                )
                // 自动分圈开启时,在速度来源下方补一行当前圈进度。
                if (state.autoLapEnabled) {
                    Text(
                        stringResource(
                            R.string.ride_hud_lap,
                            state.currentLap,
                            Units.distanceText(state.lapDistanceKm, units),
                        ),
                        fontSize = 11.sp,
                        color = BrandCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
                    text = stringResource(if (state.isPaused) R.string.ride_resume else R.string.ride_pause),
                    bg = if (state.isPaused) BrandCyan else PauseOrange,
                    fg = Color(0xFF060913),
                    hPadding = 18.dp,
                    onClick = { if (!locked) onTogglePause() },
                )
                ControlButton(
                    text = stringResource(R.string.ride_stop),
                    bg = StopRed,
                    fg = Color.White,
                    hPadding = 24.dp,
                    onClick = { if (!locked) showStopConfirm = true },
                )
            }

            // 锁屏防误触按钮（左下角）：点按锁定，长按解锁。
            // 触控目标:横屏这里原先靠 padding 撑高(14sp 文字 + 上下 14dp ≈ 45dp),
            // 低于 Material 无障碍下限 48dp;改为显式 heightIn(min = 48.dp) + 居中。
            // (竖屏那个本来就用 .height(buttonHeight),已是 48/52dp,无需改动。)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .zIndex(7f)
                    .heightIn(min = 48.dp)
                    .background(if (locked) StopRed else Color(0xAA0B1622), RoundedCornerShape(14.dp))
                    .border(1.dp, BrandCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (!locked) locked = true },
                            onLongPress = { if (locked) locked = false },
                        )
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (locked) R.string.ride_locked else R.string.ride_lock),
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 实时海拔水柱:横屏同样锚左上角(右侧是仪表盘、右下是控制按钮、左下是锁屏按钮)。
            AltitudeColumn(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(top = 80.dp, start = 8.dp)
                    .zIndex(6f),
            )

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

    // 误触确认对话框（横竖屏共用）。
    //
    // ⚠️ 配色契约:本弹窗**保持深色**,与骑行 HUD 同族 —— 它浮在"全屏地图 + 深色玻璃
    // 仪表盘"之上,四周全是深色面板,弹一个浅色框反而割裂。
    // 注意这与 [com.honglian.smartcycling.ui.screens.OnboardingDialog] /
    // [com.honglian.smartcycling.ui.screens.RideRecoveryDialog] 相反:那两个是**应用级**
    // 弹窗,浮在已主题化的地图页上,必须跟随主题。
    // 判定准则:弹窗浮在"刻意写死的深色表面"上 → 深色;浮在"跟随主题的页面"上 → 跟随主题。
    // 由于本弹窗内部全部使用恒定物理色(白/霓虹青/红压在不透明深蓝上),
    // 两种主题下对比度一致,不存在可读性风险。
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = CardBg,
            titleContentColor = SpeedText,
            textContentColor = DataLabel,
            title = { Text(stringResource(R.string.ride_stop_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ride_stop_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showStopConfirm = false; onStop() }) {
                    Text(stringResource(R.string.ride_confirm_stop), color = StopRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text(stringResource(R.string.ride_continue), color = BrandCyan)
                }
            },
        )
    }
}

/** 竖屏自适应仪表盘：按可用高度压缩字号、间距与地图比例；控制区永远独立于数据区。 */
@Composable
private fun PortraitDashboard(
    state: RideState,
    locked: Boolean,
    highContrast: Boolean,
    onTogglePause: () -> Unit,
    onStopRequest: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cadenceMode = state.sensorMode == SensorMode.CADENCE
    val units = AppTheme.units
    val compact = LocalConfiguration.current.screenHeightDp < 760
    val ringSize = if (compact) 92 else 108
    val cardHeight = if (compact) 48.dp else 54.dp
    val buttonHeight = if (compact) 48.dp else 52.dp
    val sidePadding = if (compact) 12.dp else 16.dp
    val gap = if (compact) 7.dp else 10.dp

    Surface(
        // 半透明玻璃 HUD：地图仍可透出，同时用描边和高对比文字保证骑行中一眼可读。
        color = if (highContrast) Color(0xD9020A12) else Color(0x66071420),
        contentColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth()
            .border(1.dp, BrandCyan.copy(alpha = 0.70f), RoundedCornerShape(22.dp)),
    ) {
        // 关键：使用内容高度，不再 fillMaxSize 占满父级，杜绝底部黑色空白。
        Column(Modifier.fillMaxWidth().wrapContentHeight()) {
            // 顶部一行:左侧显示当前分圈进度(仅开启分圈时),中间是拖动把手。
            Box(Modifier.fillMaxWidth().padding(top = 7.dp, start = sidePadding, end = sidePadding)) {
                if (state.autoLapEnabled) {
                    Text(
                        stringResource(
                            R.string.ride_hud_lap,
                            state.currentLap,
                            Units.distanceText(state.lapDistanceKm, units),
                        ),
                        fontSize = 11.sp,
                        color = BrandCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                Box(
                    Modifier.align(Alignment.Center)
                        .width(38.dp).height(4.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(3.dp)),
                )
            }
            // 这里不再使用会把内容滚到按钮下方的滚动容器；所有核心数据在一屏内自适应缩放。
            Column(
                Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                ) {
                    SpeedRing(
                        value = if (cadenceMode) state.cadenceRpm else Units.speedValue(state.speedKmh, units),
                        unit = if (cadenceMode) "rpm" else Units.speedUnit(units),
                        maxValue = if (cadenceMode) 120.0 else Units.speedValue(60.0, units),
                        diameterDp = ringSize,
                        accent = BrandCyan,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
                        StatusPill(text = speedSourceLabel(state, cadenceMode), paused = state.isPaused)
                        CompactHeroStat("⏱", stringResource(R.string.ride_stat_duration), state.durationText, BrandCyan, compact)
                        CompactHeroStat("🏁", stringResource(R.string.ride_stat_distance), Units.distanceText(state.distanceKm, units), BrandGreen, compact)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    AdaptiveStatChip(
                        Modifier.weight(1f),
                        stringResource(R.string.ride_stat_avg_speed),
                        "%.1f".format(Units.speedValue(state.avgSpeedKmh, units)),
                        Units.speedUnit(units), BrandCyan, highContrast, cardHeight, compact,
                    )
                    AdaptiveStatChip(
                        Modifier.weight(1f),
                        stringResource(if (cadenceMode) R.string.ride_stat_avg_cadence else R.string.ride_stat_cadence),
                        if (cadenceMode) "${state.avgCadenceRpm.roundToInt()}" else "0",
                        "rpm", BrandGreen, highContrast, cardHeight, compact,
                    )
                }
            }
            // 固定操作栏在安全区内：不参与滚动、不被手势条或统计卡片覆盖。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = sidePadding).padding(top = 4.dp)
                    .navigationBarsPadding().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.height(buttonHeight).background(if (locked) StopRed else Color(0x1A0FF2FE), RoundedCornerShape(14.dp))
                        .border(1.dp, BrandCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (!locked) onLock() }, onLongPress = { if (locked) onUnlock() }) }
                        .padding(horizontal = if (compact) 13.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (locked) "🔒" else "🔓", fontSize = if (compact) 17.sp else 19.sp) }
                Surface(onClick = onTogglePause, shape = RoundedCornerShape(14.dp), color = if (state.isPaused) BrandCyan else PauseOrange, contentColor = Color(0xFF060913), modifier = Modifier.weight(1f).height(buttonHeight)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(if (state.isPaused) R.string.ride_resume else R.string.ride_pause), fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.Bold) }
                }
                Surface(onClick = onStopRequest, shape = RoundedCornerShape(14.dp), color = StopRed, contentColor = Color.White, modifier = Modifier.weight(1f).height(buttonHeight)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.ride_stop_short), fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CompactHeroStat(icon: String, label: String, value: String, accent: Color, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(icon, fontSize = if (compact) 13.sp else 15.sp)
        Text(value, fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Text(label, fontSize = if (compact) 10.sp else 11.sp, color = Color(0xFFBCEFFF), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun AdaptiveStatChip(modifier: Modifier, label: String, value: String, unit: String, accent: Color, highContrast: Boolean, height: androidx.compose.ui.unit.Dp, compact: Boolean) {
    Row(modifier.height(height).background(if (highContrast) Color(0x66FFFFFF) else Color(0x3D061C2B), RoundedCornerShape(13.dp)).border(1.dp, accent.copy(alpha = 0.72f), RoundedCornerShape(13.dp)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = if (compact) 7.dp else 9.dp).width(3.dp).height(if (compact) 25.dp else 29.dp).background(accent, RoundedCornerShape(2.dp)))
        Column(Modifier.padding(horizontal = if (compact) 8.dp else 10.dp)) {
            Text(label, fontSize = if (compact) 10.sp else 11.sp, color = Color(0xFFE2F7FF), fontWeight = FontWeight.Bold, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                Text(unit, fontSize = if (compact) 10.sp else 11.sp, color = Color(0xFFBCEFFF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp), maxLines = 1)
            }
        }
    }
}

/** 状态药丸:速度来源 / 自动暂停中。 */
@Composable
private fun StatusPill(text: String, paused: Boolean) {
    val c = if (paused) PauseOrange else BrandCyan
    Box(
        Modifier
            .background(c.copy(alpha = 0.28f), RoundedCornerShape(50))
            .border(1.dp, c.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
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
            stringResource(if (voiceEnabled) R.string.ride_voice_on else R.string.ride_voice_off),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 顶部 turn-by-turn 转向卡：转向图标 + 路名 + 当前段剩余 + 全程剩余/ETA。 */
@Composable
private fun TurnBanner(info: NaviBannerInfo, modifier: Modifier = Modifier) {
    // ifBlank 的 lambda 不是组合上下文,需在此提前取好回退文案。
    val alongCurrentRoad = stringResource(R.string.ride_banner_along)
    val units = AppTheme.units
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
                    stringResource(R.string.ride_banner_after, Units.shortDistanceText(info.segRemainMeters.toDouble(), units)),
                    fontSize = 13.sp,
                    color = DataLabel,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    info.nextRoad.ifBlank { alongCurrentRoad },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.ride_banner_remaining), fontSize = 11.sp, color = DataLabel)
                Text(
                    Units.shortDistanceText(info.routeRemainMeters.toDouble(), units) + " · " + fmtDuration(info.routeRemainSeconds),
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
@Composable
private fun speedSourceLabel(state: RideState, cadenceMode: Boolean): String = when {
    state.isPaused -> stringResource(R.string.ride_status_auto_paused)
    cadenceMode -> stringResource(R.string.ride_status_cadence)
    state.speedSource == SpeedSource.SENSOR_WHEEL -> stringResource(R.string.ride_status_sensor)
    else -> stringResource(R.string.ride_status_gps)
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

/** 时长格式化：≥60 分显示小时+分，否则分。 */
@Composable
private fun fmtDuration(seconds: Int): String {
    val m = seconds / 60
    return if (m >= 60) {
        stringResource(R.string.ride_duration_hm, m / 60, m % 60)
    } else {
        stringResource(R.string.ride_duration_min, m)
    }
}

/**
 * 骑行底图:离线时渲染本地瓦片(osmdroid),否则走高德在线引擎。
 *
 * 坐标契约:高德的路线/定位都是 **GCJ-02**,而离线底图按 **WGS-84** 网格渲染,
 * 因此离线分支必须先把 GCJ-02 转成 WGS-84,否则车标与路线会整体偏移 300~600 米。
 */
@Composable
private fun RideBaseMap(
    mapSource: MapSource,
    offlineSpec: OfflineLayerSpec?,
    routePoints: List<LatLng>,
    traveledPoints: List<LatLng>,
    destination: LatLng?,
    currentLatLng: LatLng?,
    mapType: Int,
) {
    if (mapSource == MapSource.OFFLINE && offlineSpec != null) {
        val palette = AppTheme.palette
        val wgsRoute = remember(routePoints) { routePoints.map { it.toWgs84() } }
        val wgsDest = remember(destination) { destination?.toWgs84() }
        val wgsLoc = remember(currentLatLng) { currentLatLng?.toWgs84() }
        OfflineMapView(
            spec = offlineSpec,
            routePoints = wgsRoute,
            destination = wgsDest,
            currentLocation = wgsLoc,
            follow = true,
            routeColor = palette.primary.toArgb(),
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        NavigationMapView(
            modifier = Modifier.fillMaxSize(),
            routePoints = routePoints,
            traveledPoints = traveledPoints,
            destination = destination,
            follow = true,
            mapType = mapType,
        )
    }
}
