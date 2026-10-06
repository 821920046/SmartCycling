package com.honglian.smartcycling.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web Mercator 瓦片数学回归测试(坐标纠偏的底座)。 */
class TileMathTest {

    @Test
    fun `limit 是 2 的 zoom 次幂`() {
        assertEquals(1, TileMath.limit(0))
        assertEquals(2, TileMath.limit(1))
        assertEquals(1024, TileMath.limit(10))
        assertEquals(1 shl 18, TileMath.limit(18))
    }

    @Test
    fun `经度到瓦片列号`() {
        assertEquals(0, TileMath.lonToTileX(-180.0, 0))
        assertEquals(0, TileMath.lonToTileX(-180.0, 1))
        assertEquals(1, TileMath.lonToTileX(0.0, 1))
        assertEquals(0, TileMath.lonToTileX(-0.0001, 1))
    }

    @Test
    fun `赤道落在 zoom0 的第 0 行`() {
        assertEquals(0, TileMath.latToTileY(0.0, 0))
    }

    @Test
    fun `极区纬度被裁剪到 Web Mercator 边界`() {
        // Web Mercator 的纬度上限约 ±85.0511°,超出应被裁剪而不是产生 NaN/越界。
        val top = TileMath.tileYToLat(0.0, 1)
        assertEquals(85.05112878, top, 1e-6)
        val y = TileMath.latToTileY(89.9, 4)
        assertTrue("y 应在合法范围内", y in 0 until TileMath.limit(4))
    }

    @Test
    fun `瓦片中心点可被反查回同一瓦片`() {
        for (z in intArrayOf(0, 1, 5, 10, 14, 18)) {
            val n = TileMath.limit(z)
            val candidates = listOf(0, n / 2, n - 1).distinct()
            for (x in candidates) {
                for (y in candidates) {
                    val center = TileMath.tileCenter(z, x, y) // [lat, lon]
                    assertEquals("z=$z x=$x y=$y 的列号应可反查", x, TileMath.lonToTileX(center[1], z))
                    assertEquals("z=$z x=$x y=$y 的行号应可反查", y, TileMath.latToTileY(center[0], z))
                }
            }
        }
    }

    @Test
    fun `每列宽度恒等于 360 除以列数`() {
        val z = 10
        val width = 360.0 / TileMath.limit(z)
        assertEquals(width, TileMath.tileXToLon(101.0, z) - TileMath.tileXToLon(100.0, z), 1e-9)
        assertEquals(width, TileMath.tileXToLon(1.0, z) - TileMath.tileXToLon(0.0, z), 1e-9)
        // 第 100 列的右边界(左边界公式取 x+1)应与第 101 列左边界重合
        assertEquals(TileMath.tileXToLon(101.0, z), TileMath.tileXToLon(100.0, z) + width, 1e-9)
    }
}
