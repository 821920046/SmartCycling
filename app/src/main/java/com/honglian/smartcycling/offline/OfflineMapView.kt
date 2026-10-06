package com.honglian.smartcycling.offline

import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * 离线地图渲染视图(Compose 包装 osmdroid)。
 *
 * 坐标契约:**所有入参均为 WGS-84**。坐标系纠偏在瓦片读取层完成
 * (见 [CrsRemapArchiveFile]),因此相机、覆盖物、GPS 三者天然自洽,
 * 调用方无需关心底图是 GCJ-02 还是 BD-09。
 */
@Composable
fun OfflineMapView(
    spec: OfflineLayerSpec,
    routePoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    currentLocation: LatLng? = null,
    follow: Boolean = false,
    routeColor: Int = AndroidColor.parseColor("#1B6EF3"),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val routeOverlay = remember { Polyline(mapView) }
    val destMarker = remember { Marker(mapView) }
    val myMarker = remember { Marker(mapView) }

    // 一次性基础配置
    DisposableEffect(mapView) {
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.setTilesScaledToDpi(true)
        // 纯离线:禁止任何瓦片网络请求(离线包缺瓦片时保持空白,不静默联网)。
        mapView.setUseDataConnection(false)
        mapView.setMinZoomLevel(2.0)
        mapView.setMaxZoomLevel(22.0)

        routeOverlay.setColor(routeColor)
        routeOverlay.setWidth(10f)

        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        destMarker.icon = pinDrawable(AndroidColor.parseColor("#E5484D"))
        destMarker.title = context.getString(R.string.map_marker_destination)

        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        myMarker.icon = dotDrawable(AndroidColor.parseColor("#1B6EF3"), AndroidColor.WHITE)

        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(destMarker)
        mapView.overlays.add(myMarker)

        // 组合进入时 Activity 往往已处于 RESUMED,不会再收到 ON_RESUME 事件,
        // 必须显式调用一次,否则 osmdroid 的瓦片加载线程池不会启动。
        runCatching { mapView.onResume() }

        onDispose {
            runCatching { mapView.onPause() }
            runCatching { mapView.onDetach() }
        }
    }

    // 主题切换 → 路线颜色跟随。
    // DisposableEffect 只在首次组合执行一次,无法响应之后的主题变化,故单独监听 routeColor。
    LaunchedEffect(routeColor) {
        routeOverlay.setColor(routeColor)
        mapView.invalidate()
    }

    // 生命周期
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapView.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapView.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 切换地图包 → 重建瓦片提供器
    LaunchedEffect(spec) {
        // 打开 SQLite / 读取 ZIP 中央目录都是磁盘 IO,必须离开主线程,否则大包会卡帧甚至 ANR。
        val provider = withContext(Dispatchers.IO) {
            OfflineTileProviderFactory.create(context, spec)
        }
        if (provider != null) {
            mapView.setTileProvider(provider)
            mapView.setMinZoomLevel(spec.minZoom.toDouble().coerceAtLeast(0.0))
            mapView.setMaxZoomLevel(spec.maxZoom.toDouble().coerceAtLeast(spec.minZoom.toDouble()))
        }

        val bounds = wgsBoundsOf(spec)
        if (bounds != null) {
            val spanLat = bounds.latNorth - bounds.latSouth
            val spanLon = bounds.lonEast - bounds.lonWest
            // 覆盖范围过小时不设滚动限制:否则 osmdroid 会把最小缩放一并抬高,用户反而"被锁死"。
            val limit = if (spanLat > MIN_LIMIT_SPAN && spanLon > MIN_LIMIT_SPAN) {
                val padLat = spanLat * 0.25 + 0.01
                val padLon = spanLon * 0.25 + 0.01
                BoundingBox(
                    bounds.latNorth + padLat,
                    bounds.lonEast + padLon,
                    bounds.latSouth - padLat,
                    bounds.lonWest - padLon,
                )
            } else {
                null
            }
            // 必须"无条件"调用:否则从有范围的包切到无范围的包时,上一个包的滚动限制会残留,
            // 把用户锁死在旧范围里(osmdroid 只在传入 null 时才清除限制)。
            mapView.setScrollableAreaLimitDouble(limit)
            mapView.controller.setCenter(
                GeoPoint((bounds.latNorth + bounds.latSouth) / 2, (bounds.lonEast + bounds.lonWest) / 2),
            )
        } else {
            // 新包没有覆盖范围信息 → 显式清空可能残留的旧限制。
            mapView.setScrollableAreaLimitDouble(null)
        }
        mapView.controller.setZoom(
            (spec.minZoom + (spec.maxZoom - spec.minZoom) / 2).toDouble().coerceAtLeast(3.0),
        )
        mapView.invalidate()
    }

    // 路线 + 目的地
    LaunchedEffect(routePoints, destination, mapView) {
        routeOverlay.setPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) })
        if (destination != null) {
            destMarker.position = GeoPoint(destination.latitude, destination.longitude)
            destMarker.isEnabled = true
        } else {
            destMarker.isEnabled = false
        }
        if (routePoints.size >= 2 && !follow) {
            runCatching {
                mapView.zoomToBoundingBox(
                    BoundingBox.fromGeoPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) }),
                    true,
                    64,
                )
            }
        } else if (destination != null && !follow) {
            mapView.controller.setCenter(GeoPoint(destination.latitude, destination.longitude))
        }
        mapView.invalidate()
    }

    // 当前位置
    LaunchedEffect(currentLocation, follow, mapView) {
        val loc = currentLocation
        if (loc == null) {
            myMarker.isEnabled = false
        } else {
            myMarker.isEnabled = true
            myMarker.position = GeoPoint(loc.latitude, loc.longitude)
            if (follow) mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
        }
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 离线包覆盖范围(WGS-84)。 */
data class WgsBounds(
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double,
)

/** 覆盖范围小于该跨度(度)时不施加滚动/缩放限制,避免把用户锁死在极小区域。 */
private const val MIN_LIMIT_SPAN = 0.05

/** 把包自身的 bounds(包坐标系)换算成 WGS-84 包围盒;无有效 bounds 返回 null。 */
private fun wgsBoundsOf(spec: OfflineLayerSpec): WgsBounds? {
    val parts = spec.bounds?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
    if (parts.size != 4) return null
    val (south, west, north, east) = parts
    if (south >= north || west >= east) return null
    val corners = listOf(
        doubleArrayOf(south, west), doubleArrayOf(south, east),
        doubleArrayOf(north, west), doubleArrayOf(north, east),
    ).map { GeoTransform.convert(it[0], it[1], spec.crs, MapCrs.WGS84) }
    return WgsBounds(
        latNorth = corners.maxOf { it[0] },
        lonEast = corners.maxOf { it[1] },
        latSouth = corners.minOf { it[0] },
        lonWest = corners.minOf { it[1] },
    )
}

private fun dotDrawable(fill: Int, stroke: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
    setStroke(6, stroke)
    setSize(48, 48)
}

private fun pinDrawable(fill: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
    setStroke(6, AndroidColor.WHITE)
    setSize(40, 40)
}
