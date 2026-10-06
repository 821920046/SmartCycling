package com.honglian.smartcycling.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 单位换算回归测试。
 *
 * [Units] 是纯函数对象,可在 JVM 上直接验证 —— 这类"数值正确性"最容易在重构中悄悄写错
 * (例如把英里系数写成 1.6、把米/英尺弄反),而一旦出错,界面上只会显示一个"看起来正常"
 * 的错误数字,肉眼极难发现。
 *
 * 测试期间把默认 Locale 固定为 US:`"%.2f".format(...)` 走的是**默认 Locale**,
 * 在 de_DE 之类的小数点为逗号的区域会得到 "12,35 km",断言必须与运行环境无关。
 */
class UnitsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ------------------------------------------------------------ 数值换算

    @Test
    fun `公制下换算为恒等`() {
        assertEquals(1.0, Units.distance(1.0, UnitSystem.METRIC), 1e-12)
        assertEquals(42.195, Units.distance(42.195, UnitSystem.METRIC), 1e-12)
        assertEquals(30.0, Units.speed(30.0, UnitSystem.METRIC), 1e-12)
        assertEquals(500.0, Units.elevation(500.0, UnitSystem.METRIC), 1e-12)
    }

    @Test
    fun `英制换算系数正确`() {
        // 1 英里 = 1.609344 km(国际英里定义值)
        assertEquals(1.0, Units.distance(1.609344, UnitSystem.IMPERIAL), 1e-9)
        assertEquals(0.621371192, Units.distance(1.0, UnitSystem.IMPERIAL), 1e-9)
        // 100 km/h ≈ 62.137 mph
        assertEquals(62.1371192, Units.speed(100.0, UnitSystem.IMPERIAL), 1e-6)
        // 100 m ≈ 328.084 ft
        assertEquals(328.0839895, Units.elevation(100.0, UnitSystem.IMPERIAL), 1e-6)
    }

    @Test
    fun `逆换算与正换算互为反函数`() {
        val samples = listOf(0.0, 0.5, 1.0, 12.345, 42.195, 100.0, 160.9344)
        samples.forEach { km ->
            assertEquals(
                "公制往返 $km",
                km,
                Units.kmFromDistance(Units.distance(km, UnitSystem.METRIC), UnitSystem.METRIC),
                1e-12,
            )
            assertEquals(
                "英制往返 $km",
                km,
                Units.kmFromDistance(Units.distance(km, UnitSystem.IMPERIAL), UnitSystem.IMPERIAL),
                1e-9,
            )
        }
    }

    // ------------------------------------------------------------ 单位符号

    @Test
    fun `单位符号不随语言变化`() {
        assertEquals("km", Units.distanceUnit(UnitSystem.METRIC))
        assertEquals("mi", Units.distanceUnit(UnitSystem.IMPERIAL))
        assertEquals("km/h", Units.speedUnit(UnitSystem.METRIC))
        assertEquals("mph", Units.speedUnit(UnitSystem.IMPERIAL))
        assertEquals("m", Units.elevationUnit(UnitSystem.METRIC))
        assertEquals("ft", Units.elevationUnit(UnitSystem.IMPERIAL))
    }

    // ------------------------------------------------------------ 组合文本

    @Test
    fun `距离文本按单位制切换`() {
        assertEquals("12.35 km", Units.distanceText(12.3456, UnitSystem.METRIC))
        assertEquals("7.67 mi", Units.distanceText(12.3456, UnitSystem.IMPERIAL))
        assertEquals("5.0 km", Units.distanceText(5.0, UnitSystem.METRIC, decimals = 1))
    }

    @Test
    fun `速度文本按单位制切换`() {
        assertEquals("24.1 km/h", Units.speedText(24.1, UnitSystem.METRIC))
        assertEquals("15.0 mph", Units.speedText(24.1, UnitSystem.IMPERIAL))
    }

    @Test
    fun `爬升文本按单位制切换`() {
        assertEquals("860 m", Units.elevationText(860.0, UnitSystem.METRIC))
        assertEquals("2822 ft", Units.elevationText(860.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `播报距离公制下千米进位英制下用英尺`() {
        assertEquals("320 m", Units.shortDistanceText(320.0, UnitSystem.METRIC))
        assertEquals("1.5 km", Units.shortDistanceText(1500.0, UnitSystem.METRIC))
        assertEquals("328 ft", Units.shortDistanceText(100.0, UnitSystem.IMPERIAL))
        assertEquals("4921 ft", Units.shortDistanceText(1500.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `SpeedRing 数值与单位符号配套`() {
        // 满量程 60 km/h 在英制下应是 37.3 mph,而不是仍按 60 显示。
        assertEquals(60.0, Units.speedValue(60.0, UnitSystem.METRIC), 1e-9)
        assertEquals(37.2823, Units.speedValue(60.0, UnitSystem.IMPERIAL), 1e-4)
        assertTrue(Units.speedValue(60.0, UnitSystem.IMPERIAL) < 60.0)
    }
}
