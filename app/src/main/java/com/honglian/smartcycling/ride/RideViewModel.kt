package com.honglian.smartcycling.ride

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.location.LocationSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 骑行中枢:合并传感器与 GPS 数据流,输出 [RideState]。
 *
 * 第一性原理决策:
 * - 速度来源自适应:若 3 秒内收到轮转传感器速度则用传感器,否则回退 GPS。
 * - 里程优先用 GPS 积分(精度高);无定位时用轮转圈数 × 轮周长回退。
 * - 看门狗:任何数据源 3 秒无更新则归零,避免“停车但速度不降”。
 * - 结束骑行:先存本地,再自动上传 Cloudflare 中控(失败不影响本地)。
 */
class RideViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container
    private val sensor = container.sensorManager
    private val location = container.locationTracker
    private val repository = container.rideRepository
    private val cloudSync = container.cloudSyncRepository
    private val settings = container.settings

    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    /** 当前真实定位(WGS-84,取自 FusedLocation),用于驱动导航地图跟随真实位置。 */
    private val _currentLatLng = MutableStateFlow<LatLng?>(null)
    val currentLatLng: StateFlow<LatLng?> = _currentLatLng.asStateFlow()

    private var rideJob: Job? = null
    private val trackPoints = mutableListOf<TrackPointEntity>()

    private var startTime = 0L
    private var distanceMeters = 0.0
    private var lastSensorSpeedAt = 0L
    private var lastCadenceAt = 0L
    private var lastGpsSpeed = 0.0
    private var sensorSpeed = 0.0
    private var cadence = 0.0
    private var cadenceSum = 0.0
    private var cadenceCount = 0L
    private var sensorMode = SensorMode.SPEED

    fun startRide() {
        if (_state.value.isRiding) return
        startTime = System.currentTimeMillis()
        distanceMeters = 0.0
        cadenceSum = 0.0
        cadenceCount = 0L
        sensorMode = SensorMode.SPEED
        trackPoints.clear()
        _state.value = RideState(isRiding = true)

        rideJob = viewModelScope.launch {
            launch { collectSensor() }
            launch { collectLocation() }
            launch { ticker() }
        }
    }

    private suspend fun collectSensor() {
        sensor.readings.collect { r ->
            val now = System.currentTimeMillis()
            // 识别传感器模式:含轮转=速度模式;含曲柄=踏频模式(S314 二选一)
            r.speedKmh?.let { sensorSpeed = it; lastSensorSpeedAt = now; sensorMode = SensorMode.SPEED }
            r.cadenceRpm?.let {
                cadence = it; lastCadenceAt = now; sensorMode = SensorMode.CADENCE
                if (it > 0.0) { cadenceSum += it; cadenceCount++ }
            }
            // 无 GPS 时用轮转圈数回退测距
            if (r.wheelDeltaRevs > 0 && !hasFreshGps()) {
                distanceMeters += r.wheelDeltaRevs * sensor.wheelCircumferenceM
            }
        }
    }

    private suspend fun collectLocation() {
        location.track().collect { sample: LocationSample ->
            lastGpsSpeed = sample.speedKmh
            lastGpsAt = System.currentTimeMillis()
            _currentLatLng.value = LatLng(sample.latitude, sample.longitude)
            distanceMeters += sample.deltaMeters
            trackPoints += TrackPointEntity(
                rideId = 0,
                latitude = sample.latitude,
                longitude = sample.longitude,
                speedKmh = sample.speedKmh,
                timestampMs = sample.timestampMs,
            )
        }
    }

    private var lastGpsAt = 0L
    private fun hasFreshGps() = System.currentTimeMillis() - lastGpsAt < STALE_MS

    private suspend fun ticker() {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val dur = (now - startTime) / 1000

            val sensorFresh = now - lastSensorSpeedAt < STALE_MS
            val useSensor = sensorFresh
            val speed = when {
                useSensor -> sensorSpeed
                now - lastGpsAt < STALE_MS -> lastGpsSpeed
                else -> 0.0
            }
            val curCadence = if (now - lastCadenceAt < STALE_MS) cadence else 0.0
            val avgCadence = if (cadenceCount > 0) cadenceSum / cadenceCount else 0.0
            val distKm = distanceMeters / 1000.0
            val avg = if (dur > 0) distKm / (dur / 3600.0) else 0.0

            _state.value = _state.value.copy(
                speedKmh = speed,
                cadenceRpm = curCadence,
                avgCadenceRpm = avgCadence,
                sensorMode = sensorMode,
                distanceKm = distKm,
                durationSec = dur,
                avgSpeedKmh = avg,
                maxSpeedKmh = maxOf(_state.value.maxSpeedKmh, speed),
                speedSource = if (useSensor) SpeedSource.SENSOR_WHEEL else SpeedSource.GPS,
            )
        }
    }

    fun stopRide(onSaved: (Long) -> Unit = {}) {
        rideJob?.cancel()
        rideJob = null
        val s = _state.value
        _state.value = s.copy(isRiding = false)
        if (s.durationSec < 3) return  // 忽略误触发
        val pointsSnapshot = trackPoints.toList()
        viewModelScope.launch {
            val ride = RideEntity(
                startedAt = startTime,
                endedAt = System.currentTimeMillis(),
                durationSec = s.durationSec,
                distanceKm = s.distanceKm,
                avgSpeedKmh = s.avgSpeedKmh,
                maxSpeedKmh = s.maxSpeedKmh,
                avgCadenceRpm = s.avgCadenceRpm,
            )
            val id = repository.saveRide(ride, pointsSnapshot)
            // 自动上传云端中控(未配置或失败均不影响本地)
            runCatching {
                cloudSync.upload(
                    deviceId = settings.deviceId,
                    rider = settings.riderName,
                    ride = ride.copy(id = id),
                    points = pointsSnapshot,
                )
            }
            onSaved(id)
        }
    }

    companion object {
        private const val STALE_MS = 3000L
    }
}
