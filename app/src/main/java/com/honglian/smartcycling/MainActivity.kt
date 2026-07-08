package com.honglian.smartcycling

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.honglian.smartcycling.nav.AppNav
import com.honglian.smartcycling.ride.RideService
import com.honglian.smartcycling.ui.theme.SmartCyclingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SmartCyclingTheme {
                AppNav(
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

    private fun startRideService() {
        val intent = Intent(this, RideService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRideService() {
        stopService(Intent(this, RideService::class.java))
    }
}
