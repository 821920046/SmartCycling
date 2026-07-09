package com.honglian.smartcycling.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.map.MapViewModel
import com.honglian.smartcycling.pairing.PairingViewModel
import com.honglian.smartcycling.ride.RideViewModel
import com.honglian.smartcycling.ui.screens.MapScreen
import com.honglian.smartcycling.ui.screens.PairingScreen
import com.honglian.smartcycling.ui.screens.RideScreen

object Routes {
    const val PAIRING = "pairing"
    const val MAP = "map"
    const val RIDE = "ride"
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

    // 语音导航开关(跨横竖屏重建保持)
    var voiceEnabled by rememberSaveable { mutableStateOf(true) }

    NavHost(navController = navController, startDestination = Routes.PAIRING) {
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
                onStop = {
                    rideViewModel.stopRide()
                    // 清空上一次路线/目的地,回到地图即为干净可输入状态(便于中途换目的地)
                    mapViewModel.reset()
                    onExitRide()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.RIDE) { inclusive = true }
                    }
                },
            )
        }
    }
}
