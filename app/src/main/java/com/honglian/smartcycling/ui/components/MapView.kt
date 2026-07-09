package com.honglian.smartcycling.ui.components

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions

/**
 * 高德地图视图(Compose 包装)。
 *
 * @param routePoints 传入时绘制骑行路线。
 * @param follow 骑行导航模式:地图实时跟随当前位置、放大到街道级;
 *   预览模式(false)则只显示蓝点并把整条路线自适应到视野。
 *
 * 防抖动:路线绘制只在 routePoints 变化时执行一次(LaunchedEffect),不放在每帧 update 块。
 */
@Composable
fun NavigationMapView(
    modifier: Modifier = Modifier,
    routePoints: List<LatLng> = emptyList(),
    follow: Boolean = false,
    showMyLocation: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { TextureMapView(context) }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(Bundle())
        val aMap: AMap = mapView.map
        if (showMyLocation) {
            val type =
                if (follow) MyLocationStyle.LOCATION_TYPE_FOLLOW
                else MyLocationStyle.LOCATION_TYPE_SHOW
            aMap.myLocationStyle = MyLocationStyle().myLocationType(type).interval(1000)
            aMap.isMyLocationEnabled = true
        }
        aMap.uiSettings.isZoomControlsEnabled = false
        aMap.uiSettings.isMyLocationButtonEnabled = false
        if (follow) aMap.moveCamera(CameraUpdateFactory.zoomTo(17f))

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

    LaunchedEffect(routePoints) {
        val aMap = mapView.map ?: return@LaunchedEffect
        aMap.clear(true)
        if (routePoints.size >= 2) {
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .width(20f)
                    .color(0xFF2563EB.toInt()),
            )
            // 预览模式才自适应整条路线;导航模式交由定位跟随控制镜头。
            if (!follow) {
                val builder = LatLngBounds.Builder()
                routePoints.forEach { builder.include(it) }
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
