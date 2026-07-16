package com.honglian.smartcycling.ui.components

import android.location.Location
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/**
 * turn-by-turn 语音诱导（纯框架实现，零原生导航引擎）。
 *
 * 第一性原则重构背景：
 * 旧方案基于高德导航引擎 AMapNavi（getInstance/startNavi/内置TTS），该引擎是 native(C/C++) 密集组件，
 * 其 native 崩溃会绕过所有 Java 层 runCatching 与全局 CrashHandler，直接杀进程回桌面且不留 Java 日志——
 * 这正是“点击开始骑行直接闪退到桌面、无崩溃弹窗”的病根。
 *
 * 现彻底移除 AMapNavi：
 * - 可见地图/路线/跟随仍由 NavigationMapView(地图 SDK) 负责，稳定可靠。
 * - 转向卡与里程/ETA 由“已规划路线折线 + App 真实定位”做纯几何推算得出。
 * - 语音走系统 TextToSpeech（中文），与任何第三方 native 库无关。
 * - 全程 runCatching 兜底：任何异常只影响语音提示，绝不影响地图或导致闪退。
 */
@Composable
fun NaviVoiceGuide(
    destination: LatLng,
    startPoint: LatLng?,
    currentLatLng: LatLng?,
    routePoints: List<LatLng> = emptyList(),
    enabled: Boolean,
    onNaviInfo: (NaviBannerInfo?) -> Unit = {},
    onRoutePath: (List<LatLng>) -> Unit = {},
) {
    val context = LocalContext.current
    val enabledState = rememberUpdatedState(enabled)
    val routeState = rememberUpdatedState(routePoints)
    val locState = rememberUpdatedState(currentLatLng)
    val destState = rememberUpdatedState(destination)
    val onNaviInfoState = rememberUpdatedState(onNaviInfo)

    val ttsReady = remember { mutableStateOf(false) }
    // 系统 TextToSpeech（中文）。构造与语言设置全部兜底，初始化失败仅静音、不影响导航。
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = runCatching {
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        // 依次尝试多个中文 Locale，任一可用即采用；均不可用则回退系统默认（尽力播报）。
                        val locales = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale.CHINESE)
                        val ok = locales.firstOrNull { lc ->
                            val r = engine?.setLanguage(lc)
                            r == TextToSpeech.LANG_AVAILABLE ||
                                r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                                r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                        }
                        if (ok == null) engine?.setLanguage(Locale.getDefault())
                        // 明确走“导航语音”音频通道，避免被普通媒体音量/静音策略错误路由。
                        engine?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        engine?.setSpeechRate(0.95f)
                    }
                    // 初始化成功后先播一段短提示，验证引擎与音频通道；主循环随后播报完整路线信息。
                    ttsReady.value = true
                    runCatching {
                        engine?.speak("骑行导航语音已开启", TextToSpeech.QUEUE_FLUSH, null, "nav_ready")
                    }
                }
            }
        }.getOrNull()
        engine
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
        }
    }

    // 语音关闭时立即静音
    LaunchedEffect(enabled) {
        if (!enabled) runCatching { tts?.stop() }
    }

    // 主循环：每秒推算进度/转向并驱动转向卡与语音里程碑。
    LaunchedEffect(Unit) {
        fun speak(text: String) {
            if (!enabledState.value || !ttsReady.value) return
            runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav") }
        }

        var announcedStart = false
        var announcedArrive = false
        var turnArmed = true

        while (true) {
            val here = locState.value
            if (here != null) {
                val info = computeGuidance(routeState.value, here, destState.value)
                onNaviInfoState.value.invoke(info)

                // 等 TTS 就绪再播报起步，避免初始化未完成时白白丢掉首播。
                if (!announcedStart && ttsReady.value) {
                    speak("开始骑行导航，全程约 %.1f 公里".format(info.routeRemainMeters / 1000.0))
                    announcedStart = true
                }
                // 转向播报：接近转向点（<120m）时播报一次，转向点远离后重新武装
                if (info.iconType != 1 && info.segRemainMeters in 1..120 && turnArmed) {
                    val dir = if (info.iconType == 2) "左转" else "右转"
                    speak("前方 %d 米%s".format(info.segRemainMeters, dir))
                    turnArmed = false
                } else if (info.iconType == 1 || info.segRemainMeters > 200) {
                    turnArmed = true
                }
                // 到达播报
                if (!announcedArrive && info.routeRemainMeters <= 50) {
                    speak("即将到达目的地")
                    announcedArrive = true
                }
            }
            delay(1000)
        }
    }
}

/** turn-by-turn 转向卡数据（由路线几何推算）。 */
data class NaviBannerInfo(
    val iconType: Int,
    val nextRoad: String,
    val segRemainMeters: Int,
    val routeRemainMeters: Int,
    val routeRemainSeconds: Int,
)

/**
 * 基于规划路线折线 + 当前位置推算导航信息：
 * - routeRemainMeters：沿路线到终点的剩余距离。
 * - iconType/segRemainMeters：前方最近一次明显转向（>=25°）的方向与距离；无转向则直行。
 * - routeRemainSeconds：按约 15km/h 估算的剩余时间。
 */
private fun computeGuidance(route: List<LatLng>, here: LatLng, dest: LatLng): NaviBannerInfo {
    if (route.size < 2) {
        val d = distMeters(here, dest).toInt()
        return NaviBannerInfo(1, "", d, d, estSeconds(d))
    }
    // 最近折线顶点
    var ni = 0
    var best = Double.MAX_VALUE
    for (i in route.indices) {
        val dd = distMeters(here, route[i])
        if (dd < best) {
            best = dd
            ni = i
        }
    }
    // 沿路线剩余距离
    var remaining = distMeters(here, route[ni])
    for (i in ni until route.size - 1) remaining += distMeters(route[i], route[i + 1])
    // 前方最近转向检测（500m 前瞻）
    var acc = distMeters(here, route[ni])
    var turnType = 1
    var segRemain = remaining.toInt()
    var found = false
    var i = ni
    while (i < route.size - 2 && acc <= 500.0) {
        val inB = bearingDeg(route[i], route[i + 1])
        val outB = bearingDeg(route[i + 1], route[i + 2])
        var delta = outB - inB
        while (delta > 180) delta -= 360
        while (delta < -180) delta += 360
        acc += distMeters(route[i], route[i + 1])
        if (abs(delta) >= 25.0) {
            turnType = if (delta > 0) 3 else 2 // 顺时针(正)=右转, 逆时针(负)=左转
            segRemain = acc.toInt()
            found = true
            break
        }
        i++
    }
    if (!found) {
        turnType = 1
        segRemain = remaining.toInt()
    }
    return NaviBannerInfo(turnType, "", segRemain, remaining.toInt(), estSeconds(remaining.toInt()))
}

private fun distMeters(a: LatLng, b: LatLng): Double {
    val r = FloatArray(2)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
    return r[0].toDouble()
}

private fun bearingDeg(a: LatLng, b: LatLng): Double {
    val r = FloatArray(2)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
    return r[1].toDouble()
}

/** 剩余时间估算：约 15km/h ≈ 4.2 m/s。 */
private fun estSeconds(meters: Int): Int = (meters / 4.2).toInt()
