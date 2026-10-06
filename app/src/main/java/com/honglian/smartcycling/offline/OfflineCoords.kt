package com.honglian.smartcycling.offline

import com.amap.api.maps.model.LatLng

/**
 * 离线底图坐标纠偏。
 *
 * 契约:整个应用内部,来自高德的坐标(定位、路线规划、POI)都是 **GCJ-02**;
 * 而 [OfflineMapView] 按 **WGS-84** 网格渲染(osmdroid + [CrsRemapArchiveFile] 已把瓦片纠到 WGS-84)。
 * 因此凡是要画到离线底图上的点,都必须先经过这里做一次 GCJ-02 → WGS-84 转换,
 * 否则车标、路线、目的地会整体偏移 300~600 米。
 *
 * 境外坐标由 [GeoTransform] 原样返回,不受影响。
 */
fun LatLng.toWgs84(): LatLng {
    val w = GeoTransform.convert(latitude, longitude, MapCrs.GCJ02, MapCrs.WGS84)
    return LatLng(w[0], w[1])
}

/** 批量 GCJ-02 → WGS-84。 */
fun List<LatLng>.toWgs84(): List<LatLng> = map { it.toWgs84() }
