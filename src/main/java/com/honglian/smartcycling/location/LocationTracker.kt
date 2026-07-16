package com.honglian.smartcycling.location

import android.content.Context
import android.location.Location
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 一次定位采样。坐标为 GCJ-02(与高德地图/路线一致)。 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    /** GPS 直接提供的速度(km/h)，作为无速度传感器时的来源。 */
    val speedKmh: Double,
    /** 与上一点的直线距离(米)，用于里程积分。 */
    val deltaMeters: Double,
    /** 海拔高度(米),来自 GPS;0 表示不可用。 */
    val altitude: Double = 0.0,
    val timestampMs: Long,
)

/**
 * 基于高德 AMapLocationClient 的轨迹与速度采集器。
 *
 * 第一性原则修正:原先用 Google FusedLocationProvider(play-services-location),
 * 在国内无 Google Play 服务的手机上永不回调 → 定位/里程/实时跟随全部失效。
 * 现统一改用高德定位(SDK 已内置,与搜索/路线规划同源),全国可用且直接输出 GCJ-02。
 * 里程采用相邻点距离积分,并过滤低精度/静止漂移/隐路跳点。
 */
class LocationTracker(private val context: Context) {

    fun track(intervalMs: Long = 1000L): Flow<LocationSample> = callbackFlow {
        // AMapLocationClient 创建可能因隐私合规未就绪等抛异常;失败则不发数,
        // 交由上层(传感器里程回退)兄底,绝不因定位失败而崩溃。
        val client = runCatching { AMapLocationClient(context.applicationContext) }.getOrNull()
        if (client == null) {
            awaitClose { }
            return@callbackFlow
        }

        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = intervalMs
            isNeedAddress = false
            isOnceLocation = false
            isLocationCacheEnable = false
        }
        runCatching { client.setLocationOption(option) }

        var lastLat = 0.0
        var lastLng = 0.0
        var lastTime = 0L
        var hasPrev = false

        val listener = AMapLocationListener { loc: AMapLocation? ->
            if (loc == null || loc.errorCode != 0) return@AMapLocationListener
            val lat = loc.latitude
            val lng = loc.longitude
            val now = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()

            var delta = 0.0
            var isJump = false
            if (hasPrev && loc.accuracy <= 30f) {
                val d = distanceMeters(lastLat, lastLng, lat, lng)
                val dt = (now - lastTime) / 1000.0
                if (dt > 0.0 && (d / dt) * 3.6 > 80.0) {
                    // 时速 > 80km/h 极可能是隐道/高架跳点漂移,标记为跳点
                    isJump = true
                }
                if (!isJump) delta = if (d < 1.0) 0.0 else d // 静止漂移阈值 1m
            }
            if (!isJump) {
                lastLat = lat
                lastLng = lng
                lastTime = now
                hasPrev = true
            }

            val rawSpeedKmh = loc.speed * 3.6 // AMapLocation.speed 单位 m/s
            val speedKmh = if (rawSpeedKmh in 0.0..80.0) rawSpeedKmh else 0.0

            trySend(
                LocationSample(
                    latitude = lat,
                    longitude = lng,
                    speedKmh = speedKmh,
                    deltaMeters = delta,
                    altitude = loc.altitude,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
        }
        client.setLocationListener(listener)
        client.startLocation()
        awaitClose {
            runCatching { client.stopLocation() }
            runCatching { client.onDestroy() }
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0].toDouble()
    }
}
