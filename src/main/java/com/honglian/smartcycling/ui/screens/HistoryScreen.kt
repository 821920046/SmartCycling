package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 极致科幻霓虹骑行历史统计与回顾页面。 */
@Composable
fun HistoryScreen(
    rides: List<RideEntity>,
    onDelete: (Long) -> Unit,
    onGetTrackPoints: suspend (Long) -> List<TrackPointEntity>,
    onBack: () -> Unit,
    mapType: Int = 3,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    // 轨迹弹窗状态
    var activeRideForTrack by remember { mutableStateOf<RideEntity?>(null) }
    var trackPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var loadingTrack by remember { mutableStateOf(false) }

    // 统计大看板数值
    val totalDistance = remember(rides) { rides.sumOf { it.distanceKm } }
    val totalRides = remember(rides) { rides.size }
    val totalDurationSec = remember(rides) { rides.sumOf { it.durationSec } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelBgTop, PanelBgBottom)))
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            
            // 顶栏面板
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "骑行历史回顾", 
                    fontSize = 22.sp, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = SpeedText
                )
                TextButton(onClick = onBack) {
                    Text("返回", color = BrandCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // 1. 霓虹统计大看板
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBox(value = "%.1f".format(totalDistance), unit = "km", label = "累计里程")
                    StatBox(value = "$totalRides", unit = "次", label = "骑行次数")
                    StatBox(value = formatDurationHours(totalDurationSec), unit = "小时", label = "累计时长")
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. 骑行列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (rides.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无本地骑行记录，快去骑行吧！", color = DataLabel, fontSize = 14.sp)
                        }
                    }
                }
                items(rides, key = { it.id }) { ride ->
                    HistoryCard(
                        ride = ride,
                        dateFormat = fmt,
                        onClick = {
                            coroutineScope.launch {
                                loadingTrack = true
                                activeRideForTrack = ride
                                val points = onGetTrackPoints(ride.id)
                                trackPoints = points.map { LatLng(it.latitude, it.longitude) }
                                loadingTrack = false
                            }
                        },
                        onDelete = { onDelete(ride.id) }
                    )
                }
            }
        }
    }

    // 轨迹回顾地图大弹窗
    activeRideForTrack?.let { ride ->
        AlertDialog(
            onDismissRequest = { activeRideForTrack = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {},
            dismissButton = {},
            containerColor = CardBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp)),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(fmt.format(Date(ride.startedAt)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SpeedText)
                        Text("里程: %.2f km".format(ride.distanceKm), fontSize = 12.sp, color = DataLabel)
                    }
                    IconButton(onClick = { activeRideForTrack = null }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = StopRed)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelBg)
                ) {
                    if (loadingTrack) {
                        CircularProgressIndicator(
                            color = BrandCyan,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        NavigationMapView(
                            modifier = Modifier.fillMaxSize(),
                            routePoints = trackPoints,
                            destination = trackPoints.lastOrNull(),
                            follow = false,
                            showMyLocation = false,
                            mapType = mapType
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun StatBox(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Black, 
                color = BrandCyan, 
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 11.sp, color = DataLabel, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = DataLabel)
    }
}

@Composable
private fun HistoryCard(
    ride: RideEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsBike, 
                        contentDescription = null, 
                        tint = BrandCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        dateFormat.format(Date(ride.startedAt)), 
                        fontSize = 12.sp, 
                        color = DataLabel,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "%.2f km · %s · 均速 %.1f".format(
                            ride.distanceKm,
                            formatDuration(ride.durationSec),
                            ride.avgSpeedKmh,
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpeedText
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%.0f kcal · 爬升 %.0f m".format(ride.calories, ride.elevationGainM),
                        fontSize = 12.sp,
                        color = DataLabel
                    )
                }
            }
            
            // 删除小图标
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = StopRed.copy(alpha = 0.8f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatDuration(sec: Long): String =
    "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)

private fun formatDurationHours(sec: Long): String =
    "%.1f".format(sec / 3600.0)

