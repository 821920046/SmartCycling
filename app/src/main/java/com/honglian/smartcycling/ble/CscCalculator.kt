package com.honglian.smartcycling.ble

/**
 * 由相邻两帧 CSC 数据计算速度与踏频。
 *
 * 关键点(第一性原理):
 * - 时间戳以 1/1024 秒为单位,且 uint16 会在 65536 处翻转,必须取模修正。
 * - dt 为 0 表示传感器未上报新数据(非运动),保持上一速度。
 * - 轮转增量为 0 表示已停止,速度归零。
 */
class CscCalculator(
    /** 轮周长(米)。700x25C 约 2.105m,可在设置页校准。 */
    var wheelCircumferenceM: Double = 2.105,
) {
    private var last: CscData? = null
    private var lastSpeed = 0.0

    fun reset() {
        last = null
        lastSpeed = 0.0
    }

    fun update(cur: CscData): SensorReading {
        val prev = last
        last = cur
        if (prev == null) return SensorReading(timestampMs = System.currentTimeMillis())

        var speed: Double? = null
        var cadence: Double? = null
        var wheelDelta = 0L

        if (cur.wheelRevs != null && prev.wheelRevs != null &&
            cur.wheelTime != null && prev.wheelTime != null
        ) {
            wheelDelta = cur.wheelRevs - prev.wheelRevs
            val dt = tsDelta(cur.wheelTime, prev.wheelTime) / 1024.0
            speed = when {
                dt <= 0.0 -> lastSpeed          // 无新帧,维持
                wheelDelta <= 0L -> 0.0         // 已停止
                else -> wheelDelta * wheelCircumferenceM / dt * 3.6
            }
            lastSpeed = speed
        }

        if (cur.crankRevs != null && prev.crankRevs != null &&
            cur.crankTime != null && prev.crankTime != null
        ) {
            val dRev = (cur.crankRevs - prev.crankRevs) and 0xFFFF
            val dt = tsDelta(cur.crankTime, prev.crankTime) / 1024.0
            cadence = if (dt > 0.0 && dRev > 0) dRev / dt * 60.0 else 0.0
        }

        return SensorReading(
            speedKmh = speed,
            cadenceRpm = cadence,
            wheelDeltaRevs = if (wheelDelta > 0) wheelDelta else 0L,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /** uint16 时间戳翻转修正。 */
    private fun tsDelta(cur: Int, prev: Int): Int = (cur - prev) and 0xFFFF
}
