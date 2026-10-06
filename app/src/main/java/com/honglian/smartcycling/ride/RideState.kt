package com.honglian.smartcycling.ride

/** 速度来源:单个 S314 只能二选一。 */
enum class SpeedSource { SENSOR_WHEEL, GPS }

/** 传感器工作模式:S314 挂在轮上=速度模式;挂在曲柄上=踏频模式。 */
enum class SensorMode { SPEED, CADENCE }

/** 骑行实时状态,驱动数据界面。 */
data class RideState(
    val speedKmh: Double = 0.0,
    val cadenceRpm: Double = 0.0,
    val avgCadenceRpm: Double = 0.0,
    val sensorMode: SensorMode = SensorMode.SPEED,
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val speedSource: SpeedSource = SpeedSource.GPS,
    val calories: Double = 0.0,
    val elevationGainM: Double = 0.0,
    /** 实时心率(bpm);未连接心率带或数据过期时为 0。 */
    val heartRateBpm: Int = 0,
    /** 本次骑行平均心率(bpm);无心率数据时为 0。 */
    val avgHeartRateBpm: Double = 0.0,
    /** 本次骑行最大心率(bpm);无心率数据时为 0。 */
    val maxHeartRateBpm: Int = 0,
    /** 是否正在收到有效心率数据(决定界面是否展示心率行)。 */
    val hasHeartRate: Boolean = false,
    val sensorFresh: Boolean = false,
    val isRiding: Boolean = false,
    val isPaused: Boolean = false,
    /** 本次骑行是否开启自动分圈。 */
    val autoLapEnabled: Boolean = false,
    /** 分圈距离(米);0 表示未开启分圈。 */
    val lapDistanceM: Double = 0.0,
    /** 当前圈号(从 1 开始);未开启分圈时为 0。 */
    val currentLap: Int = 0,
    /** 当前这一圈已骑行的距离(km)。 */
    val lapDistanceKm: Double = 0.0,
    /**
     * 分圈明细(含尚未骑满的尾圈)。
     *
     * 由轨迹点现算(见 [Laps.split]),因此骑行中与结束后落库的结果完全一致。
     */
    val laps: List<LapSplit> = emptyList(),
) {
    val durationText: String
        get() = "%02d:%02d:%02d".format(durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
}

