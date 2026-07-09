package com.honglian.smartcycling.ble

/**
 * 由相邻两帧 CSC 数据计算速度与踏频。
 *
 * 关键点(第一性原理):
 * - 时间戳以 1/1024 秒为单位,且 uint16 会在 65536 处翻转,必须取模修正。
 * - 轮子停转后传感器仍会周期性发帧,但累计圈数/时间戳冻结(dt=0)。
 *   此时不能一直“维持上一速度”,否则停车后速度不归零。
 *   因此加入停转看门狗:超过 STALL_MS 无新增转动即归零。
 */
class CscCalculator(
    /** 轮周长(米)。700x25C 约 2.105m,可在设置页校准。 */
    var wheelCircumferenceM: Double = 2.105,
) {
    private var last: CscData? = null
    private var lastSpeed = 0.0
    private var lastWheelMoveMs = 0L

    fun reset() {
        last = null
        lastSpeed = 0.0
        lastWheelMoveMs = 0L
    }

    fun update(cur: CscData): SensorReading {
        val now = System.currentTimeMillis()
        val prev = last
        last = cur
        if (prev == null) {
            lastWheelMoveMs = now
            return SensorReading(timestampMs = now)
        }

        var speed: Double? = null
        var cadence: Double? = null
        var wheelDelta = 0L

        if (cur.wheelRevs != null && prev.wheelRevs != null &&
            cur.wheelTime != null && prev.wheelTime != null
        ) {
            wheelDelta = cur.wheelRevs - prev.wheelRevs
            val dt = tsDelta(cur.wheelTime, prev.wheelTime) / 1024.0
            speed = if (wheelDelta > 0L && dt > 0.0) {
                lastWheelMoveMs = now
                lastSpeed = wheelDelta * wheelCircumferenceM / dt * 3.6
                lastSpeed
            } else {
                // 轮子未转动(dt=0 或 圈数未增):短暂维持,超过停转阈值即归零
                if (now - lastWheelMoveMs > STALL_MS) lastSpeed = 0.0
                lastSpeed
            }
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
            timestampMs = now,
        )
    }

    /** uint16 时间戳翻转修正。 */
    private fun tsDelta(cur: Int, prev: Int): Int = (cur - prev) and 0xFFFF

    companion object {
        /** 停转归零阈值(毫秒):避免低速时因帧间 dt=0 而频繁闪烁。 */
        private const val STALL_MS = 2500L
    }
}
