package com.honglian.smartcycling.core

/**
 * 单位制。
 *
 * 第一性原理:**内部一律用公制**。
 * 距离/速度/爬升在数据库、GPX、云端载荷里全部是米与 km/h,只有"显示层"才按用户偏好换算。
 * 这样切换单位不会污染任何持久化数据,也不会让历史记录出现"同一段路两个数值"。
 */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * 单位换算与格式化。
 *
 * 全部为纯函数(无 Android / Compose 依赖),可直接在 JVM 单测里验证往返一致性。
 */
object Units {

    /** 1 英里 = 1609.344 米(国际英里,精确定义值)。 */
    private const val METERS_PER_MILE = 1609.344

    /** 1 英尺 = 0.3048 米(精确定义值)。 */
    private const val METERS_PER_FOOT = 0.3048

    // ------------------------------------------------------------ 数值换算

    /** 公里 → 当前单位下的距离数值。 */
    fun distance(km: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) km * 1000.0 / METERS_PER_MILE else km

    /** km/h → 当前单位下的速度数值。 */
    fun speed(kmh: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) kmh * 1000.0 / METERS_PER_MILE else kmh

    /** 米 → 当前单位下的爬升数值。 */
    fun elevation(meters: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) meters / METERS_PER_FOOT else meters

    /** 米 → 当前单位下的小段距离数值(导航播报用:公制取米,英制取英尺)。 */
    fun shortDistance(meters: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) meters / METERS_PER_FOOT else meters

    /**
     * [distance] 的逆运算:当前单位下的距离数值 → 公里。
     *
     * 用于"按显示单位调节、按公里存储"的控件(如自动分圈距离滑块):
     * 滑块的值随单位制变化,但写回设置时统一折回公里,避免反复换算产生累计误差。
     */
    fun kmFromDistance(value: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) value * METERS_PER_MILE / 1000.0 else value

    // ------------------------------------------------------------ 单位符号

    fun distanceUnit(unit: UnitSystem): String =
        if (unit == UnitSystem.IMPERIAL) "mi" else "km"

    fun speedUnit(unit: UnitSystem): String =
        if (unit == UnitSystem.IMPERIAL) "mph" else "km/h"

    fun elevationUnit(unit: UnitSystem): String =
        if (unit == UnitSystem.IMPERIAL) "ft" else "m"

    fun shortDistanceUnit(unit: UnitSystem): String =
        if (unit == UnitSystem.IMPERIAL) "ft" else "m"

    // ------------------------------------------------------------ 组合文本
    //
    // 说明:这些是"数值 + 单位符号"的拼接,单位符号(km / mi / mph / ft)在任何语言下写法一致,
    // 因此不放进 strings.xml —— 放进去反而会让每个语言都要重复一遍相同的符号。
    // 需要翻译的**句子**(如"预计骑行 …""均速 …")仍然走资源。

    /** "12.34 km" / "7.67 mi"。 */
    fun distanceText(km: Double, unit: UnitSystem, decimals: Int = 2): String =
        "%.${decimals}f %s".format(distance(km, unit), distanceUnit(unit))

    /** "24.1 km/h" / "15.0 mph"。 */
    fun speedText(kmh: Double, unit: UnitSystem, decimals: Int = 1): String =
        "%.${decimals}f %s".format(speed(kmh, unit), speedUnit(unit))

    /** "860 m" / "2822 ft"。 */
    fun elevationText(meters: Double, unit: UnitSystem): String =
        "%.0f %s".format(elevation(meters, unit), elevationUnit(unit))

    /** 仅数值(用于 SpeedRing 这类"数值与单位分开渲染"的控件)。 */
    fun speedValue(kmh: Double, unit: UnitSystem): Double = speed(kmh, unit)

    /** 导航播报用的小段距离:"320 m" / "1050 ft";公制下 ≥1km 自动进位到 km。 */
    fun shortDistanceText(meters: Double, unit: UnitSystem): String {
        if (unit == UnitSystem.IMPERIAL) {
            return "%.0f %s".format(meters / METERS_PER_FOOT, shortDistanceUnit(unit))
        }
        return if (meters >= 1000.0) {
            "%.1f km".format(meters / 1000.0)
        } else {
            "%.0f m".format(meters)
        }
    }
}
