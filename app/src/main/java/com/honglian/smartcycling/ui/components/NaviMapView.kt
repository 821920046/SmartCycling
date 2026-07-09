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
@Composable
fun NaviMapView(
    destination: LatLng,
    voiceEnabled: Boolean,
    startPoint: LatLng? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    // 导航视图/实例创建可能因 Key 未开通导航、资源缺失等抛异常。
    // 一律 runCatching 兑底,失败则 naviView 为 null 并回退到跟随地图。
    val naviView = remember { runCatching { AMapNaviView(context) }.getOrNull() }
    val navi = remember { runCatching { AMapNavi.getInstance(appContext) }.getOrNull() }
    val destState = rememberUpdatedState(destination)
    val startState = rememberUpdatedState(startPoint)

    if (naviView == null) {
        // 回退:普通跟随地图 + 目的地标记(仍可正常骑行,只是无转向语音)
        NavigationMapView(
            modifier = modifier,
            destination = destination,
            follow = true,
        )
        return
    }

    DisposableEffect(lifecycleOwner) {
        runCatching { naviView.onCreate(Bundle()) }
        // 自动锁车:相机始终跟随并居中当前车辆位置,不停留在全览模式
        runCatching {
            val options = naviView.viewOptions
            options.setAutoLockCar(true)
            naviView.viewOptions = options
        }
        var attachedListener: SimpleNaviListener? = null
        if (navi != null) {
            val listener = NaviCallbacks(navi, destState, startState)
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

    AndroidView(factory = { naviView }, modifier = modifier)
}

/**
 * 导航回调(继承官方空实现适配器 SimpleNaviListener,仅重写所需方法)。
 */
private class NaviCallbacks(
    private val navi: AMapNavi,
    private val destination: State<LatLng>,
    private val startPoint: State<LatLng?>,
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
}
