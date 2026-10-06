package com.honglian.smartcycling.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CSC(骑行速度/踏频)计算回归测试。
 *
 * 重点锁死两个"真实设备上才会暴露"的坑:
 *  1. uint16 时间戳/圈数在 65536 处**翻转** —— 不修正就会算出负 dt 或天量速度;
 *  2. 首帧没有前值,必须返回空而不是伪造 0。
 */
class CscCalculatorTest {

    /** 307 / 1024 秒 ≈ 0.2998s,恰好一圈 2.0m 轮周长 → 约 24.0 km/h。 */
    private val dtTicks = 307
    private val expectedSpeed = 1.0 * 2.0 / (dtTicks / 1024.0) * 3.6

    @Test
    fun `首帧不产生速度与踏频`() {
        val calc = CscCalculator(wheelCircumferenceM = 2.0)
        val r = calc.update(CscData(wheelRevs = 100L, wheelTime = 0, crankRevs = 10, crankTime = 0))
        assertNull(r.speedKmh)
        assertNull(r.cadenceRpm)
        assertEquals(0L, r.wheelDeltaRevs)
    }

    @Test
    fun `常规两帧可算出速度与新增圈数`() {
        val calc = CscCalculator(wheelCircumferenceM = 2.0)
        calc.update(CscData(wheelRevs = 100L, wheelTime = 0))
        val r = calc.update(CscData(wheelRevs = 101L, wheelTime = dtTicks))
        assertEquals(expectedSpeed, r.speedKmh!!, 0.05)
        assertEquals(1L, r.wheelDeltaRevs)
    }

    @Test
    fun `时间戳 65536 翻转不会算出异常速度`() {
        val calc = CscCalculator(wheelCircumferenceM = 2.0)
        val prevTicks = 65530
        calc.update(CscData(wheelRevs = 10L, wheelTime = prevTicks))
        // 65530 + 307 = 65837 → 溢出回绕到 301
        val curTicks = (prevTicks + dtTicks) and 0xFFFF
        assertEquals(301, curTicks)
        val r = calc.update(CscData(wheelRevs = 11L, wheelTime = curTicks))
        // 若不修正翻转,dt 会变成负数或约 -65 秒,速度会离谱;修正后应与常规帧一致。
        assertEquals(expectedSpeed, r.speedKmh!!, 0.05)
        assertEquals(1L, r.wheelDeltaRevs)
    }

    @Test
    fun `踏频圈数 65535 到 2 的翻转按 3 圈计`() {
        val calc = CscCalculator()
        calc.update(CscData(crankRevs = 65535, crankTime = 0))
        val r = calc.update(CscData(crankRevs = 2, crankTime = 1024))
        // dRev = (2 - 65535) and 0xFFFF = 3 ;dt = 1.0s → 3 圈/秒 = 180 rpm
        assertEquals(180.0, r.cadenceRpm!!, 1e-6)
    }

    @Test
    fun `仅踏频帧不产生轮转增量`() {
        val calc = CscCalculator()
        calc.update(CscData(crankRevs = 10, crankTime = 0))
        val r = calc.update(CscData(crankRevs = 12, crankTime = 2048))
        assertEquals(0L, r.wheelDeltaRevs)
        assertNull(r.speedKmh)
    }

    @Test
    fun `轮周长变化线性影响速度`() {
        val a = CscCalculator(wheelCircumferenceM = 2.0)
        a.update(CscData(wheelRevs = 0L, wheelTime = 0))
        val ra = a.update(CscData(wheelRevs = 1L, wheelTime = dtTicks))

        val b = CscCalculator(wheelCircumferenceM = 4.0)
        b.update(CscData(wheelRevs = 0L, wheelTime = 0))
        val rb = b.update(CscData(wheelRevs = 1L, wheelTime = dtTicks))

        assertEquals(ra.speedKmh!! * 2, rb.speedKmh!!, 1e-6)
    }
}
