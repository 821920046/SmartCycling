package com.honglian.smartcycling.ui.components

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.NaviLatLng

/**
 * 完整 turn-by-turn 骑行导航视图(基于 AMapNaviView)。
 * - 自动展示转向箭头、车道信息、剩余距离/时间等导航 UI。
 * - 开启自动锁车(autoLockCar):相机始终跟随并居中当前车辆位置(街道级)。
 * - 语音使用高德内置语音播报(setUseInnerVoice),由 voiceEnabled 实时开关。
 * - 健壮性:若导航 SDK 初始化失败(如 Key 未开通导航权限),
 *   自动回退到普通跟随地图,绝不闪退。
 */
/**
 * 安全从 Compose Context 中剥离出原始 Activity，防止部分 SDK 初始化因 ContextWrapper 抛异常。
 */
internal fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun NaviMapView(
    destination: LatLng,
    voiceEnabled: Boolean,
    routePoints: List<LatLng> = emptyList(),
    startPoint: LatLng? = null,
    currentLatLng: LatLng? = null,
    onExitRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
    mapType: Int = 3,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityContext = context.findActivity() ?: context

    // 导航视图/实例创建可能因 Key 未开通导航、资源缺失等抛异常。
    // 一律 runCatching 兑底,失败则 naviView 为 null 并回退到跟随地图。
    val naviView = remember { runCatching { AMapNaviView(activityContext) }.getOrNull() }
    val navi = remember { runCatching { AMapNavi.getInstance(appContext) }.getOrNull() }
    val destState = rememberUpdatedState(destination)
    val startState = rememberUpdatedState(startPoint)
    val exitState = rememberUpdatedState(onExitRequested)

    if (naviView == null) {
        // 回退:普通跟随地图 + 目的地标记(仍可正常骑行,只是无转向语音)
        NavigationMapView(
            modifier = modifier,
            routePoints = routePoints,
            destination = destination,
            follow = true,
            followLocation = currentLatLng,
            mapType = mapType,
        )
        return
    }

    DisposableEffect(lifecycleOwner) {
        runCatching { naviView.onCreate(Bundle()) }
        // 自动锁车 + 保留底图,但隐藏所有原生导航 UI 覆盖层(黑色转向面板/剩余距离时间/全览退出/速度圈)。
        // 做法:setLayoutVisible(true) 让底图正常渲染(setLayoutVisible(false) 会把底图也一起隐藏),
        // 再通过“隐藏所有不含地图的兄弟视图分支”把原生覆盖 UI 全部 GONE 掉,
        // 只留下干净的深色底图 + 蓝色路线 + 车标(即百度那种清爽导航样式)。
        // 转向提示仍由内置语音播报;速度/数据在右侧仪表盘显示。

        runCatching {
            val options = naviView.viewOptions
            options.setAutoLockCar(true)
            options.setLayoutVisible(true)
            options.isSettingMenuEnabled = false
            options.isTrafficBarEnabled = false
            options.isRouteListButtonShow = false
            options.isCrossDisplayShow = false
            options.isTrafficLine = false
            naviView.viewOptions = options
        }
        // 首次布局后隐藏原生覆盖层,并在 SDK 因导航事件重新显示时持续隐藏。
        val hideOverlays = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            runCatching { hideNativeNaviOverlays(naviView) }
        }
        runCatching { naviView.viewTreeObserver.addOnGlobalLayoutListener(hideOverlays) }
        var attachedListener: SimpleNaviListener? = null
        if (navi != null) {
            val listener = NaviCallbacks(navi, destState, startState, exitState)
            runCatching { navi.addAMapNaviListener(listener) }
            attachedListener = listener
            runCatching { navi.setUseInnerVoice(voiceEnabled, false) }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { naviView.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { naviView.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { naviView.viewTreeObserver.removeOnGlobalLayoutListener(hideOverlays) }
            val l = attachedListener
            if (navi != null && l != null) {
                runCatching { navi.stopNavi() }
                runCatching { navi.removeAMapNaviListener(l) }
            }
            runCatching { naviView.onDestroy() }
            runCatching { AMapNavi.destroy() }
        }
    }

    // 语音开关:实时切换高德内置语音播报
    LaunchedEffect(voiceEnabled, navi) {
        runCatching { navi?.setUseInnerVoice(voiceEnabled, false) }
    }

    // 动态监听地图样式变化
    LaunchedEffect(mapType) {
        val aMap = naviView?.map ?: return@LaunchedEffect
        runCatching {
            aMap.mapType = when (mapType) {
                1 -> com.amap.api.maps.AMap.MAP_TYPE_NORMAL
                2 -> com.amap.api.maps.AMap.MAP_TYPE_SATELLITE
                else -> com.amap.api.maps.AMap.MAP_TYPE_NIGHT
            }
        }
    }

    AndroidView(factory = { naviView!! }, modifier = modifier)
}


/**
 * 导航回调(继承官方空实现适配器 SimpleNaviListener,仅重写所需方法)。
 * - 退出拦截: 劫持所有 SDK 原生退出/到达行为，重定向到外部自定义大红按钮逻辑。
 */
private class NaviCallbacks(
    private val navi: AMapNavi,
    private val destination: State<LatLng>,
    private val startPoint: State<LatLng?>,
    private val onExitRequested: State<() -> Unit>,
) : SimpleNaviListener() {
    override fun onInitNaviSuccess() {
        val d = destination.value
        val s = startPoint.value
        runCatching {
            if (s != null) {
                navi.calculateRideRoute(
                    NaviLatLng(s.latitude, s.longitude),
                    NaviLatLng(d.latitude, d.longitude),
                )
            } else {
                navi.calculateRideRoute(NaviLatLng(d.latitude, d.longitude))
            }
        }
    }

    override fun onCalculateRouteSuccess(routeResult: AMapCalcRouteResult?) {
        runCatching { navi.startNavi(NaviType.GPS) }
    }

    /** 劫持 SDK 原生退出按钮/NaviUI 退出: 重定向到外部自定义退出逻辑 */
    override fun onNaviCancel() = runCatching { onExitRequested.value() }

    /** 到达目的地后的行为: 不做强制退出, 仅记录日志, 由用户手动按大红按钮结束 */
    override fun onArrivedDestination() { /* no-op — user decides when to stop */ }

    /** 兜底: 部分情况下 SDK 仅回调此方法表示导航结束 */
    override fun onEndEmulatorNavi() = runCatching { onExitRequested.value() }

}

/** 判断某视图子树中是否包含地图渲染面(用于在隐藏原生覆盖层时保留底图)。 */
private fun viewContainsMapSurface(v: android.view.View): Boolean {
    if (v is android.view.SurfaceView || v is android.view.TextureView || v is android.opengl.GLSurfaceView) return true
    val cn = v.javaClass.name
    if (cn.contains("MapView", true) || cn.contains("GLMapView", true) || cn.contains("TextureMapView", true)) return true
    if (v is android.view.ViewGroup) {
        for (i in 0 until v.childCount) {
            if (viewContainsMapSurface(v.getChildAt(i))) return true
        }
    }
    return false
}

/**
 * 隐藏 AMapNaviView 的所有原生 UI 覆盖层,仅保留底图分支。
 * 递归:只深入“包含地图”的分支去隐藏其覆盖兄弟;凡是“不含地图”的分支整体 GONE。
 * 与具体控件层级无关,稳健地移除黑色转向面板、剩余距离时间、全览/退出、速度圈等。
 */
private fun hideNativeNaviOverlays(v: android.view.View) {
    if (v !is android.view.ViewGroup) return
    for (i in 0 until v.childCount) {
        val c = v.getChildAt(i)
        if (viewContainsMapSurface(c)) {
            hideNativeNaviOverlays(c)
        } else if (c.visibility != android.view.View.GONE) {
            c.visibility = android.view.View.GONE
        }
    }
}
