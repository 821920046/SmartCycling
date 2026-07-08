package com.honglian.smartcycling.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.map.MapViewModel
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
 * 主导航图:配对 → 地图 → 骑行。
 * @param connection 传感器连接状态(由上层传入)
 * @param onEnterRide / onExitRide 用于锁屏横屏与前台服务开关
 */
@Composable
fun AppNav(
    connection: ConnectionState,
    onEnterRide: () -> Unit,
    onExitRide: () -> Unit,
) {
    val navController = rememberNavController()
    val rideViewModel: RideViewModel = viewModel()
    val mapViewModel: MapViewModel = viewModel()
    val routePoints by mapViewModel.route.collectAsState()
    val mapStatus by mapViewModel.status.collectAsState()

    NavHost(navController = navController, startDestination = Routes.PAIRING) {
        composable(Routes.PAIRING) {
            PairingScreen(connection)
            LaunchedEffect(connection) {
                if (connection == ConnectionState.READY) {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                }
            }
        }
        composable(Routes.MAP) {
            MapScreen(
                routePoints = routePoints,
                status = mapStatus,
                onRide = { dest ->
                    mapViewModel.planTo(dest)
                    rideViewModel.startRide()
                    onEnterRide()
                    navController.navigate(Routes.RIDE)
                },
            )
        }
        composable(Routes.RIDE) {
            val state by rideViewModel.state.collectAsState()
            RideScreen(
                state = state,
                routePoints = routePoints,
                onStop = {
                    rideViewModel.stopRide()
                    onExitRide()
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.RIDE) { inclusive = true }
                    }
                },
            )
        }
    }
}
