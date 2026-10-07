package com.honglian.smartcycling.ride

/**
 * 海拔水柱的"量程"计算(纯函数,可直接 JVM 单测)。
 *
 * ## 为什么不把水柱直接映射到绝对海拔
 * 骑行的海拔变化通常在几十到几百米。若把水柱映射到 0~8000m 的绝对刻度,
 * 一次 100m 的爬坡只让水面动 1.2% —— 肉眼完全看不出"在爬升"。
 * 因此水柱显示的是**当前海拔在本次骑行量程内的相对位置**。
 *
 * ## 量程怎么定
 * - **下限 [baseM] 一次锁定、永不下移**:骑行开始时按起点海拔下探 [BASE_DROP_M] 米,
 *   之后固定。若下限随"刷新最低海拔"下移,水面会在每次刷新时莫名往上跳一下。
 * - **上限按 [INITIAL_SPAN_M] 起步,超出即翻倍**(200 → 400 → 800 …)。
 *   翻倍相当于"换挡":水面从满格回落到半程,同时量程标签同步变大,
 *   用户看到的是"量程放大了一档",而不是数据出错。
 *
 * ## 已知取舍(如实记录,不假装不存在)
 * 量程翻倍的瞬间,水面会从 ~100% 掉到 ~50%(界面用动画把它抹平)。
 * 这是任何"自适应窗口"都无法回避的非单调点。之所以选择"翻倍"而不是
 * "窗口平滑跟随当前海拔",是因为后者会让水面**永远停在中间**,
 * 彻底失去"正在爬升"的观感 —— 而"一眼看出在爬升"正是这个控件存在的理由。
 *
 * 换句话说:本类保证的是"同一量程内单调",而不是"全局单调"。
 * 全局单调 + 分辨率足够,在数学上不可兼得。
 */
object AltitudeGauge {

    /** 量程下限相对起点海拔的下探余量(米):让下坡时水柱也有位置可掉。 */
    const val BASE_DROP_M = 100.0

    /** 初始量程(米)。 */
    const val INITIAL_SPAN_M = 200.0

    /** 最多翻倍次数:200m × 2^10 = 204800m,覆盖地球全部海拔,同时杜绝死循环。 */
    private const val MAX_DOUBLINGS = 10

    /** 水面填充的上下限:两端各留余量,避免"满格/见底"时看不出还剩多少空间。 */
    private const val FILL_MIN = 0.02f
    private const val FILL_MAX = 0.98f

    /**
     * 量程 `[baseM, baseM + spanM]`。
     *
     * @property baseM 量程下限(绝对海拔,米),整段骑行固定不变。
     * @property spanM 量程跨度(米),随海拔升高自动翻倍。
     */
    data class Window(val baseM: Double, val spanM: Double) {
        /** 量程上限(绝对海拔,米)。 */
        val topM: Double get() = baseM + spanM

        /** 当前海拔在水柱中的填充比例,已钳制到 `[FILL_MIN, FILL_MAX]`。 */
        fun fraction(altM: Double): Float {
            // 零跨度理论上不会出现(window() 保证跨度 ≥ INITIAL_SPAN_M),
            // 但 Window 是公开构造的,这里必须自保,避免除零得到 NaN 把整个水柱画崩。
            if (spanM <= 0.0) return FILL_MIN
            return ((altM - baseM) / spanM).toFloat().coerceIn(FILL_MIN, FILL_MAX)
        }
    }

    /** 由起点海拔推出量程下限(整段骑行固定)。 */
    fun base(startAltitudeM: Double): Double = startAltitudeM - BASE_DROP_M

    /**
     * 由当前海拔推出量程:下限不动,上限不够就翻倍。
     *
     * @param baseM 量程下限,见 [base]。
     * @param currentM 当前海拔(米)。调用方需保证是有限值。
     */
    fun window(baseM: Double, currentM: Double): Window {
        var span = INITIAL_SPAN_M
        var doublings = 0
        // 翻倍而不是线性扩张:每次"换挡"只跳 2 倍,量程标签的变化是整齐的
        // (200/400/800…),用户更容易把它理解为"刻度换了"而不是"数据坏了"。
        while (currentM > baseM + span && doublings < MAX_DOUBLINGS) {
            span *= 2.0
            doublings++
        }
        return Window(baseM, span)
    }
}
