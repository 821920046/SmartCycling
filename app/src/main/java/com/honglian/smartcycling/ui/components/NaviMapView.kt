package com.honglian.smartcycling.ui.components

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.honglian.smartcycling.navi.NaviTts

/**
 * 完整 turn-by-turn 骑行导航视图(基于 AMapNaviView)。
 * - 自动展示转向箭头、车道信息、剩余距离/时间等导航 UI。
 * - 语音播报走系统 TTS,由 voiceEnabled 实时开关。
 * - 不带起点算路(calculateRideRoute(to)),以当前定位为起点,经纬度骑行算路为免费接口。
 */
@Composable
fun NaviMapView(
    destination: LatLng,
    voiceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val naviView = remember { AMapNaviView(context) }
    val tts = remember { NaviTts(context) }
    val voiceState = rememberUpdatedState(voiceEnabled)
    val destState = rememberUpdatedState(destination)

    DisposableEffect(lifecycleOwner) {
        naviView.onCreate(Bundle())
        val navi = runCatching { AMapNavi.getInstance(context.applicationContext) }.getOrNull()
        var attachedListener: SimpleNaviListener? = null
        if (navi != null) {
            // 关闭内置语音但回调播报文本,交由系统 TTS 播报(便于开关)
            navi.setUseInnerVoice(false, true)
            val listener = NaviCallbacks(navi, destState, voiceState, tts)
            navi.addAMapNaviListener(listener)
            attachedListener = listener
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
            tts.shutdown()
        }
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
    private val voiceEnabled: State<Boolean>,
    private val tts: NaviTts,
) : SimpleNaviListener() {
    override fun onInitNaviSuccess() {
        val d = destination.value
        navi.calculateRideRoute(NaviLatLng(d.latitude, d.longitude))
    }

    override fun onCalculateRouteSuccess(routeResult: AMapCalcRouteResult?) {
        navi.startNavi(NaviType.GPS)
    }

    override fun onGetNavigationText(type: Int, text: String?) {
        if (voiceEnabled.value && !text.isNullOrBlank()) tts.speak(text)
    }

    override fun onArriveDestination() {
        if (voiceEnabled.value) tts.speak("您已到达目的地,本次骑行结束")
    }
}
