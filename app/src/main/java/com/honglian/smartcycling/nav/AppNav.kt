package com.honglian.smartcycling.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.map.MapViewModel
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
}

/**
 * 主导航图:配对 → 地图(搜索/预览路线) → 骑行(turn-by-turn 导航)。
 * @param onEnterRide / onExitRide 用于锁屏横屏与前台服务开关。
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

    val connection by pairingViewModel.connection.collectAsState()
    val devices by pairingViewModel.devices.collectAsState()
    val routePoints by mapViewModel.route.collectAsState()
    val destination by mapViewModel.destination.collectAsState()
    val startPoint by mapViewModel.startPoint.collectAsState()
    val mapStatus by mapViewModel.status.collectAsState()
    val currentWheel by settingsViewModel.wheel.collectAsState()
    
    // 全局地图样式和历史记录列表数据收集
    val mapType by settingsViewModel.mapType.collectAsState()
    val rides by container.rideRepository.observeRides().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    // 语音导航开关(跨横竖屏重建保持)
    var voiceEnabled by rememberSaveable { mutableStateOf(true) }

    NavHost(
        navController = navController, 
        startDestination = Routes.PAIRING,
        enterTransition = { slideInHorizontally(animationSpec = tween(350)) { it } + fadeIn(animationSpec = tween(200)) },
        exitTransition = { slideOutHorizontally(animationSpec = tween(350)) { -it } + fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { slideInHorizontally(animationSpec = tween(350)) { -it } + fadeIn(animationSpec = tween(200)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(350)) { it } + fadeOut(animationSpec = tween(200)) }
    ) {
        composable(Routes.PAIRING) {
            PairingScreen(
                connection = connection,
                devices = devices,
                onConnect = { pairingViewModel.connect(it) },
                onStartScan = { pairingViewModel.startScan() },
            )
            LaunchedEffect(connection) {
                if (connection == ConnectionState.READY) {
                    onPaired()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                }
            }
        }
        composable(Routes.MAP) {
            MapScreen(
                routePoints = routePoints,
                destination = destination,
                status = mapStatus,
                currentWheel = currentWheel,
                onSearch = { dest -> mapViewModel.planTo(dest) },
                onStartRide = {
                    rideViewModel.startRide()
                    onEnterRide()
                    navController.navigate(Routes.RIDE)
                },
                onSelectWheel = { settingsViewModel.select(it) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                mapType = mapType
            )
        }
        composable(Routes.RIDE) {
            val state by rideViewModel.state.collectAsState()
            val currentLatLng by rideViewModel.currentLatLng.collectAsState()
            RideScreen(
                state = state,
                routePoints = routePoints,
                destination = destination,
                startPoint = startPoint,
                currentLatLng = currentLatLng,
                voiceEnabled = voiceEnabled,
                onToggleVoice = { voiceEnabled = !voiceEnabled },
                onTogglePause = { rideViewModel.togglePause() },
                onStop = {
                    rideViewModel.stopRide()
                    // 清空上一次路线/目的地,回到地图即为干净可输入状态(便于中途换目的地)
                    mapViewModel.reset()
                    onExitRide()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.RIDE) { inclusive = true }
                    }
                },
                mapType = mapType
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                rides = rides,
                onDelete = { id ->
                    coroutineScope.launch {
                        container.rideRepository.deleteRide(id)
                    }
                },
                onGetTrackPoints = { id ->
                    container.rideRepository.trackPoints(id)
                },
                onBack = { navController.popBackStack() },
                mapType = mapType
            )
        }
    }
}

