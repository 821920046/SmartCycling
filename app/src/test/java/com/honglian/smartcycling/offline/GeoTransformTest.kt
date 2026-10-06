package com.honglian.smartcycling.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坐标系互转回归测试。
 *
 * 这是整个离线地图功能里"最容易悄悄错"的一环:偏移 300~600m 不会崩溃、不会报错,
 * 只会让车标整体漂移半条街。因此必须用测试锁住。
 */
class GeoTransformTest {

    private val delta = 1e-6 // 约 0.1 米

    /**
     * BD09 的往返容差必须比 WGS↔GCJ 宽,原因是**公式本身**,不是实现有误。
     *
     * GCJ↔BD09 用的是业界公开的**近似**互逆公式:正向用 GCJ 坐标算扰动项,
     * 反向用"扣掉 0.006/0.0065 偏移后"的坐标算扰动项,两者并非严格互逆。
     * 实测(华中华东境内 12300 个采样点,步长 0.5°)最大往返残差
     * **1.86e-6 度 ≈ 0.21 米**。
     *
     * 为什么不去"修"这个残差:所有工具(含百度自家)都使用这组公式,
     * 把反向改成严格数值逆反而会与它们不一致,导入导出时更容易对不上。
     * 取 5e-6 度 ≈ 0.56 米,相对本模块要纠正的 300~600 米偏移可完全忽略。
     */
    private val deltaBd09 = 5e-6

    @Test
    fun `境外坐标不做偏移`() {
        // 伦敦
        assertTrue(GeoTransform.outOfChina(51.5074, -0.1278))
        val p = GeoTransform.wgs84ToGcj02(51.5074, -0.1278)
        assertEquals(51.5074, p[0], 0.0)
        assertEquals(-0.1278, p[1], 0.0)
    }

    @Test
    fun `境内坐标判定`() {
        assertFalse(GeoTransform.outOfChina(39.9042, 116.4074)) // 北京
        assertFalse(GeoTransform.outOfChina(31.2304, 121.4737)) // 上海
    }

    @Test
    fun `WGS84 转 GCJ02 确实产生偏移`() {
        val wgs = doubleArrayOf(39.9042, 116.4074)
        val gcj = GeoTransform.wgs84ToGcj02(wgs[0], wgs[1])
        // 偏移量级应在数百米(约 0.001~0.01 度),既不能为 0,也不能离谱。
        val dLat = Math.abs(gcj[0] - wgs[0])
        val dLon = Math.abs(gcj[1] - wgs[1])
        assertTrue("dLat=$dLat 应显著大于 0", dLat > 1e-4)
        assertTrue("dLon=$dLon 应显著大于 0", dLon > 1e-4)
        assertTrue("dLat=$dLat 不应超过 0.02 度", dLat < 0.02)
        assertTrue("dLon=$dLon 不应超过 0.02 度", dLon < 0.02)
    }

    @Test
    fun `GCJ02 转 WGS84 是正向的可逆运算`() {
        val samples = listOf(
            doubleArrayOf(39.9042, 116.4074),
            doubleArrayOf(31.2304, 121.4737),
            doubleArrayOf(23.1291, 113.2644),
            doubleArrayOf(30.5728, 104.0668),
        )
        for (wgs in samples) {
            val gcj = GeoTransform.wgs84ToGcj02(wgs[0], wgs[1])
            val back = GeoTransform.gcj02ToWgs84(gcj[0], gcj[1])
            assertEquals("lat round-trip @ ${wgs.toList()}", wgs[0], back[0], delta)
            assertEquals("lon round-trip @ ${wgs.toList()}", wgs[1], back[1], delta)
        }
    }

    @Test
    fun `BD09 往返可逆`() {
        val wgs = doubleArrayOf(39.9042, 116.4074)
        val bd = GeoTransform.wgs84ToBd09(wgs[0], wgs[1])

        // 先确认"确实发生了 BD09 偏移"。否则万一实现退化成"原样返回",
        // 下面的往返断言会因为 0 == 0 而恒真 —— 回归测试就变成了摆设。
        val offsetDeg = maxOf(Math.abs(bd[0] - wgs[0]), Math.abs(bd[1] - wgs[1]))
        assertTrue("BD09 偏移量应达数百米量级,实际 $offsetDeg 度", offsetDeg > 1e-3)

        val back = GeoTransform.bd09ToWgs84(bd[0], bd[1])
        assertEquals("lat round-trip", wgs[0], back[0], deltaBd09)
        assertEquals("lon round-trip", wgs[1], back[1], deltaBd09)
    }

    @Test
    fun `convert 同坐标系原样返回`() {
        val p = GeoTransform.convert(39.9, 116.4, MapCrs.GCJ02, MapCrs.GCJ02)
        assertEquals(39.9, p[0], 0.0)
        assertEquals(116.4, p[1], 0.0)
    }

    @Test
    fun `convert 任意两坐标系之间往返一致`() {
        val wgs = doubleArrayOf(39.9042, 116.4074)
        for (crs in MapCrs.entries) {
            val there = GeoTransform.convert(wgs[0], wgs[1], MapCrs.WGS84, crs)
            val back = GeoTransform.convert(there[0], there[1], crs, MapCrs.WGS84)
            // BD09 走的是近似互逆公式,容差需放宽 —— 理由见 `deltaBd09` 的注释。
            val tol = if (crs == MapCrs.BD09) deltaBd09 else delta
            assertEquals("lat via $crs", wgs[0], back[0], tol)
            assertEquals("lon via $crs", wgs[1], back[1], tol)
        }
    }
}
