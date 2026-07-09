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
    destination: LatLng? = null,
    follow: Boolean = false,
    showMyLocation: Boolean = true,
    /** 传入真实定位(WGS-84)时,用它驱动镜头跟随与“我的位置”标记,不依赖高德内置定位。 */
    followLocation: LatLng? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { TextureMapView(context) }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(Bundle())
        val aMap: AMap = mapView.map
        // 夜间深色地图样式,与整体深色 HUD 保持一致
        runCatching { aMap.mapType = AMap.MAP_TYPE_NIGHT }
        // 提供外部定位(followLocation)时不启用高德内置定位图层,改由外部定位驱动。
        if (showMyLocation && followLocation == null) {
            val type = when {
                follow -> MyLocationStyle.LOCATION_TYPE_FOLLOW
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

    LaunchedEffect(routePoints, destination) {
        val aMap = mapView.map ?: return@LaunchedEffect
        aMap.clear(true)
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
