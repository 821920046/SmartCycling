package com.honglian.smartcycling.ride

/** 速度来源:单个 S314 只能二选一。 */
enum class SpeedSource { SENSOR_WHEEL, GPS }

/** 骑行实时状态,驱动数据界面。 */
data class RideState(
    val speedKmh: Double = 0.0,
    val cadenceRpm: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val speedSource: SpeedSource = SpeedSource.GPS,
    val isRiding: Boolean = false,
) {
    val durationText: String
        get() = "%02d:%02d:%02d".format(durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
}
