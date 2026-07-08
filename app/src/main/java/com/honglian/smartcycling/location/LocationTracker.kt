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
                // 过滤低精度定位(>25m 误差不参与积分)
                val delta = if (prev != null && loc.accuracy <= 25f) {
                    val d = prev.distanceTo(loc).toDouble()
                    if (d < 1.0) 0.0 else d  // 静止漂移阀值 1m
                } else 0.0
                lastLocation = loc
                val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
                trySend(
                    LocationSample(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speedKmh = speedKmh,
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
