package com.honglian.smartcycling.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AltitudeGauge] 的水柱量程行为。
 *
 * 这些断言保护的是**水面观感**,而不是"数学好看":
 * 若量程退化成常量,水面就不动;若翻倍逻辑写反,水面会乱跳。
 * 两种情况编译器都不会报错,只有单测能先一步拦住。
 *
 * 断言里刻意少用精确值、多用区间,避免把私有常量(填充上下限)的实现细节焊死:
 * 这里要锁的是"行为"(满格会回落、见底会钳制),不是那几个魔数。
 */
class AltitudeGaugeTest {

    private val delta = 1e-9

    @Test
    fun `量程下限由起点海拔下探固定余量`() {
        assertEquals(400.0, AltitudeGauge.base(500.0), delta)
        // 余量必须为正:否则起点就是量程最低点,一下坡水面立刻见底。
        assertTrue(AltitudeGauge.BASE_DROP_M > 0.0)
    }

    @Test
    fun `刚出发时用初始量程且水面居中`() {
        val base = AltitudeGauge.base(500.0) // 400
        val w = AltitudeGauge.window(base, 500.0)
        assertEquals(AltitudeGauge.INITIAL_SPAN_M, w.spanM, delta)
        assertEquals(600.0, w.topM, delta)
        // 起点海拔落在量程正中(下探余量 100 = 初始跨度 200 的一半)
        assertEquals(0.5f, w.fraction(500.0), 1e-6f)
    }

    @Test
    fun `同一量程内海拔越高水面越高`() {
        val base = AltitudeGauge.base(500.0)
        val w = AltitudeGauge.window(base, 500.0)
        val heights = listOf(400.0, 450.0, 500.0, 550.0, 600.0).map { w.fraction(it) }
        assertEquals(heights.sorted(), heights)
        assertTrue("相邻高度必须真的拉开差距,否则水柱看不出变化", heights[1] - heights[0] > 0.2f)
    }

    @Test
    fun `超出量程上限后翻倍而不是溢出`() {
        val base = AltitudeGauge.base(500.0) // 400
        // 600 恰好压在上限上:不翻倍
        assertEquals(200.0, AltitudeGauge.window(base, 600.0).spanM, delta)
        // 601 超出上限:翻倍到 400
        assertEquals(400.0, AltitudeGauge.window(base, 601.0).spanM, delta)
        assertEquals(800.0, AltitudeGauge.window(base, 1001.0).spanM, delta)
        assertEquals(1600.0, AltitudeGauge.window(base, 1900.0).spanM, delta)
    }

    @Test
    fun `翻倍瞬间水面从满格回落到半程`() {
        val base = AltitudeGauge.base(500.0) // 400
        val before = AltitudeGauge.window(base, 600.0).fraction(600.0)
        val after = AltitudeGauge.window(base, 601.0).fraction(601.0)
        assertTrue("翻倍前应接近满格,实际 $before", before > 0.9f)
        assertTrue("翻倍后应回落到半程附近,实际 $after", after in 0.45f..0.60f)
    }

    @Test
    fun `从起点往上水面始终不低于半程`() {
        // 不变量:翻倍只在"超过当前上限"时发生,而翻倍后当前海拔必然落在**新量程的中点之上**,
        // 所以只要在爬升,填充比例就 ≥ 50%。这条不变量若被破坏,说明翻倍方向或次数写错了。
        //
        // 注意下界是**闭区间**:起点海拔恰好落在量程正中(下探余量 100 = 初始跨度 200 的一半),
        // 因此 alt == 起点时比例正好等于 0.5,写成严格大于会误报。
        // (这个边界值是先用 Python 独立复现时被发现的 —— 逻辑没错,是断言写错了。)
        val base = AltitudeGauge.base(500.0) // 400
        for (alt in 500..1400 step 7) {
            val f = AltitudeGauge.window(base, alt.toDouble()).fraction(alt.toDouble())
            assertTrue("海拔 $alt 的水面 $f 不该低于半程", f >= 0.5f)
        }
        // 严格高于起点时则必须严格高于半程
        val above = AltitudeGauge.window(base, 500.5).fraction(500.5)
        assertTrue("略高于起点时水面应严格过半,实际 $above", above > 0.5f)
    }

    @Test
    fun `低于量程下限时钳制在很低但不为零的位置`() {
        val base = AltitudeGauge.base(500.0) // 400
        val w = AltitudeGauge.window(base, 500.0)
        val f = w.fraction(0.0)
        // 不为 0 是有意的:见底时仍留一条可见的细水,用户才能区分"到底了"和"控件坏了"。
        assertTrue("见底应钳制在很低的位置,实际 $f", f > 0f && f < 0.05f)
    }

    @Test
    fun `珠峰高度也能落在量程内`() {
        val base = AltitudeGauge.base(0.0) // -100
        val w = AltitudeGauge.window(base, 8848.0)
        assertTrue("量程上限必须覆盖当前海拔,实际 ${w.topM}", w.topM >= 8848.0)
        val f = w.fraction(8848.0)
        assertTrue("水面必须落在量程内,实际 $f", f in 0.02f..0.98f)
    }

    @Test
    fun `极大输入不会死循环`() {
        val w = AltitudeGauge.window(0.0, Double.MAX_VALUE)
        // 200m × 2^10;这条断言同时证明 MAX_DOUBLINGS 兜底生效了。
        assertEquals(AltitudeGauge.INITIAL_SPAN_M * 1024.0, w.spanM, delta)
    }

    @Test
    fun `零跨度量程不产生除零`() {
        val w = AltitudeGauge.Window(baseM = 100.0, spanM = 0.0)
        val f = w.fraction(500.0)
        assertTrue("零跨度必须退化成有限值而不是 NaN,实际 $f", f.isFinite())
    }
}
