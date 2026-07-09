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
 * - 开启自动锁车(autoLockCar):相机始终跟随并居中当前车辆位置(街道级),不停在全览。
 * - 语音使用高德内置语音播报(setUseInnerVoice),最稳定、无需系统 TTS 引擎;由 voiceEnabled 实时开关。
 * - 传入 startPoint(真实当前定位)作为算路起点,避免导航默认落到北京。经纬度骑行算路为免费接口。
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
    val naviView = remember { AMapNaviView(context) }
    val navi = remember { runCatching { AMapNavi.getInstance(appContext) }.getOrNull() }
    val destState = rememberUpdatedState(destination)
    val startState = rememberUpdatedState(startPoint)

    DisposableEffect(lifecycleOwner) {
        naviView.onCreate(Bundle())
        // 自动锁车:相机始终跟随并居中当前车辆位置,不停留在全览模式
        runCatching {
            val options = naviView.viewOptions
            options.setAutoLockCar(true)
            naviView.viewOptions = options
        }
        var attachedListener: SimpleNaviListener? = null
        if (navi != null) {
            val listener = NaviCallbacks(navi, destState, startState)
            navi.addAMapNaviListener(listener)
            attachedListener = listener
            // 默认开启内置语音播报
            runCatching { navi.setUseInnerVoice(voiceEnabled, false) }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> naviView.onResume()
                Lifecycle.Event.ON_PAUSE -> naviView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val l = attachedListener
            if (navi != null && l != null) {
                navi.stopNavi()
                navi.removeAMapNaviListener(l)
            }
            naviView.onDestroy()
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
 * 以构造参传入非空 AMapNavi,避免闭包中的可空智能转换问题。
 */
private class NaviCallbacks(
    private val navi: AMapNavi,
    private val destination: State<LatLng>,
    private val startPoint: State<LatLng?>,
) : SimpleNaviListener() {
    override fun onInitNaviSuccess() {
        val d = destination.value
        val s = startPoint.value
        if (s != null) {
            // 带真实起点算路:导航从用户当前位置开始,而非默认北京
            navi.calculateRideRoute(
                NaviLatLng(s.latitude, s.longitude),
                NaviLatLng(d.latitude, d.longitude),
            )
        } else {
            navi.calculateRideRoute(NaviLatLng(d.latitude, d.longitude))
        }
    }

    override fun onCalculateRouteSuccess(routeResult: AMapCalcRouteResult?) {
        navi.startNavi(NaviType.GPS)
    }
}
