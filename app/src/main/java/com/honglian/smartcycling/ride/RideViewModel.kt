package com.honglian.smartcycling.ride

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.location.LocationSample
import kotlinx.coroutines.CancellationException
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
 * - 看门狗:任何数据源 3 秒无更新则归零,避免"停车但速度不降"。
 * - 结束骑行:先存本地,再自动上传 Cloudflare 中控(失败不影响本地)。
 *
 * 断点续记(本版新增):
 * 骑行**一开始就落库**一行 `endedAt = 0` 的占位记录,之后每 [PERSIST_INTERVAL_MS] 增量落盘。
 * 若进程被杀(闪退 / 被系统回收),下次启动时这行仍在,可恢复或收尾 ——
 * 核心诉求是"骑了 80 公里,不该因为一次闪退全丢"。
 * 用 `endedAt = 0` 当哨兵值而不是新增一列:既省一次迁移,又天然让"进行中"的记录
 * 与"已完成"的记录共用同一张表与同一套级联删除。
 */
class RideViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container
    private val sensor = container.sensorManager
    private val heartRate = container.heartRateManager
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

    /** 最近一次完成骑行的成绩快照,用于结束后成绩总结页展示。 */
    private val _lastSummary = MutableStateFlow<RideState?>(null)
    val lastSummary: StateFlow<RideState?> = _lastSummary.asStateFlow()

    /** 最近一次落库的骑行 id;用于成绩页一键导出 GPX(此时才拿得到轨迹点)。 */
    private val _lastSavedRideId = MutableStateFlow<Long?>(null)
    val lastSavedRideId: StateFlow<Long?> = _lastSavedRideId.asStateFlow()

    /**
     * 启动时发现的"未收尾骑行"(上次骑行中进程被杀留下的占位记录)。
     * 非 null 时界面应提示用户:继续骑 / 收尾保存 / 丢弃。
     */
    private val _recoverableRide = MutableStateFlow<RideEntity?>(null)
    val recoverableRide: StateFlow<RideEntity?> = _recoverableRide.asStateFlow()

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

    // 训练指标与偏好(startRide 时从设置载入)
    private var elevationGain = 0.0
    private var caloriesKcal = 0.0
    private var lastAltitude: Double? = null
    private var autoPauseEnabled = true
    private var autoPauseThresholdKmh = 1.5
    private var riderWeightKg = 65.0

    // 心率(可选外设):实时值、均值累计与最大值
    private var heartRateBpm = 0
    private var lastHrAt = 0L
    private var hrSum = 0L
    private var hrCount = 0L
    private var maxHeartRate = 0

    // ---------------------------------------------------------------- 断点续记状态
    /** 本次骑行的数据库行 id;为 null 表示占位记录尚未落库(或落库失败)。 */
    private var activeRideId: Long? = null

    /** 已经写进数据库的轨迹点数量,用于"只追加还没写过的点"。 */
    private var persistedPointCount = 0

    /** 本次骑行的自动分圈距离(米);0 = 未开启。 */
    private var lapDistanceM = 0.0

    private var lastGpsAt = 0L

    // ================================================================== 对外操作

    fun startRide() {
        if (_state.value.isRiding) return
        resetAccumulators()
        autoPauseEnabled = settings.autoPauseEnabled
        autoPauseThresholdKmh = settings.autoPauseThresholdKmh.toDouble()
        riderWeightKg = settings.riderWeightKg.toDouble()
        lapDistanceM = if (settings.autoLapEnabled) settings.autoLapDistanceKm * 1000.0 else 0.0
        trackPoints.clear()
        _traveledPath.value = emptyList()
        _state.value = RideState(
            isRiding = true,
            isPaused = false,
            autoLapEnabled = lapDistanceM > 0.0,
            lapDistanceM = lapDistanceM,
            currentLap = if (lapDistanceM > 0.0) 1 else 0,
        )

        // 先落一行 endedAt = 0 的占位记录:进程一旦被杀,这行就是恢复的锚点。
        viewModelScope.launch {
            runCatching {
                activeRideId = repository.beginRide(
                    RideEntity(
                        startedAt = startTime,
                        endedAt = 0L,
                        durationSec = 0L,
                        distanceKm = 0.0,
                        avgSpeedKmh = 0.0,
                        maxSpeedKmh = 0.0,
                        avgCadenceRpm = 0.0,
                        calories = 0.0,
                        elevationGainM = 0.0,
                        avgHeartRateBpm = 0.0,
                        maxHeartRateBpm = 0,
                        lapDistanceM = lapDistanceM,
                    ),
                )
            }
        }

        launchCollectors()
    }

    private fun resetAccumulators() {
        startTime = System.currentTimeMillis()
        distanceMeters = 0.0
        cadenceSum = 0.0
        cadenceCount = 0L
        cadence = 0.0
        sensorSpeed = 0.0
        lastSensorSpeedAt = 0L
        lastCadenceAt = 0L
        lastGpsSpeed = 0.0
        sensorMode = SensorMode.SPEED
        lastActiveAt = System.currentTimeMillis()
        accumulatedDurationSec = 0L
        elevationGain = 0.0
        caloriesKcal = 0.0
        lastAltitude = null
        heartRateBpm = 0
        lastHrAt = 0L
        hrSum = 0L
        hrCount = 0L
        maxHeartRate = 0
        activeRideId = null
        persistedPointCount = 0
        lastGpsAt = 0L
    }

    /**
     * 启动四个采集协程 + 一个落盘协程。
     *
     * 采集协程兜底:任一数据源(传感器/GPS/心率/计时)抛异常都只在本协程内消化,
     * 绝不冒泡到 viewModelScope 触发未捕获异常导致整个 App 闪退。
     */
    private fun launchCollectors() {
        rideJob = viewModelScope.launch {
            launch { try { collectSensor() } catch (c: CancellationException) { throw c } catch (t: Throwable) {} }
            launch { try { collectHeartRate() } catch (c: CancellationException) { throw c } catch (t: Throwable) {} }
            launch { try { collectLocation() } catch (c: CancellationException) { throw c } catch (t: Throwable) {} }
            launch { try { ticker() } catch (c: CancellationException) { throw c } catch (t: Throwable) {} }
            launch { try { persistLoop() } catch (c: CancellationException) { throw c } catch (t: Throwable) {} }
        }
    }

    // ================================================================== 采集

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
            distanceMeters += sample.deltaMeters
            // 跳点 / 低精度点:仅用于保活 GPS 看门狗,不写入轨迹、不移动车标、不累计爬升 ——
            // 否则隧道/高架的漂移点会在历史轨迹上留下一段明显的尖刺。
            if (!sample.isReliable) return@collect
            val here = LatLng(sample.latitude, sample.longitude)
            _currentLatLng.value = here
            // 追加到已走轨迹(上限保护,防超长骑行内存膨胀)
            val prevPath = _traveledPath.value
            _traveledPath.value = if (prevPath.size >= 8000) prevPath.drop(1) + here else prevPath + here
            // 累计爬升(GPS 高程,+0.5m 阈值过滤噪声)
            val alt = sample.altitude
            if (alt != 0.0) {
                val prevAlt = lastAltitude
                if (prevAlt != null && alt - prevAlt >= 0.5) elevationGain += (alt - prevAlt)
                lastAltitude = alt
            }
            // 逐点心率:分圈心率的前提(轨迹点没有心率,分圈就只能给整段平均值)。
            val hrNow = if (lastHrAt > 0L && System.currentTimeMillis() - lastHrAt < HR_STALE_MS) heartRateBpm else 0
            trackPoints += TrackPointEntity(
                rideId = 0,
                latitude = sample.latitude,
                longitude = sample.longitude,
                speedKmh = sample.speedKmh,
                timestampMs = sample.timestampMs,
                elevationM = if (alt == 0.0) 0.0 else alt,
                heartRateBpm = hrNow,
            )
        }
    }

    /**
     * 采集标准 BLE 心率带读数。
     * 心率带是可选外设:未连接时该流恒为 0,不会影响任何其它指标。
     */
    private suspend fun collectHeartRate() {
        heartRate.bpm.collect { bpm ->
            if (_state.value.isPaused) return@collect
            if (bpm <= 0) return@collect
            heartRateBpm = bpm
            lastHrAt = System.currentTimeMillis()
            if (bpm > maxHeartRate) maxHeartRate = bpm
            hrSum += bpm
            hrCount++
        }
    }

    private fun hasFreshGps() = System.currentTimeMillis() - lastGpsAt < STALE_MS

    /** 按骑行速度估算 MET(代谢当量),用于卡路里估算。 */
    private fun metForSpeed(kmh: Double): Double = when {
        kmh < 16.0 -> 4.0
        kmh < 19.0 -> 6.8
        kmh < 22.0 -> 8.0
        kmh < 25.0 -> 10.0
        kmh < 30.0 -> 12.0
        else -> 15.8
    }

    fun togglePause() {
        val s = _state.value
        if (!s.isRiding) return
        val targetPaused = !s.isPaused
        _state.value = s.copy(isPaused = targetPaused)
        if (!targetPaused) {
            lastActiveAt = System.currentTimeMillis()
        }
    }

    // ================================================================== 主循环

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

            // 自动暂停判定：可配置阈值/开关;静止超 5 秒自动暂停,移动自动恢复
            val hasMotion = rawSpeed > autoPauseThresholdKmh
            var nextPaused = stateVal.isPaused

            if (hasMotion) {
                lastActiveAt = now
                if (stateVal.isPaused && autoPauseEnabled) {
                    nextPaused = false // 自动恢复
                }
            } else {
                if (autoPauseEnabled && !stateVal.isPaused && (now - lastActiveAt >= 5000L)) {
                    nextPaused = true
                }
            }

            if (!nextPaused) {
                accumulatedDurationSec++
                // 卡路里累计(MET × 体重 × 3.5 / 200 kcal/min,按秒累加)
                caloriesKcal += metForSpeed(rawSpeed) * riderWeightKg * 3.5 / 200.0 / 60.0
            }

            val curCadence = if (now - lastCadenceAt < STALE_MS) cadence else 0.0
            val avgCadence = if (cadenceCount > 0) cadenceSum / cadenceCount else 0.0
            val distKm = distanceMeters / 1000.0
            val avg = if (accumulatedDurationSec > 0) distKm / (accumulatedDurationSec / 3600.0) else 0.0

            // 心率:与其它传感器一致做"陈旧检测",掉线后自动归零,避免读数卡在最后一次值。
            val hrFresh = lastHrAt > 0L && now - lastHrAt < HR_STALE_MS
            val curHr = if (hrFresh && !nextPaused) heartRateBpm else 0
            val avgHr = if (hrCount > 0) hrSum.toDouble() / hrCount else 0.0

            // 分圈:由轨迹点现算,保证"骑行中"与"落库后"看到的是同一套结果。
            // 未开启分圈时 lapDistanceM = 0,split() 会立即返回,零开销。
            val laps = if (lapDistanceM > 0.0) Laps.split(trackPoints, lapDistanceM) else emptyList()

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
                calories = caloriesKcal,
                elevationGainM = elevationGain,
                heartRateBpm = curHr,
                avgHeartRateBpm = avgHr,
                maxHeartRateBpm = maxHeartRate,
                hasHeartRate = hrFresh,
                sensorFresh = sensorFresh,
                isPaused = nextPaused,
                autoLapEnabled = lapDistanceM > 0.0,
                lapDistanceM = lapDistanceM,
                currentLap = if (lapDistanceM > 0.0) maxOf(1, laps.size) else 0,
                lapDistanceKm = laps.lastOrNull()?.distanceKm ?: 0.0,
                laps = laps,
            )
        }
    }

    // ================================================================== 断点续记

    /** 周期性把"还没落盘的轨迹点"与最新成绩写进那条占位记录。 */
    private suspend fun persistLoop() {
        while (true) {
            delay(PERSIST_INTERVAL_MS)
            persistProgress()
        }
    }

    private suspend fun persistProgress() {
        val id = activeRideId ?: return
        val s = _state.value
        if (!s.isRiding) return
        runCatching {
            // 1) 只追加"还没写过"的轨迹点
            val newPoints = trackPoints.drop(persistedPointCount)
            if (newPoints.isNotEmpty()) {
                repository.appendTrackPoints(newPoints.map { it.copy(rideId = id) })
                // 用 += 而不是 = trackPoints.size:期间可能有新点写入,
                // 直接取 size 会把"没写进去的点"也标记成已落盘,造成丢点。
                persistedPointCount += newPoints.size
            }
            // 2) 更新占位记录的成绩字段(endedAt 保持 0 = 仍在进行中)
            repository.updateRide(
                RideEntity(
                    id = id,
                    startedAt = startTime,
                    endedAt = 0L,
                    durationSec = s.durationSec,
                    distanceKm = s.distanceKm,
                    avgSpeedKmh = s.avgSpeedKmh,
                    maxSpeedKmh = s.maxSpeedKmh,
                    avgCadenceRpm = s.avgCadenceRpm,
                    calories = s.calories,
                    elevationGainM = s.elevationGainM,
                    avgHeartRateBpm = s.avgHeartRateBpm,
                    maxHeartRateBpm = s.maxHeartRateBpm,
                    lapDistanceM = lapDistanceM,
                ),
            )
        }
    }

    // ================================================================== 恢复

    /** 启动时查一次:上次是否有没来得及收尾的骑行。 */
    fun checkRecoverableRide() {
        if (_state.value.isRiding) return
        viewModelScope.launch {
            runCatching { repository.unfinishedRide() }.getOrNull()?.let { _recoverableRide.value = it }
        }
    }

    /** 丢弃这条残留记录(含其轨迹点,靠外键级联删除)。 */
    fun discardRecoverableRide() {
        val ride = _recoverableRide.value ?: return
        _recoverableRide.value = null
        viewModelScope.launch { runCatching { repository.deleteRide(ride.id) } }
    }

    /** 把残留记录直接收尾保存为一次完整骑行(用户不想续骑时)。 */
    fun finalizeRecoverableRide() {
        val ride = _recoverableRide.value ?: return
        _recoverableRide.value = null
        viewModelScope.launch {
            runCatching { repository.updateRide(ride.copy(endedAt = System.currentTimeMillis())) }
        }
    }

    /**
     * 继续骑这条残留记录。
     *
     * 可还原的量全部从**已落盘的轨迹点**反推(距离/爬升/心率均值),而不是猜:
     * 逐点心率与海拔都在库里,所以续骑后的平均值不会突然跳变。
     * 唯一无法逐点还原的是踏频(轨迹点未存踏频),用占位记录的整段均值近似顶上。
     */
    fun resumeRecoverableRide() {
        val ride = _recoverableRide.value ?: return
        _recoverableRide.value = null
        viewModelScope.launch {
            val points = runCatching { repository.trackPoints(ride.id) }.getOrDefault(emptyList())

            startTime = ride.startedAt
            distanceMeters = ride.distanceKm * 1000.0
            accumulatedDurationSec = ride.durationSec
            caloriesKcal = ride.calories
            elevationGain = ride.elevationGainM
            maxHeartRate = ride.maxHeartRateBpm
            autoPauseEnabled = settings.autoPauseEnabled
            autoPauseThresholdKmh = settings.autoPauseThresholdKmh.toDouble()
            riderWeightKg = settings.riderWeightKg.toDouble()
            lapDistanceM = ride.lapDistanceM

            // 心率均值/计数由逐点心率精确还原
            val hrs = points.map { it.heartRateBpm }.filter { it > 0 }
            hrSum = hrs.sumOf { it.toLong() }
            hrCount = hrs.size.toLong()
            heartRateBpm = 0
            lastHrAt = 0L

            // 踏频无法逐点还原:用整段均值作为"一个样本"顶上,避免续骑后均值塌到 0
            cadenceSum = ride.avgCadenceRpm
            cadenceCount = if (ride.avgCadenceRpm > 0.0) 1L else 0L
            cadence = 0.0
            sensorSpeed = 0.0
            sensorMode = SensorMode.SPEED
            lastSensorSpeedAt = 0L
            lastCadenceAt = 0L
            lastGpsSpeed = 0.0
            lastGpsAt = 0L
            lastAltitude = points.lastOrNull()?.elevationM?.takeIf { it != 0.0 }
            lastActiveAt = System.currentTimeMillis()

            trackPoints.clear()
            trackPoints.addAll(points)
            persistedPointCount = points.size
            activeRideId = ride.id
            _traveledPath.value = points.map { LatLng(it.latitude, it.longitude) }
            _currentLatLng.value = _traveledPath.value.lastOrNull()

            val laps = if (lapDistanceM > 0.0) Laps.split(points, lapDistanceM) else emptyList()
            _state.value = RideState(
                isRiding = true,
                isPaused = false,
                distanceKm = ride.distanceKm,
                durationSec = ride.durationSec,
                avgSpeedKmh = ride.avgSpeedKmh,
                maxSpeedKmh = ride.maxSpeedKmh,
                calories = ride.calories,
                elevationGainM = ride.elevationGainM,
                avgHeartRateBpm = ride.avgHeartRateBpm,
                maxHeartRateBpm = ride.maxHeartRateBpm,
                autoLapEnabled = lapDistanceM > 0.0,
                lapDistanceM = lapDistanceM,
                currentLap = if (lapDistanceM > 0.0) maxOf(1, laps.size) else 0,
                lapDistanceKm = laps.lastOrNull()?.distanceKm ?: 0.0,
                laps = laps,
            )
            launchCollectors()
        }
    }

    // ================================================================== 结束

    fun stopRide(onSaved: (Long) -> Unit = {}) {
        rideJob?.cancel()
        rideJob = null
        val s = _state.value
        // 结束时把分圈定稿(与骑行中每秒算的是同一个函数,结果一致)
        val laps = if (lapDistanceM > 0.0) Laps.split(trackPoints.toList(), lapDistanceM) else emptyList()
        _state.value = s.copy(isRiding = false, isPaused = false, laps = laps)
        _lastSavedRideId.value = null
        // 捕获成绩快照(时长过短视为误触发,不生成成绩)
        _lastSummary.value = if (s.durationSec < 3) null else _state.value
        val pointsSnapshot = trackPoints.toList()
        val rideId = activeRideId
        activeRideId = null

        viewModelScope.launch {
            if (s.durationSec < 3) {
                // 误触发:连同占位记录一起删掉,避免历史里留下一条空记录
                rideId?.let { runCatching { repository.deleteRide(it) } }
                return@launch
            }
            val ride = RideEntity(
                id = rideId ?: 0L,
                startedAt = startTime,
                endedAt = System.currentTimeMillis(),
                durationSec = s.durationSec,
                distanceKm = s.distanceKm,
                avgSpeedKmh = s.avgSpeedKmh,
                maxSpeedKmh = s.maxSpeedKmh,
                avgCadenceRpm = s.avgCadenceRpm,
                calories = s.calories,
                elevationGainM = s.elevationGainM,
                avgHeartRateBpm = s.avgHeartRateBpm,
                maxHeartRateBpm = s.maxHeartRateBpm,
                lapDistanceM = lapDistanceM,
            )
            val id = if (rideId != null) {
                // 正常路径:占位记录已存在 → 补写剩余轨迹点 + 更新为最终成绩
                val remaining = pointsSnapshot.drop(persistedPointCount)
                if (remaining.isNotEmpty()) {
                    runCatching { repository.appendTrackPoints(remaining.map { it.copy(rideId = rideId) }) }
                }
                runCatching { repository.updateRide(ride) }
                rideId
            } else {
                // 兜底:占位记录没建成(极端情况),退化成一次性写入
                repository.saveRide(ride.copy(id = 0L), pointsSnapshot)
            }
            _lastSavedRideId.value = id
            // 自动上传云端中控(仅本地模式下不联网上传)
            if (!settings.localOnlyMode) runCatching {
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
        /** 心率带心跳间隔较长,用更宽松的陈旧阈值(5s),避免正常漏帧被误判为掉线。 */
        private const val HR_STALE_MS = 5000L
        /** 断点续记的落盘周期。10 秒 = 最坏情况丢 10 秒数据,而写入开销可忽略。 */
        private const val PERSIST_INTERVAL_MS = 10_000L
    }
}
