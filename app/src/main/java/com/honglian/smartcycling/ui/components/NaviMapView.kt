package com.honglian.smartcycling.ui.components

import android.location.Location
import android.os.Handler
import android.os.Looper
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
import com.amap.api.navi.model.NaviInfo
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
    onNaviInfo: (NaviBannerInfo?) -> Unit = {},
    onRoutePath: (List<LatLng>) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val navi = remember { runCatching { AMapNavi.getInstance(appContext) }.getOrNull() }
    val destState = rememberUpdatedState(destination)
    val startState = rememberUpdatedState(startPoint)
    val locState = rememberUpdatedState(currentLatLng)
    val onNaviInfoState = rememberUpdatedState(onNaviInfo)
    val onRoutePathState = rememberUpdatedState(onRoutePath)
    // 引擎回调在导航线程触发,统一切回主线程再更新 Compose 状态。
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(navi) {
        val n = navi
        var listener: SimpleNaviListener? = null
        if (n != null) {
            runCatching { n.setUseInnerVoice(enabled, false) }
            // 必须在 startNavi 之前开启外部 GPS 喂数
            runCatching { n.setIsUseExtraGPSData(true) }
            var naviStarted = false
            val l = object : SimpleNaviListener() {
                override fun onInitNaviSuccess() {
                    requestRoute(n, startState.value, destState.value)
                }

                override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
                    // 首次算路成功才 startNavi;偏航重算的后续成功不重复启动(引擎自动续航)。
                    if (!naviStarted) {
                        runCatching { n.startNavi(NaviType.GPS) }
                        naviStarted = true
                    }
                    // 把引擎算出的真实路线上抛,驱动可见地图重绘(支持偏航重算后路线更新)。
                    runCatching {
                        val path = n.naviPath?.coordList?.map { LatLng(it.latitude, it.longitude) }
                        if (!path.isNullOrEmpty()) mainHandler.post { onRoutePathState.value(path) }
                    }
                }

                // 偏航:引擎会自动重新算路,onCalculateRouteSuccess 会带来新路线并重绘,无需手动干预。
                override fun onReCalculateRouteForYaw() {}

                override fun onNaviInfoUpdate(info: NaviInfo?) {
                    val banner = if (info == null) null else runCatching {
                        NaviBannerInfo(
                            iconType = info.iconType,
                            nextRoad = info.nextRoadName ?: "",
                            segRemainMeters = info.curStepRetainDistance,
                            routeRemainMeters = info.pathRetainDistance,
                            routeRemainSeconds = info.pathRetainTime,
                        )
                    }.getOrNull()
                    mainHandler.post { onNaviInfoState.value(banner) }
                }

                override fun onArriveDestination() {
                    mainHandler.post { onNaviInfoState.value(null) }
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

/** turn-by-turn 转向卡数据(从 AMap 导航引擎的实时 NaviInfo 提炼)。 */
data class NaviBannerInfo(
    val iconType: Int,
    val nextRoad: String,
    val segRemainMeters: Int,
    val routeRemainMeters: Int,
    val routeRemainSeconds: Int,
)

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
