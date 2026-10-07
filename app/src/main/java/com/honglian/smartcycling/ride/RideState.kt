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
    /**
     * 实时海拔(米,已做显示滤波);null = 尚无有效高程读数。
     *
     * 与 [elevationGainM](累计爬升)是**两个不同的量**:这里是"此刻所处的高度",
     * 那个是"一路上净爬了多少"。界面上必须用不同的标签,不能互相顶替。
     *
     * 用可空类型而不是 0.0 哨兵:"海拔 0 米"是合法读数(海平面、荷兰、部分沿海路段),
     * 若拿 0 表示"未知",两者就再也分不开了 —— 而 GPS 恰好用 0.0 表示"没有高程"。
     */
    val currentAltitudeM: Double? = null,
    /**
     * 水柱量程下限(绝对海拔,米);null = 尚未确定量程(还没拿到第一个高程读数)。
     *
     * 由 [AltitudeGauge.base] 在骑行开始时一次锁定,整段骑行不变。
     */
    val altitudeBaseM: Double? = null,
    /** 海拔趋势:1 上升 / -1 下降 / 0 基本持平(约 15 秒窗口)。 */
    val altitudeTrend: Int = 0,
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

