package com.honglian.smartcycling.ui.components

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.NaviLatLng
import kotlinx.coroutines.delay

/**
 * 无界面(headless)turn-by-turn 语音诱导。
 *
 * 第一性原则重构背景:
 * 旧方案 NaviMapView 基于 AMapNaviView + startNavi(NaviType.GPS),靠导航引擎自己的内部
 * 定位器驱动镜头与车标,但从未向引擎喂定位(无 setExtraGPSData),
 * 也完全忽略了 App 自己的真实定位 → 引擎内部定位器无定位,镜头停在高德默认中心(北京)。
 *
 * 现将“可见地图”交给可靠的 NavigationMapView(TextureMapView + 高德自带实时跟随),
 * 本组件仅保留“语音”职责:
 * - 不创建任何 AMapNaviView(无界面),彻底规避镜头停北京的问题。
 * - 用 AMapNavi 引擎算路 + 内置语音播报,并用 App 真实定位(GCJ-02)持续喂给引擎。
 * - 全程 runCatching 兢底:任何失败都只影响语音,绝不影响地图或导致闪退。
 */
@Composable
fun NaviVoiceGuide(
    destination: LatLng,
    startPoint: LatLng?,
    currentLatLng: LatLng?,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val navi = remember { runCatching { AMapNavi.getInstance(appContext) }.getOrNull() }
    val destState = rememberUpdatedState(destination)
    val startState = rememberUpdatedState(startPoint)
    val locState = rememberUpdatedState(currentLatLng)

    DisposableEffect(navi) {
        val n = navi
        var listener: SimpleNaviListener? = null
        if (n != null) {
            runCatching { n.setUseInnerVoice(enabled, false) }
            // 必须在 startNavi 之前开启外部 GPS 喂数
            runCatching { n.setIsUseExtraGPSData(true) }
            val l = object : SimpleNaviListener() {
                override fun onInitNaviSuccess() {
                    requestRoute(n, startState.value, destState.value)
                }

                override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
                    runCatching { n.startNavi(NaviType.GPS) }
                }
            }
            runCatching { n.addAMapNaviListener(l) }
            listener = l
        }
        onDispose {
            val l = listener
            if (n != null && l != null) {
                runCatching { n.stopNavi() }
                runCatching { n.removeAMapNaviListener(l) }
            }
            runCatching { AMapNavi.destroy() }
        }
    }

    // 兢底:若监听器注册时引擎已初始化完毕(onInitNaviSuccess 不再重发),延时主动算一次路。
    LaunchedEffect(navi) {
        val n = navi ?: return@LaunchedEffect
        delay(1500)
        requestRoute(n, startState.value, destState.value)
    }

    // 语音开关实时切换
    LaunchedEffect(enabled, navi) {
        runCatching { navi?.setUseInnerVoice(enabled, false) }
    }

    // 用 App 真实定位(GCJ-02)持续喂给导航引擎,让语音基于真实位置播报。
    LaunchedEffect(navi) {
        val n = navi ?: return@LaunchedEffect
        var prev: Location? = null
        while (true) {
            val cur = locState.value
            if (cur != null) {
                val loc = Location("gps").apply {
                    latitude = cur.latitude
                    longitude = cur.longitude
                    accuracy = 5f
                    time = System.currentTimeMillis()
                    val p = prev
                    if (p != null) {
                        val d = p.distanceTo(this)
                        if (d >= 0.8f) {
                            bearing = p.bearingTo(this)
                            speed = d / 0.8f
                        } else {
                            bearing = p.bearing
                        }
                    }
                }
                runCatching { n.setExtraGPSData(2, loc) } // 2 = GCJ-02
                prev = loc
            }
            delay(800)
        }
    }
}

/** 发起骑行算路(有真实起点则用起点，否则由引擎自定位起点)。 */
private fun requestRoute(navi: AMapNavi, start: LatLng?, dest: LatLng) {
    runCatching {
        if (start != null) {
            navi.calculateRideRoute(
                NaviLatLng(start.latitude, start.longitude),
                NaviLatLng(dest.latitude, dest.longitude),
            )
        } else {
            navi.calculateRideRoute(NaviLatLng(dest.latitude, dest.longitude))
        }
    }
}
