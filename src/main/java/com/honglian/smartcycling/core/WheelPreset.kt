package com.honglian.smartcycling.core

/**
 * 常见车轮/轮胎尺寸预设及其滞动周长(毫米)。
 * 周长用于根据轮转圈数计算实时速度与里程(速度 = Δ圈 × 周长 / Δt)。
 * 数值参考通用 ETRTO 周长表。
 */
enum class WheelPreset(val label: String, val circumferenceMm: Int) {
    MTB_26_195("26\" ×1.95 (山地)", 2050),
    MTB_26_20("26\" ×2.0 (山地)", 2074),
    MTB_26_21("26\" ×2.1 (山地)", 2089),
    MTB_275_21("27.5\" ×2.1 (山地)", 2148),
    MTB_275_225("27.5\" ×2.25 (山地)", 2182),
    MTB_29_21("29\" ×2.1 (山地)", 2288),
    MTB_29_225("29\" ×2.25 (山地)", 2326),
    ROAD_700_25("700\u00d725C (公路)", 2105),
    ROAD_700_28("700\u00d728C (公路)", 2136),
    ROAD_700_32("700\u00d732C (通勤)", 2155);

    /** 周长(米)。 */
    val circumferenceM: Double get() = circumferenceMm / 1000.0

    companion object {
        /** 默认值:常见山地车 27.5\"×2.1。 */
        val DEFAULT = MTB_275_21
    }
}
