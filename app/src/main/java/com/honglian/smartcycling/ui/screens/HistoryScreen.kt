package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.offline.OfflineLayerSpec
import com.honglian.smartcycling.offline.OfflineMapView
import com.honglian.smartcycling.offline.toWgs84
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 骑行历史:累计看板 + 记录列表 + 轨迹回放。 */
@Composable
fun HistoryScreen(
    rides: List<RideEntity>,
    onDelete: (Long) -> Unit,
    onGetTrackPoints: suspend (Long) -> List<TrackPointEntity>,
    onBack: () -> Unit,
    mapType: Int = 3,
    mapSource: MapSource = MapSource.ONLINE,
    offlineSpec: OfflineLayerSpec? = null,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var activeRide by remember { mutableStateOf<RideEntity?>(null) }
    var trackPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val totalDistance = remember(rides) { rides.sumOf { it.distanceKm } }
    val totalDuration = remember(rides) { rides.sumOf { it.durationSec } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .navigationBarsPadding()
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = palette.textPrimary)
                }
                Text(
                    "骑行历史",
                    style = MaterialTheme.typography.title,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(Modifier.padding(horizontal = Space.lg)) {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.lg),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat("累计里程", "%.1f".format(totalDistance), "km")
                        Stat("骑行次数", "${rides.size}", "次")
                        Stat("累计时长", "%.1f".format(totalDuration / 3600.0), "h")
                    }
                }
                Spacer(Modifier.height(Space.md))
            }

            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (rides.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无骑行记录,完成一次骑行后会显示在这里。",
                                style = MaterialTheme.typography.body,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
                items(rides, key = { it.id }) { ride ->
                    RideCard(
                        ride = ride,
                        dateText = fmt.format(Date(ride.startedAt)),
                        onClick = {
                            scope.launch {
                                loading = true
                                activeRide = ride
                                trackPoints = onGetTrackPoints(ride.id)
                                    .map { LatLng(it.latitude, it.longitude) }
                                loading = false
                            }
                        },
                        onDelete = { onDelete(ride.id) },
                    )
                }
                item { Spacer(Modifier.height(Space.lg)) }
            }
        }
    }

    activeRide?.let { ride ->
        AlertDialog(
            onDismissRequest = { activeRide = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = palette.surface,
            confirmButton = {},
            dismissButton = {},
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            fmt.format(Date(ride.startedAt)),
                            style = MaterialTheme.typography.subtitle,
                            color = palette.textPrimary,
                        )
                        Text(
                            "%.2f km · %s · 均速 %.1f km/h".format(
                                ride.distanceKm,
                                formatDuration(ride.durationSec),
                                ride.avgSpeedKmh,
                            ),
                            style = MaterialTheme.typography.caption,
                            color = palette.textTertiary,
                        )
                    }
                    IconButton(onClick = { activeRide = null }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = palette.textSecondary)
                    }
                }
            },
            text = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(palette.surfaceVariant),
                ) {
                    // 轨迹点存的是 GCJ-02(高德定位),离线底图按 WGS-84 网格渲染 →
                    // 离线分支必须先纠偏,否则历史轨迹会整体偏移 300~600 米。
                    val wgsTrack = remember(trackPoints) { trackPoints.toWgs84() }
                    when {
                        loading -> CircularProgressIndicator(
                            color = palette.primary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        mapSource == MapSource.OFFLINE && offlineSpec != null -> OfflineMapView(
                            spec = offlineSpec,
                            routePoints = wgsTrack,
                            destination = wgsTrack.lastOrNull(),
                            follow = false,
                            routeColor = palette.primary.toArgb(),
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> NavigationMapView(
                            modifier = Modifier.fillMaxSize(),
                            routePoints = trackPoints,
                            destination = trackPoints.lastOrNull(),
                            follow = false,
                            showMyLocation = false,
                            mapType = mapType,
                        )
                    }
                    if (!loading && trackPoints.isEmpty()) {
                        Text(
                            "该次骑行没有采集到有效轨迹点",
                            style = MaterialTheme.typography.body,
                            color = palette.textTertiary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String) {
    val palette = AppTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.display, color = palette.textPrimary)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = MaterialTheme.typography.caption, color = palette.textTertiary)
        }
        Text(label, style = MaterialTheme.typography.caption, color = palette.textSecondary)
    }
}

@Composable
private fun RideCard(
    ride: RideEntity,
    dateText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = AppTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.lg),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(palette.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsBike,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "%.2f km".format(ride.distanceKm),
                    style = MaterialTheme.typography.subtitle,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniFact(Icons.Outlined.Timer, formatDuration(ride.durationSec))
                    Spacer(Modifier.width(Space.md))
                    MiniFact(Icons.Outlined.Place, "均速 %.1f".format(ride.avgSpeedKmh))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.0f kcal · 爬升 %.0f m".format(ride.calories, ride.elevationGainM),
                    style = MaterialTheme.typography.caption,
                    color = palette.textSecondary,
                )
                Text(dateText, style = MaterialTheme.typography.caption, color = palette.textTertiary)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = palette.textTertiary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniFact(icon: ImageVector, text: String) {
    val palette = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.caption, color = palette.textSecondary)
    }
}

private fun formatDuration(sec: Long): String =
    "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
