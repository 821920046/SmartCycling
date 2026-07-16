package com.honglian.smartcycling.ui.components

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions

/**
 * 高德地图视图(Compose 包装)。
 *
 * @param routePoints 传入时绘制骑行路线。
 * @param destination 目的地;传入时在地图上标红色终点。
 * @param follow 骑行导航模式:地图实时跟随当前位置、放大到街道级;
 *   预览模式(false)且无路线/目的地时自动定位并居中到当前位置。
 *
 * 防抖动:路线/标记绘制只在 routePoints/destination 变化时执行(LaunchedEffect),不放在每帧 update 块。
 */
@Composable
fun NavigationMapView(
    modifier: Modifier = Modifier,
    routePoints: List<LatLng> = emptyList(),
    /** 已骑行轨迹(GCJ-02):在规划线之上叠加一条橙色“走过的路”。 */
    traveledPoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    follow: Boolean = false,
    showMyLocation: Boolean = true,
    /** 传入真实定位(WGS-84)时,用它驱动镜头跟随与“我的位置”标记,不依赖高德内置定位。 */
    followLocation: LatLng? = null,
    mapType: Int = 3,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { TextureMapView(context) }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(Bundle())
        val aMap: AMap = mapView.map
        // 提供外部定位(followLocation)时不启用高德内置定位图层,改由外部定位驱动。
        if (showMyLocation && followLocation == null) {
            val type = when {
                // 导航模式：定位箭头随设备朝向旋转（罗盘）并保持居中。
                follow -> MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE
                // 预览且尚无路线/目的地:定位并将镜头居中到当前位置
                routePoints.isEmpty() && destination == null -> MyLocationStyle.LOCATION_TYPE_LOCATE
                else -> MyLocationStyle.LOCATION_TYPE_SHOW
            }
            aMap.myLocationStyle = MyLocationStyle().myLocationType(type).interval(1000)
            aMap.isMyLocationEnabled = true
        }
        aMap.uiSettings.isZoomControlsEnabled = false
        aMap.uiSettings.isMyLocationButtonEnabled = false
        if (follow) aMap.moveCamera(CameraUpdateFactory.zoomTo(16f))

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // 动态监听地图样式变化
    LaunchedEffect(mapType) {
        val aMap = mapView.map ?: return@LaunchedEffect
        runCatching {
            aMap.mapType = when (mapType) {
                1 -> AMap.MAP_TYPE_NORMAL
                2 -> AMap.MAP_TYPE_SATELLITE
                else -> AMap.MAP_TYPE_NIGHT
            }
        }
    }


    // 已走轨迹折线(持久引用,逐秒 setPoints 更新,避免每秒 clear 重绘全图)
    val traveledLine = remember { mutableStateOf<Polyline?>(null) }

    LaunchedEffect(routePoints, destination) {
        val aMap = mapView.map ?: return@LaunchedEffect
        aMap.clear(true)
        // clear(true) 会一并移除已走轨迹折线,置空引用以便下一次重建。
        traveledLine.value = null
        // 目的地红色标记
        destination?.let { aMap.addMarker(MarkerOptions().position(it).title("目的地")) }
        if (routePoints.size >= 2) {
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .width(20f)
                    .color(0xFF34D399.toInt()),
            )
            // 预览模式才自适应整条路线(含目的地);导航模式交由定位跟随控制镜头。
            if (!follow) {
                val builder = LatLngBounds.Builder()
                routePoints.forEach { builder.include(it) }
                destination?.let { builder.include(it) }
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
            }
        } else if (destination != null && !follow) {
            // 尚无路线时先把镜头对准目的地
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f))
        }
    }

    // 用真实定位(WGS-84→GCJ-02)驱动镜头跟随与“我的位置”标记,不依赖高德内置定位。
    val myMarker = remember { mutableStateOf<Marker?>(null) }
    LaunchedEffect(followLocation) {
        val aMap = mapView.map ?: return@LaunchedEffect
        val wgs = followLocation ?: return@LaunchedEffect
        val gcj = toGcj02(context, wgs)
        runCatching { myMarker.value?.remove() }
        myMarker.value = runCatching {
            aMap.addMarker(MarkerOptions().position(gcj).title("我的位置"))
        }.getOrNull()
        if (follow) runCatching { aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gcj, 17f)) }
    }

    // 实时回放“走过的路”:橙色折线,叠在绿色规划线之上。
    LaunchedEffect(traveledPoints) {
        val aMap = mapView.map ?: return@LaunchedEffect
        if (traveledPoints.size < 2) return@LaunchedEffect
        val existing = traveledLine.value
        if (existing != null) {
            runCatching { existing.points = traveledPoints }
        } else {
            traveledLine.value = runCatching {
                aMap.addPolyline(
                    PolylineOptions()
                        .addAll(traveledPoints)
                        .width(16f)
                        .color(0xFFF59E0B.toInt())
                        .zIndex(2f),
                )
            }.getOrNull()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 将 WGS-84(GPS)坐标转换为高德 GCJ-02 坐标;转换失败则原样返回。 */
internal fun toGcj02(context: Context, wgs: LatLng): LatLng =
    runCatching {
        CoordinateConverter(context)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(wgs)
            .convert()
    }.getOrNull() ?: wgs
