package com.honglian.smartcycling.ride

import com.honglian.smartcycling.data.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分圈推导回归测试。
 *
 * 轨迹点全部沿同一经线向北排布(每点纬度 +0.001°),此时 haversine 退化为
 * `R × Δlat(弧度)`,单段长度恒为 6371008.8 × 0.001 × π/180 ≈ 111.195 m,便于手算期望值。
 */
class LapsTest {

    /** 0.001° 纬度对应的米数(与 [Laps.haversineMeters] 的球面模型一致)。 */
    private val metersPerSegment = 111.19508

    /** 单段用时:取 10 秒 —— 恰好落在 [Laps] 的"有效间隔"上界(含),不会被判为失锁。 */
    private val stepMs = 10_000L

    private fun track(
        count: Int,
        heartRates: List<Int>? = null,
        elevations: List<Double>? = null,
        stepMillis: Long = stepMs,
    ): List<TrackPointEntity> = (0 until count).map { i ->
        TrackPointEntity(
            rideId = 1L,
            latitude = 30.0 + i * 0.001,
            longitude = 120.0,
            speedKmh = 0.0,
            timestampMs = i * stepMillis,
            elevationM = elevations?.getOrNull(i) ?: 0.0,
            heartRateBpm = heartRates?.getOrNull(i) ?: 0,
        )
    }

    @Test
    fun `未开启分圈时返回空列表`() {
        assertTrue(Laps.split(track(10), 0.0).isEmpty())
        assertTrue(Laps.split(track(10), -1.0).isEmpty())
    }

    @Test
    fun `少于两个轨迹点无法成圈`() {
        assertTrue(Laps.split(emptyList(), 1000.0).isEmpty())
        assertTrue(Laps.split(track(1), 1000.0).isEmpty())
    }

    @Test
    fun `按距离切圈并保留未骑满的尾圈`() {
        // 11 个点 = 10 段 ≈ 1111.95 m;阈值 250 m → 每 3 段(≈333.59 m)结一圈。
        // 3 段 ×3 次结圈,剩 1 段(≈111.20 m)作为未完成的尾圈。
        val laps = Laps.split(track(11), 250.0)

        assertEquals(4, laps.size)
        assertEquals(listOf(1, 2, 3, 4), laps.map { it.index })

        listOf(0, 1, 2).forEach { i ->
            assertEquals("第 ${i + 1} 圈距离", 3 * metersPerSegment / 1000.0, laps[i].distanceKm, 1e-4)
            assertTrue("第 ${i + 1} 圈应已骑满", laps[i].isComplete)
        }
        assertEquals(metersPerSegment / 1000.0, laps[3].distanceKm, 1e-4)
        assertFalse("尾圈不应标记为已完成", laps[3].isComplete)
    }

    @Test
    fun `圈用时按毫秒累加并正确折算成秒与均速`() {
        // 每段 10 s → 每圈 3 段 = 30 s;333.585 m / (30 s) ≈ 40.03 km/h。
        val laps = Laps.split(track(11), 250.0)

        assertEquals(30L, laps[0].durationSec)
        assertEquals(40.03, laps[0].avgSpeedKmh, 0.02)
        // 尾圈只有 1 段 → 10 s
        assertEquals(10L, laps[3].durationSec)
        assertEquals(40.03, laps[3].avgSpeedKmh, 0.02)
    }

    @Test
    fun `超出阈值的时间间隔不计入圈用时但仍计入距离`() {
        // 第 2 个点与第 1 个点相隔 20 s(> 10 s 阈值)→ 该段用时被剔除,距离照算。
        val points = listOf(
            pointAt(0, 0L),
            pointAt(1, stepMs),
            pointAt(2, stepMs + 20_000L),
        )
        val laps = Laps.split(points, 10_000.0)

        assertEquals(1, laps.size)
        assertEquals(10L, laps[0].durationSec)
        assertEquals(2 * metersPerSegment / 1000.0, laps[0].distanceKm, 1e-4)
    }

