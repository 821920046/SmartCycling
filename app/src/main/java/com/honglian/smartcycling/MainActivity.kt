package com.honglian.smartcycling

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.honglian.smartcycling.nav.AppNav
import com.honglian.smartcycling.ride.RideService
import com.honglian.smartcycling.ui.theme.SmartCyclingTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SmartCyclingApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SmartCyclingTheme {
                val connection by container.sensorManager.connection.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    if (result.values.all { it }) startAutoPairing()
                }

                LaunchedEffect(Unit) {
                    if (hasPermissions()) startAutoPairing()
                    else permissionLauncher.launch(requiredPermissions())
                }

                AppNav(
                    connection = connection,
                    onEnterRide = {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        startRideService()
                    },
                    onExitRide = {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        stopRideService()
                    },
                )
            }
        }
    }

    /** 启动自动配对:扫描到目标传感器后立即连接。 */
    private fun startAutoPairing() {
        val repo = container.pairingRepository
        val manager = container.sensorManager
        lifecycleScope.launch {
            // 扫到第一个名称匹配的传感器后停止扫描(first 会取消上游 → awaitClose 停扫),再发起连接。
            val target = repo.scan().first { repo.isTargetSensor(it.name) }
            manager.connectTo(target.device)
        }
    }

    private fun startRideService() {
        val intent = Intent(this, RideService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRideService() {
        stopService(Intent(this, RideService::class.java))
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
