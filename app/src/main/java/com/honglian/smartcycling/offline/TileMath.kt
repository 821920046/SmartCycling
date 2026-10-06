package com.honglian.smartcycling.offline

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator(EPSG:3857)瓦片网格数学。
 *
 * 这里刻意与 osmdroid 内部实现解耦:坐标系重映射需要在"经纬度 ↔ 瓦片索引"之间
 * 自由往返,自己实现一份语义明确的工具比依赖第三方内部类更可控。
 *
 * 约定:采用 **XYZ / Slippy** 编号(y 从北向南递增),与 MBTiles 规范相反(TMS)。
 * MBTiles 的 TMS 翻转由 osmdroid 的 MBTilesFileArchive 内部处理。
 */
object TileMath {

    fun tilesPerAxis(zoom: Int): Double = Math.pow(2.0, zoom.toDouble())

    /** 经度 → 瓦片列号(可能越界,调用方自行裁剪)。 */
    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = tilesPerAxis(zoom)
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    /** 纬度 → 瓦片行号(Web Mercator,已对极区做裁剪)。 */
    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = tilesPerAxis(zoom)
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clamped * PI / 180.0
        return floor((1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * n).toInt()
    }

    /** 瓦片列号 → 瓦片**左边界**经度。 */
    fun tileXToLon(x: Double, zoom: Int): Double =
        x / tilesPerAxis(zoom) * 360.0 - 180.0

    /** 瓦片行号 → 瓦片**上边界**纬度。 */
    fun tileYToLat(y: Double, zoom: Int): Double {
        val n = tilesPerAxis(zoom)
        return Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / n))))
    }

    /** 瓦片中心点 [lat, lon]。 */
    fun tileCenter(zoom: Int, x: Int, y: Int): DoubleArray = doubleArrayOf(
        tileYToLat(y + 0.5, zoom),
        tileXToLon(x + 0.5, zoom),
    )

    /** 该 zoom 下合法的 x/y 上界(不含)。 */
    fun limit(zoom: Int): Int = 1 shl zoom
}
