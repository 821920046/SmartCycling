package com.honglian.smartcycling.map

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.RideRouteResult
import com.amap.api.services.route.RouteSearch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 高德地图服务封装:定位、反查城市、目的地解析(POI优先)、骑行路线规划。
 * 均返回 GCJ-02 坐标,与高德地图一致,避免偏移。
 */
class AMapRouteRepository(private val context: Context) {

    /** 一次性获取当前位置(GCJ-02)。 */
    suspend fun currentPoint(): LatLonPoint? = suspendCancellableCoroutine { cont ->
        val client = AMapLocationClient(context.applicationContext)
        client.setLocationOption(
            AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = true
            },
        )
        client.setLocationListener { loc ->
            val point = if (loc != null && loc.errorCode == 0) {
                LatLonPoint(loc.latitude, loc.longitude)
            } else {
                null
            }
            client.stopLocation()
            client.onDestroy()
            if (cont.isActive) cont.resume(point)
        }
        cont.invokeOnCancellation {
            client.stopLocation()
            client.onDestroy()
        }
        client.startLocation()
    }

    /** 反地理编码获取当前城市(用于限定 POI 搜索范围),失败返回空串。 */
    suspend fun cityOf(point: LatLonPoint): String = suspendCancellableCoroutine { cont ->
        val search = GeocodeSearch(context)
        search.setOnGeocodeSearchListener(
            object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) {
                    val city = result?.regeocodeAddress?.city?.takeIf { it.isNotBlank() }
                        ?: result?.regeocodeAddress?.province ?: ""
                    if (cont.isActive) cont.resume(city)
                }

                override fun onGeocodeSearched(result: GeocodeResult?, code: Int) = Unit
            },
        )
        search.getFromLocationAsyn(RegeocodeQuery(point, 200f, GeocodeSearch.AMAP))
    }

    /** 目的地解析:优先 POI 关键字搜索(支持地名/商场/景点等),未命中再回退地理编码。 */
    suspend fun resolveDestination(name: String, city: String): LatLonPoint? =
        poiSearch(name, city) ?: geocode(name, city)

    private suspend fun poiSearch(keyword: String, city: String): LatLonPoint? =
        suspendCancellableCoroutine { cont ->
            val query = PoiSearch.Query(keyword, "", city).apply {
                pageSize = 5
                pageNum = 1
            }
            val search = try {
                PoiSearch(context, query)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            search.setOnPoiSearchListener(
                object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, code: Int) {
                        val point = result?.pois?.firstOrNull()?.latLonPoint
                        if (cont.isActive) cont.resume(point)
                    }

                    override fun onPoiItemSearched(item: PoiItem?, code: Int) = Unit
                },
            )
            search.searchPOIAsyn()
        }

    /** 目的地名称 → 坐标(地理编码回退)。 */
    private suspend fun geocode(name: String, city: String = ""): LatLonPoint? =
        suspendCancellableCoroutine { cont ->
            val search = GeocodeSearch(context)
            search.setOnGeocodeSearchListener(
                object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
                        val point = result?.geocodeAddressList?.firstOrNull()?.latLonPoint
                        if (cont.isActive) cont.resume(point)
                    }

                    override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) = Unit
                },
            )
            search.getFromLocationNameAsyn(GeocodeQuery(name, city))
        }

    /** 骑行路径规划 → 折线点集合。 */
    suspend fun planRide(from: LatLonPoint, to: LatLonPoint): List<LatLng> =
        suspendCancellableCoroutine { cont ->
            val search = RouteSearch(context)
            search.setRouteSearchListener(
                object : RouteSearch.OnRouteSearchListener {
                    override fun onRideRouteSearched(result: RideRouteResult?, code: Int) {
                        val points = result?.paths?.firstOrNull()?.steps
                            ?.flatMap { it.polyline }
                            ?.map { LatLng(it.latitude, it.longitude) }
                            ?: emptyList()
                        if (cont.isActive) cont.resume(points)
                    }

                    override fun onDriveRouteSearched(
                        result: com.amap.api.services.route.DriveRouteResult?,
                        code: Int,
                    ) = Unit

                    override fun onBusRouteSearched(
                        result: com.amap.api.services.route.BusRouteResult?,
                        code: Int,
                    ) = Unit

                    override fun onWalkRouteSearched(
                        result: com.amap.api.services.route.WalkRouteResult?,
                        code: Int,
                    ) = Unit
                },
            )
            val query = RouteSearch.RideRouteQuery(RouteSearch.FromAndTo(from, to))
            search.calculateRideRouteAsyn(query)
        }
}
