package com.honglian.smartcycling.ride

import com.honglian.smartcycling.data.TrackPointEntity
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 一圈(分圈)的统计结果。 */
data class LapSplit(
    /** 圈号,从 1 开始。 */
    val index: Int,
    val distanceKm: Double,
    val durationSec: Long,
    val avgSpeedKmh: Double,
    val elevationGainM: Double,
    /** 该圈平均心率(bpm);该圈无心率数据时为 0。 */
    val avgHeartRateBpm: Double,
    /** 该圈最大心率(bpm);无数据时为 0。 */
    val maxHeartRateBpm: Int,
    /** false 表示这是骑行结束时尚未骑满的"进行中"尾圈。 */
    val isComplete: Boolean,
)

/**
 * 分圈推导。
 *
 * 第一性原理 —— 为什么不建 `laps` 表?
 * 每一圈的边界完全由"轨迹点 + 分圈距离"决定,是**可重算的派生数据**,不是独立事实。
 * 把它物化成表会带来三处一致性负担(写入时机 / 迁移 / 删除级联),却没有换来任何
 * 无法从轨迹点算出的信息。因此只在 rides 上存一个 `lapDistanceM`(记录当时用的阈值),
 * 分圈本身一律现算 —— 好处是**历史记录也能立刻显示分圈**,无需补数据。
 *
 * 唯一的取舍:分圈依据是 GPS 轨迹几何,而非轮速计程。GPS 长时间失锁的那一段
 * (如长隧道)不会产生轨迹点,该段的距离与时长都不会计入任何一圈。
 */
object Laps {

    /** 超过该间隔视为"中途暂停/GPS 失锁",不计入圈的用时。 */
    private const val MAX_SEGMENT_MS = 10_000L

    /** 爬升噪声阈值(米),与 RideViewModel 主逻辑保持一致。 */
    private const val ELEVATION_THRESHOLD_M = 0.5

    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * 把轨迹点切成若干圈。
     *
     * @param lapDistanceM 分圈距离(米);≤0 表示未开启分圈,返回空列表。
     * @return 按圈号升序的列表;最后一个元素的 [LapSplit.isComplete] 可能为 false。
     */
    fun split(points: List<TrackPointEntity>, lapDistanceM: Double): List<LapSplit> {
        if (lapDistanceM <= 0.0 || points.size < 2) return emptyList()

        val laps = mutableListOf<LapSplit>()
        var index = 1
        var lapMeters = 0.0
        // 注意单位:时间戳之差是**毫秒**,这里累加的就是毫秒。
        // 结算时再一次性折算成秒与小时 —— 若把毫秒当秒用,均速会算小 1000 倍。
        var lapMillis = 0L
        var lapElevation = 0.0
        var hrSum = 0L
        var hrCount = 0L
        var hrMax = 0
        // 海拔基线:始终是"上一个有效海拔点"。跨圈时**不能**重置回起点海拔 ——
        // 否则新一圈的首个高差会被算成"起点→圈首"的整段落差,凭空多出几百米爬升。
        // 边界点本身就是上一圈的末点,把它留作新圈的基线正是正确行为。
        var lastAltitude: Double? = null

        fun flush(complete: Boolean) {
            val km = lapMeters / 1000.0
            val hours = lapMillis / 3_600_000.0
            laps += LapSplit(
                index = index,
                distanceKm = km,
                durationSec = lapMillis / 1000L,
                avgSpeedKmh = if (hours > 0.0) km / hours else 0.0,
                elevationGainM = lapElevation,
                avgHeartRateBpm = if (hrCount > 0) hrSum.toDouble() / hrCount else 0.0,
                maxHeartRateBpm = hrMax,
                isComplete = complete,
            )
        }

        fun resetLap() {
            index++
            lapMeters = 0.0
            lapMillis = 0L
            lapElevation = 0.0
            hrSum = 0L
            hrCount = 0L
            hrMax = 0
        }

        lastAltitude = points.first().elevationM.takeIf { it != 0.0 }
        // 首个点的心率也要计入它所在的那一圈
        val firstHr = points.first().heartRateBpm
        if (firstHr > 0) {
            hrSum += firstHr
            hrCount++
            hrMax = firstHr
        }

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            lapMeters += haversineMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
            val dt = cur.timestampMs - prev.timestampMs
            if (dt in 1..MAX_SEGMENT_MS) lapMillis += dt

            val alt = cur.elevationM
            if (alt != 0.0) {
                val prevAlt = lastAltitude
                if (prevAlt != null && alt - prevAlt >= ELEVATION_THRESHOLD_M) {
                    lapElevation += alt - prevAlt
                }
                lastAltitude = alt
            }
            val hr = cur.heartRateBpm
            if (hr > 0) {
                hrSum += hr
                hrCount++
                hrMax = max(hrMax, hr)
            }

            if (lapMeters >= lapDistanceM) {
                // 骑满一圈:结算并开新圈。跨过圈界的那一小段(≤1 个采样间隔)不再拆分,
                // 直接归入新圈 —— 拆分的复杂度换不来肉眼可见的精度。
                flush(complete = true)
                resetLap()
            }
        }

        // 尾圈:还没骑满一整圈,或者刚好骑满(此时 lapMeters 已被重置为 0)
        if (lapMeters > 0.0 || laps.isEmpty()) flush(complete = false)

        return laps
    }

    /** 大圆距离(米)。使用 haversine,与坐标系统无关(只看经纬度几何)。 */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
