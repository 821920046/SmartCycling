package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.ride.RideState
import com.honglian.smartcycling.ride.SensorMode
import com.honglian.smartcycling.ride.SpeedSource
import com.honglian.smartcycling.ui.components.DataGrid
import com.honglian.smartcycling.ui.components.NaviMapView
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.components.SpeedRing
import com.honglian.smartcycling.ui.theme.BrandCyan
import com.honglian.smartcycling.ui.theme.CardBg
import com.honglian.smartcycling.ui.theme.DataLabel
import com.honglian.smartcycling.ui.theme.DividerNavy
import com.honglian.smartcycling.ui.theme.PanelBg
import com.honglian.smartcycling.ui.theme.PanelBgBottom
import com.honglian.smartcycling.ui.theme.PanelBgTop
import com.honglian.smartcycling.ui.theme.StopRed
import kotlin.math.min

/**
 * 骑行中数据界面:左导航 / 右仪表盘(横屏)。
 * - 有目的地时左侧为完整 turn-by-turn 导航(转向箭头+语音,带真实起点);否则为跟随地图。
 * - 左上角提供语音开关(悬浮、置顶、加大点击区域)。
 * - 右侧仪表盘按屏幕高度自适应速度环尺寸,保证整屏可见、无需滚动。
 */
@Composable
fun RideScreen(
    state: RideState,
    routePoints: List<LatLng> = emptyList(),
    destination: LatLng? = null,
    startPoint: LatLng? = null,
    currentLatLng: LatLng? = null,
    voiceEnabled: Boolean = true,
    onToggleVoice: () -> Unit = {},
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().background(PanelBg)) {
        // 左:导航
        Box(Modifier.weight(1.3f).fillMaxHeight()) {
            if (destination != null) {
                NaviMapView(
                    destination = destination,
                    voiceEnabled = voiceEnabled,
                    startPoint = startPoint,
                    currentLatLng = currentLatLng,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    onClick = onToggleVoice,
                    shape = RoundedCornerShape(22.dp),
                    color = if (voiceEnabled) BrandCyan else Color(0xFF37424F),
                    contentColor = if (voiceEnabled) Color(0xFF04121A) else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(10.dp)
                        .zIndex(2f),
                ) {
                    Text(
                        if (voiceEnabled) "🔊 语音开" else "🔇 语音关",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            } else {
                NavigationMapView(
                    modifier = Modifier.fillMaxSize(),
                    routePoints = routePoints,
                    follow = true,
                    followLocation = currentLatLng,
                )
            }
        }
        // 左右之间的细分隔线,让地图与仪表盘有自然过渡
        Box(Modifier.fillMaxHeight().width(1.dp).background(DividerNavy))
        // 右:数据仪表盘(按屏幕高度自适应,保证不滚动即可整屏显示)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(PanelBgTop, PanelBgBottom)))
                .safeDrawingPadding(),
        ) {
            // 预留"标签+数据卡+结束按钮+间距"所需高度,其余高度给速度环;并按面板宽度上限收敛
            val maxRing = min(maxWidth.value * 0.88f, 224f).coerceAtLeast(96f)
            val ring = (maxHeight - 188.dp).coerceIn(96.dp, maxRing.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val cadenceMode = state.sensorMode == SensorMode.CADENCE
                SpeedRing(
                    value = if (cadenceMode) state.cadenceRpm else state.speedKmh,
                    unit = if (cadenceMode) "rpm" else "km/h",
                    maxValue = if (cadenceMode) 120.0 else 60.0,
                    diameterDp = ring.value.toInt(),
                )
                Text(
                    when {
                        cadenceMode -> "踏频 · 实时 rpm"
                        state.speedSource == SpeedSource.SENSOR_WHEEL -> "速度来源 · 传感器"
                        else -> "速度来源 · GPS"
                    },
                    fontSize = 11.sp,
                    color = DataLabel,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                ) {
                    DataGrid(state, Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onStop,
                    shape = RoundedCornerShape(12.dp),
                    color = StopRed,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().height(46.dp).zIndex(2f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("结束骑行", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
