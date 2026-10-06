package com.honglian.smartcycling.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.map.MapViewModel
import com.honglian.smartcycling.offline.OfflineMapsViewModel
import com.honglian.smartcycling.pairing.PairingViewModel
import com.honglian.smartcycling.ride.RideViewModel
import com.honglian.smartcycling.ui.screens.*
import kotlinx.coroutines.launch

object Routes {
    const val PAIRING = "pairing"
    const val MAP = "map"
    const val RIDE = "ride"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val OFFLINE_MAPS = "offline_maps"
    const val SUMMARY = "summary"
}

/**
 * 主导航图:配对 → 地图(搜索/预览路线) → 骑行(turn-by-turn 或离线跟随)。
 *
 * 地图引擎选择在 [MapSource] 上分流:
 *  - ONLINE  → 高德(路线规划 / POI 搜索 / 语音播报)
 *  - OFFLINE → 本地瓦片(osmdroid),完全离线
 */
@Composable
fun AppNav(
    onPaired: () -> Unit,
    onEnterRide: () -> Unit,
    onExitRide: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartCyclingApp
    val container = app.container

    val navController = rememberNavController()
    val pairingViewModel: PairingViewModel = viewModel()
    val rideViewModel: RideViewModel = viewModel()
    val mapViewModel: MapViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val offlineViewModel: OfflineMapsViewModel = viewModel()

    val connection by pairingViewModel.connection.collectAsState()
    val devices by pairingViewModel.devices.collectAsState()
    val routePoints by mapViewModel.route.collectAsState()
    val destination by mapViewModel.destination.collectAsState()
    val startPoint by mapViewModel.startPoint.collectAsState()
    val mapStatus by mapViewModel.status.collectAsState()
    val suggestions by mapViewModel.suggestions.collectAsState()
    val planning by mapViewModel.planning.collectAsState()
    val currentWheel by settingsViewModel.wheel.collectAsState()

    val mapType by settingsViewModel.mapType.collectAsState()
    val mapSource by settingsViewModel.mapSource.collectAsState()
    val activeOfflineId by settingsViewModel.activeOfflineMapId.collectAsState()
    val offlineMaps by offlineViewModel.maps.collectAsState()
    val rides by container.rideRepository.observeRides().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    // 当前激活的离线底图描述(仅在文件仍存在时有效)
    val activeOfflineSpec = remember(offlineMaps, activeOfflineId) {
        offlineMaps.firstOrNull { it.id == activeOfflineId }
            ?.takeIf { offlineViewModel.isMissing(it).not() }
            ?.let { offlineViewModel.layerSpecOf(it) }
    }
    // 处于离线模式但离线包不可用时,自动回退到在线,避免用户看到空白地图。
    val effectiveSource = if (mapSource == MapSource.OFFLINE && activeOfflineSpec == null) {
        MapSource.ONLINE
    } else {
        mapSource
    }

    var voiceEnabled by rememberSaveable { mutableStateOf(true) }
    // 日照高对比模式(设置页可切换)
    val highContrast by settingsViewModel.highContrast.collectAsState()
    // 首次引导(未展示过则弹出一次)
    var showOnboarding by rememberSaveable { mutableStateOf(!container.settings.onboardingShown) }

    NavHost(
        navController = navController,
        startDestination = Routes.PAIRING,
        enterTransition = { slideInHorizontally(tween(320)) { it } + fadeIn(tween(180)) },
        exitTransition = { slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(180)) },
        popEnterTransition = { slideInHorizontally(tween(320)) { -it } + fadeIn(tween(180)) },
        popExitTransition = { slideOutHorizontally(tween(320)) { it } + fadeOut(tween(180)) },
    ) {
        composable(Routes.PAIRING) {
            PairingScreen(
                connection = connection,
                devices = devices,
                onConnect = { pairingViewModel.connect(it) },
                onStartScan = { pairingViewModel.startScan() },
            )
            androidx.compose.runtime.LaunchedEffect(connection) {
                if (connection == ConnectionState.READY) {
                    onPaired()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.MAP) {
            // 只在"地图页"被组合时才订阅定位(currentLatLng 用 WhileSubscribed 实现),
            // 离开该页 5s 后自动停止定位,避免整个 App 生命周期都在跑 GPS。
            //
            // 且**仅离线底图需要**这路定位:在线底图由高德自带定位驱动,
            // 若两种模式都订阅,就会同时跑两路定位白白耗电 —— 故在线时改订阅一个常量空流,
            // 使真实的 GPS 流失去订阅者而自动停止。
            val idleLocation = remember { flowOf<LatLng?>(null) }
            val locationFlow: Flow<LatLng?> = if (effectiveSource == MapSource.OFFLINE) {
                mapViewModel.currentLatLng
            } else {
                idleLocation
            }
            val currentLocation by locationFlow.collectAsState()
            MapScreen(
                routePoints = routePoints,
                destination = destination,
                status = mapStatus,
                currentWheel = currentWheel,
                onSearch = { mapViewModel.planTo(it) },
                onStartRide = {
                    rideViewModel.startRide()
                    onEnterRide()
                    navController.navigate(Routes.RIDE)
                },
                onSelectWheel = { settingsViewModel.select(it) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToOfflineMaps = { navController.navigate(Routes.OFFLINE_MAPS) },
                mapType = mapType,
                suggestions = suggestions,
                onKeywordChanged = { mapViewModel.searchSuggestions(it) },
                onSuggestionSelected = { poi -> mapViewModel.planToPoi(poi) },
                mapSource = effectiveSource,
                offlineSpec = activeOfflineSpec,
                planning = planning,
                currentLocation = currentLocation,
                onSwitchSource = { settingsViewModel.updateMapSource(it) },
                onSelectMapType = { settingsViewModel.updateMapType(it) },
            )
        }

        composable(Routes.RIDE) {
            val state by rideViewModel.state.collectAsState()
            val currentLatLng by rideViewModel.currentLatLng.collectAsState()
            val traveledPath by rideViewModel.traveledPath.collectAsState()
            RideScreen(
                state = state,
                routePoints = routePoints,
                destination = destination,
                startPoint = startPoint,
                currentLatLng = currentLatLng,
                traveledPoints = traveledPath,
                voiceEnabled = voiceEnabled,
                onToggleVoice = { voiceEnabled = !voiceEnabled },
                onTogglePause = { rideViewModel.togglePause() },
                onStop = {
                    rideViewModel.stopRide()
                    onExitRide()
                    if (rideViewModel.lastSummary.value != null) {
                        // 有有效成绩 → 进入成绩总结页
                        navController.navigate(Routes.SUMMARY) {
                            popUpTo(Routes.RIDE) { inclusive = true }
                        }
                    } else {
                        // 误触发(时长过短):清空路线直接回地图
                        mapViewModel.reset()
                        navController.navigate(Routes.MAP) {
                            popUpTo(Routes.RIDE) { inclusive = true }
                        }
                    }
                },
                mapType = mapType,
                mapSource = effectiveSource,
                offlineSpec = activeOfflineSpec,
                highContrast = highContrast
            )
        }
        composable(Routes.SUMMARY) {
            val summary by rideViewModel.lastSummary.collectAsState()
            RideSummaryScreen(
                state = summary,
                onDone = {
                    mapViewModel.reset()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.SUMMARY) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToOfflineMaps = { navController.navigate(Routes.OFFLINE_MAPS) },
            )
        }

        composable(Routes.OFFLINE_MAPS) {
            OfflineMapsScreen(
                viewModel = offlineViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                rides = rides,
                onDelete = { id ->
                    coroutineScope.launch { container.rideRepository.deleteRide(id) }
                },
                onGetTrackPoints = { id -> container.rideRepository.trackPoints(id) },
                onBack = { navController.popBackStack() },
                mapType = mapType,
                mapSource = effectiveSource,
                offlineSpec = activeOfflineSpec,
            )
        }
    }

    if (showOnboarding) {
        OnboardingDialog(onDismiss = {
            container.settings.onboardingShown = true
            showOnboarding = false
        })
    }
}