    @Test
    fun `每圈心率独立统计`() {
        val hr = listOf(100, 120, 140, 160, 150, 150, 150, 90, 100, 110, 120)
        val laps = Laps.split(track(11, heartRates = hr), 250.0)

        // 第 1 圈覆盖点 0..3(边界点归属前一形态的末点)
        assertEquals(130.0, laps[0].avgHeartRateBpm, 1e-6)
        assertEquals(160, laps[0].maxHeartRateBpm)
        // 第 2 圈覆盖点 4..6
        assertEquals(150.0, laps[1].avgHeartRateBpm, 1e-6)
        assertEquals(150, laps[1].maxHeartRateBpm)
        // 第 3 圈覆盖点 7..9
        assertEquals(100.0, laps[2].avgHeartRateBpm, 1e-6)
        assertEquals(110, laps[2].maxHeartRateBpm)
        // 尾圈只有点 10
        assertEquals(120.0, laps[3].avgHeartRateBpm, 1e-6)
    }

    @Test
    fun `无心率数据时心率字段为零`() {
        val laps = Laps.split(track(11), 250.0)
        laps.forEach {
            assertEquals(0.0, it.avgHeartRateBpm, 0.0)
            assertEquals(0, it.maxHeartRateBpm)
        }
    }

    /**
     * 跨圈海拔回归测试。
     *
     * 曾经的缺陷:开新圈时把海拔基线重置回**整段第一个点**的海拔,
     * 导致新一圈的首个高差被算成"起点→圈首"的整段落差(此例中会凭空多出 30 m)。
     * 正确行为是保留边界点海拔作为新圈基线,每圈爬升恒等于圈内真实增量。
     */
    @Test
    fun `跨圈不会把整段落差算进新一圈的爬升`() {
        val elevations = (0 until 11).map { 100.0 + it * 10.0 }
        val laps = Laps.split(track(11, elevations = elevations), 250.0)

        // 每圈 3 段 × 10 m = 30 m;尾圈 1 段 = 10 m。
        assertEquals(30.0, laps[0].elevationGainM, 1e-6)
        assertEquals(30.0, laps[1].elevationGainM, 1e-6)
        assertEquals(30.0, laps[2].elevationGainM, 1e-6)
        assertEquals(10.0, laps[3].elevationGainM, 1e-6)
    }

    @Test
    fun `下坡与噪声不累计为爬升`() {
        // 一路下坡(海拔递减)+ 抖动:均不满足 +0.5 m 阈值,爬升应为 0。
        val elevations = (0 until 11).map { 200.0 - it * 5.0 }
        val laps = Laps.split(track(11, elevations = elevations), 250.0)
        laps.forEach { assertEquals(0.0, it.elevationGainM, 1e-6) }
    }

    @Test
    fun `恰好骑满整数圈时不产生空的尾圈`() {
        // 9 个点 = 8 段;阈值取"2 段再少 1 米"(≈221.39 m),避免踩在浮点相等边界上。
        // 第 2/4/6/8 段各结一圈,尾圈距离为 0,不应入列。
        val laps = Laps.split(track(9), 2 * metersPerSegment - 1.0)
        assertEquals(4, laps.size)
        assertTrue(laps.all { it.isComplete })
    }

    @Test
    fun `haversine 与已知球面距离一致`() {
        assertEquals(0.0, Laps.haversineMeters(30.0, 120.0, 30.0, 120.0), 1e-9)
        // 沿经线 1° ≈ 111.195 km(6371008.8 m × π/180)
        assertEquals(111_195.08, Laps.haversineMeters(30.0, 120.0, 31.0, 120.0), 0.5)
        // 对称性
        val ab = Laps.haversineMeters(30.0, 120.0, 31.5, 121.5)
        val ba = Laps.haversineMeters(31.5, 121.5, 30.0, 120.0)
        assertEquals(ab, ba, 1e-9)
    }

    private fun pointAt(index: Int, timestampMs: Long) = TrackPointEntity(
        rideId = 1L,
        latitude = 30.0 + index * 0.001,
        longitude = 120.0,
        speedKmh = 0.0,
        timestampMs = timestampMs,
        elevationM = 0.0,
        heartRateBpm = 0,
    )
}
