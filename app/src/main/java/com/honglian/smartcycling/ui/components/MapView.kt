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
 * 高德地图导航视图(Compose 包装)。
 * - 显示当前位置蓝点(需定位权限 + 高德 Key)。
 * - 传入 routePoints 时绘制骑行路线并自动调整视野。
 *
 * 修复“地图一直跳动”:
 * - 路线绘制与移镜头只在 routePoints 变化时执行一次(LaunchedEffect),
 *   不再放在每帧都会运行的 update 块里。
 * - 定位蓝点用 LOCATION_TYPE_SHOW,不随朝向旋转/不抢镜头。
 */
@Composable
fun NavigationMapView(
    modifier: Modifier = Modifier,
    routePoints: List<LatLng> = emptyList(),
    showMyLocation: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { TextureMapView(context) }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(Bundle())
        val aMap: AMap = mapView.map
        if (showMyLocation) {
            aMap.myLocationStyle = MyLocationStyle()
                .myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW)
                .interval(2000)
            aMap.isMyLocationEnabled = true
        }
        aMap.uiSettings.isZoomControlsEnabled = false

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

    // 仅在路线变化时重绘并调整视野一次,避免每帧重绘导致地图“跳动”。
    LaunchedEffect(routePoints) {
        val aMap = mapView.map ?: return@LaunchedEffect
        aMap.clear(true)
        if (routePoints.size >= 2) {
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .width(18f)
                    .color(0xFF2563EB.toInt()),
            )
            val builder = LatLngBounds.Builder()
            routePoints.forEach { builder.include(it) }
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
