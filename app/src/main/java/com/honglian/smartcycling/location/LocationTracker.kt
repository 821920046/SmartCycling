package com.honglian.smartcycling.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 一次定位采样。 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    /** GPS 直接提供的速度(km/h),作为无速度传感器时的来源。 */
    val speedKmh: Double,
    /** 与上一点的直线距离(米),用于里程积分。 */
    val deltaMeters: Double,
    val timestampMs: Long,
)

/**
 * 基于 FusedLocationProvider 的轨迹与速度采集器。
 * 里程采用相邻点距离积分,并过滤低精度/静止漂移。
 */
class LocationTracker(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun track(intervalMs: Long = 1000L): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMinUpdateDistanceMeters(0f)
            .build()

        var lastLocation: Location? = null
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val prev = lastLocation
                
                var delta = 0.0
                var isJumpPoint = false

                if (prev != null && loc.accuracy <= 25f) {
                    val d = prev.distanceTo(loc).toDouble()
                    val dt = (loc.time - prev.time) / 1000.0
                    
                    if (dt > 0.0) {
                        val calcSpeedKmh = (d / dt) * 3.6
                        if (calcSpeedKmh > 80.0) {
                            // 时速超过 80km/h，极有可能是隧道/高架导致的跳点漂移，标记为跳点
                            isJumpPoint = true
                        }
                    }
                    
                    if (!isJumpPoint) {
                        delta = if (d < 1.0) 0.0 else d // 静止漂移阈值 1m
                    }
                }

                // 如果是跳点，不更新 lastLocation，防止持续受影响
                if (!isJumpPoint) {
                    lastLocation = loc
                }

                val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
                // 同样，如果自身上报速度异常，或者计算异常，可以限制最大速度表现
                val finalSpeedKmh = if (speedKmh > 80.0) 0.0 else speedKmh

                trySend(
                    LocationSample(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speedKmh = finalSpeedKmh,
                        deltaMeters = delta,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }

    }
}
