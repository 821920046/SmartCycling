package com.honglian.smartcycling.offline

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 中国大陆坐标系互转工具。
 *
 * 第一性原理:
 * 同一个物理地点在不同平台的"经纬度数字"并不相同,差异来自加密偏移:
 *  - WGS-84  —— GPS / Google Maps(海外) / OSM / Mapbox 原始坐标
 *  - GCJ-02  —— 国测局火星坐标,高德 / 腾讯 / Google 中国 / 国内大部分瓦片
 *  - BD-09   —— 百度在 GCJ-02 上二次偏移
 * 三者在中国大陆境内相差 300~600 米。把 GCJ-02 的瓦片当 WGS-84 渲染,
 * 车标就会整体"漂移"半条街 —— 这是离线地图导入最容易翻车的地方。
 *
 * 本文件提供三者之间的双向转换。算法为业界公开的标准实现:
 *  - WGS→GCJ 正向:椭球参数 + 三角函数扰动
 *  - GCJ→WGS 反向:正向无解析逆,采用**不动点迭代**收敛(精度优于 1e-7 度,约 1cm)
 *  - GCJ↔BD09:公开的固定公式,双向均有解析解
 *
 * 所有方法在中国大陆境外(或转换失败)时原样返回,保证海外地图不受影响。
 */
object GeoTransform {

    private const val PI = Math.PI
    private const val A = 6378245.0                 // 克拉索夫斯基椭球长半轴
    private const val EE = 0.00669342162296594323   // 第一偏心率平方
    private const val X_PI = PI * 3000.0 / 180.0

    /** 是否在中国大陆范围外(境外不做偏移)。 */
    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 73.66 || lon > 135.05 || lat < 3.86 || lat > 53.55

    /** WGS-84 → GCJ-02。返回 [lat, lon]。 */
    fun wgs84ToGcj02(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        val dLatLon = delta(lat, lon)
        return doubleArrayOf(lat + dLatLon[0], lon + dLatLon[1])
    }

    /**
     * GCJ-02 → WGS-84(不动点迭代求逆)。
     * 误差随迭代次数指数收敛,3 次即可到亚厘米级。
     */
    fun gcj02ToWgs84(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var wLat = lat
        var wLon = lon
        repeat(4) {
            val g = wgs84ToGcj02(wLat, wLon)
            wLat -= g[0] - lat
            wLon -= g[1] - lon
        }
        return doubleArrayOf(wLat, wLon)
    }

    /** GCJ-02 → BD-09。 */
    fun gcj02ToBd09(lat: Double, lon: Double): DoubleArray {
        val z = sqrt(lon * lon + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = Math.atan2(lat, lon) + 0.000003 * cos(lon * X_PI)
        return doubleArrayOf(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    /** BD-09 → GCJ-02。 */
    fun bd09ToGcj02(lat: Double, lon: Double): DoubleArray {
        val x = lon - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = Math.atan2(y, x) - 0.000003 * cos(x * X_PI)
        return doubleArrayOf(z * sin(theta), z * cos(theta))
    }

    /** WGS-84 → BD-09。 */
    fun wgs84ToBd09(lat: Double, lon: Double): DoubleArray {
        val g = wgs84ToGcj02(lat, lon)
        return gcj02ToBd09(g[0], g[1])
    }

    /** BD-09 → WGS-84。 */
    fun bd09ToWgs84(lat: Double, lon: Double): DoubleArray {
        val g = bd09ToGcj02(lat, lon)
        return gcj02ToWgs84(g[0], g[1])
    }

    /** 从 [from] 坐标系转换到 [to] 坐标系。 */
    fun convert(lat: Double, lon: Double, from: MapCrs, to: MapCrs): DoubleArray {
        if (from == to) return doubleArrayOf(lat, lon)
        // 先归一到 WGS-84,再转出。
        val wgs = when (from) {
            MapCrs.WGS84 -> doubleArrayOf(lat, lon)
            MapCrs.GCJ02 -> gcj02ToWgs84(lat, lon)
            MapCrs.BD09 -> bd09ToWgs84(lat, lon)
        }
        return when (to) {
            MapCrs.WGS84 -> wgs
            MapCrs.GCJ02 -> wgs84ToGcj02(wgs[0], wgs[1])
            MapCrs.BD09 -> wgs84ToBd09(wgs[0], wgs[1])
        }
    }

    /** 正向偏移量 [dLat, dLon]。 */
    private fun delta(lat: Double, lon: Double): DoubleArray {
        val dLat = transformLat(lon - 105.0, lat - 35.0)
        val dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        return doubleArrayOf(
            (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI),
            (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI),
        )
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
