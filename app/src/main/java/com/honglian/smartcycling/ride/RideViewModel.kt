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

    /** 当前真实定位(GCJ-02,取自高德 AMapLocation),用于语音诱导喂数与真实位置跟随。 */
    private val _currentLatLng = MutableStateFlow<LatLng?>(null)
    val currentLatLng: StateFlow<LatLng?> = _currentLatLng.asStateFlow()

    /** 已骑行轨迹(GCJ-02),用于在地图上实时回放“走过的路”。 */
    private val _traveledPath = MutableStateFlow<List<LatLng>>(emptyList())
    val traveledPath: StateFlow<List<LatLng>> = _traveledPath.asStateFlow()

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

    // 自动暂停与滤波状态量
    private var lastActiveAt = 0L
    private var accumulatedDurationSec = 0L

    fun startRide() {
        if (_state.value.isRiding) return
        startTime = System.currentTimeMillis()
        distanceMeters = 0.0
        cadenceSum = 0.0
        cadenceCount = 0L
        sensorMode = SensorMode.SPEED
        lastActiveAt = System.currentTimeMillis()
        accumulatedDurationSec = 0L
        trackPoints.clear()
        _traveledPath.value = emptyList()
        _state.value = RideState(isRiding = true, isPaused = false)

        rideJob = viewModelScope.launch {
            launch { collectSensor() }
            launch { collectLocation() }
            launch { ticker() }
        }
    }

    private suspend fun collectSensor() {
        sensor.readings.collect { r ->
            if (_state.value.isPaused) return@collect
            val now = System.currentTimeMillis()
            // 识别传感器模式:含轮转=速度模式;含曲柄=踏频模式(S314 二选一)
            r.speedKmh?.let { raw ->
                // EMA 滤波: 0.4 * new + 0.6 * prev
                sensorSpeed = 0.4 * raw + 0.6 * sensorSpeed
                lastSensorSpeedAt = now
                sensorMode = SensorMode.SPEED
            }
            r.cadenceRpm?.let { raw ->
                cadence = 0.4 * raw + 0.6 * cadence
                lastCadenceAt = now
                sensorMode = SensorMode.CADENCE
                if (raw > 0.0) {
                    cadenceSum += raw
                    cadenceCount++
                }
            }
            // 无 GPS 时用轮转圈数回退测距 (仅在运动状态)
            if (r.wheelDeltaRevs > 0 && !hasFreshGps() && !_state.value.isPaused) {
                distanceMeters += r.wheelDeltaRevs * sensor.wheelCircumferenceM
            }
        }
    }

    private suspend fun collectLocation() {
        location.track().collect { sample: LocationSample ->
            if (_state.value.isPaused) return@collect
            lastGpsSpeed = sample.speedKmh
            lastGpsAt = System.currentTimeMillis()
            val here = LatLng(sample.latitude, sample.longitude)
            _currentLatLng.value = here
            // 追加到已走轨迹(上限保护,防超长骑行内存膨胀)
            val prevPath = _traveledPath.value
            _traveledPath.value = if (prevPath.size >= 8000) prevPath.drop(1) + here else prevPath + here
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

    fun togglePause() {
        val s = _state.value
        if (!s.isRiding) return
        val targetPaused = !s.isPaused
        _state.value = s.copy(isPaused = targetPaused)
        if (!targetPaused) {
            lastActiveAt = System.currentTimeMillis()
        }
    }

    private suspend fun ticker() {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val stateVal = _state.value
            if (!stateVal.isRiding) continue

            val sensorFresh = now - lastSensorSpeedAt < STALE_MS
            val useSensor = sensorFresh
            val rawSpeed = when {
                useSensor -> sensorSpeed
                now - lastGpsAt < STALE_MS -> lastGpsSpeed
                else -> 0.0
            }

            // 自动暂停判定：时速大于 1.5 km/h 判定为运动，否则为静止
            val hasMotion = rawSpeed > 1.5
            var nextPaused = stateVal.isPaused

            if (hasMotion) {
                lastActiveAt = now
                if (stateVal.isPaused) {
                    nextPaused = false // 自动恢复
                }
            } else {
                // 静止状态下，超过 5 秒未移动，自动暂停
                if (!stateVal.isPaused && (now - lastActiveAt >= 5000L)) {
                    nextPaused = true
                }
            }

            if (!nextPaused) {
                accumulatedDurationSec++
            }

            val curCadence = if (now - lastCadenceAt < STALE_MS) cadence else 0.0
            val avgCadence = if (cadenceCount > 0) cadenceSum / cadenceCount else 0.0
            val distKm = distanceMeters / 1000.0
            val avg = if (accumulatedDurationSec > 0) distKm / (accumulatedDurationSec / 3600.0) else 0.0

            _state.value = stateVal.copy(
                speedKmh = if (nextPaused) 0.0 else rawSpeed,
                cadenceRpm = if (nextPaused) 0.0 else curCadence,
                avgCadenceRpm = avgCadence,
                sensorMode = sensorMode,
                distanceKm = distKm,
                durationSec = accumulatedDurationSec,
                avgSpeedKmh = avg,
                maxSpeedKmh = maxOf(stateVal.maxSpeedKmh, rawSpeed),
                speedSource = if (useSensor) SpeedSource.SENSOR_WHEEL else SpeedSource.GPS,
                isPaused = nextPaused
            )
        }
    }

    fun stopRide(onSaved: (Long) -> Unit = {}) {
        rideJob?.cancel()
        rideJob = null
        val s = _state.value
        _state.value = s.copy(isRiding = false, isPaused = false)
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
            // 自动上传云端中控
            runCatching {
                cloudSync.upload(
                    deviceId = settings.deviceId,
                    rider = settings.riderName,
                    ride = ride.copy(id = id),
                    points = pointsSnapshot,
                    customUrl = settings.cloudSyncUrl,
                    customToken = settings.cloudSyncToken,
                )
            }
            onSaved(id)
        }
    }

    companion object {
        private const val STALE_MS = 3000L
    }
}

