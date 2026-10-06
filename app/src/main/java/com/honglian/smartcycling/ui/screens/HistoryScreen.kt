package com.honglian.smartcycling.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.R
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.core.UnitSystem
import com.honglian.smartcycling.core.Units
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.export.RideExporter
import com.honglian.smartcycling.offline.OfflineLayerSpec
import com.honglian.smartcycling.offline.OfflineMapView
import com.honglian.smartcycling.offline.toWgs84
import com.honglian.smartcycling.ride.LapSplit
import com.honglian.smartcycling.ride.Laps
import com.honglian.smartcycling.ui.components.NavigationMapView
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.AppType
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
    onExportRide: (RideEntity, Uri) -> Unit = { _, _ -> },
    onExportAll: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val units = AppTheme.units
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var activeRide by remember { mutableStateOf<RideEntity?>(null) }
    var trackPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // 原始轨迹点(保留海拔/心率/时间戳),用于在详情里现算分圈 ——
    // 上面那份 trackPoints 已被降维成 LatLng,不足以推导分圈。
    var rawPoints by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // —— GPX 导出(SAF,用户自选保存位置) ——
    var pendingRide by remember { mutableStateOf<RideEntity?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    val exportRideLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RideExporter.MIME_GPX),
    ) { uri: Uri? ->
        val ride = pendingRide
        pendingRide = null
        if (uri != null && ride != null) onExportRide(ride, uri)
    }
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RideExporter.MIME_GPX),
    ) { uri: Uri? ->
        val all = pendingAll
        pendingAll = false
        if (uri != null && all) onExportAll(uri)
    }

    val totalDistance = remember(rides) { rides.sumOf { it.distanceKm } }
    val totalDuration = remember(rides) { rides.sumOf { it.durationSec } }

    // 个人纪录:从已有记录里取各项极值(参考 Strava / OpenTracks 的 PR 展示)
    val prDistance = remember(rides) { rides.maxOfOrNull { it.distanceKm } ?: 0.0 }
    val prSpeed = remember(rides) { rides.maxOfOrNull { it.avgSpeedKmh } ?: 0.0 }
    val prDuration = remember(rides) { rides.maxOfOrNull { it.durationSec } ?: 0L }
    val prElevation = remember(rides) { rides.maxOfOrNull { it.elevationGainM } ?: 0.0 }

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
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = palette.textPrimary)
                }
                Text(
                    stringResource(R.string.map_nav_history),
                    style = AppType.title,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (rides.isNotEmpty()) {
                    IconButton(onClick = {
                        pendingAll = true
                        runCatching { exportAllLauncher.launch("SmartCycling-all.gpx") }
                    }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = stringResource(R.string.history_export_all),
                            tint = palette.textSecondary,
                        )
                    }
                }
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
                        Stat(stringResource(R.string.history_total_distance), "%.1f".format(Units.distance(totalDistance, units)), Units.distanceUnit(units))
                        Stat(stringResource(R.string.history_ride_count), "${rides.size}", stringResource(R.string.history_unit_times))
                        Stat(stringResource(R.string.history_total_duration), "%.1f".format(totalDuration / 3600.0), "h")
                    }
                }

                if (rides.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    Surface(
                        shape = RoundedCornerShape(Radius.lg),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Space.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.EmojiEvents,
                                    contentDescription = null,
                                    tint = palette.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(Space.xs))
                                Text(
                                    stringResource(R.string.history_personal_records),
                                    style = AppType.subtitle,
                                    color = palette.textPrimary,
                                )
                            }
                            Spacer(Modifier.height(Space.sm))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat(stringResource(R.string.history_pr_distance), "%.1f".format(Units.distance(prDistance, units)), Units.distanceUnit(units))
                                Stat(stringResource(R.string.history_pr_speed), "%.1f".format(Units.speed(prSpeed, units)), Units.speedUnit(units))
                            }
                            Spacer(Modifier.height(Space.sm))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat(stringResource(R.string.history_pr_duration), formatDuration(prDuration), "")
                                Stat(stringResource(R.string.history_pr_elevation), "%.0f".format(Units.elevation(prElevation, units)), Units.elevationUnit(units))
                            }
                        }
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
                                stringResource(R.string.history_empty),
                                style = AppType.body,
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
                                val pts = onGetTrackPoints(ride.id)
                                rawPoints = pts
                                trackPoints = pts.map { LatLng(it.latitude, it.longitude) }
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
                            style = AppType.subtitle,
                            color = palette.textPrimary,
                        )
                        Text(
                            stringResource(
                                R.string.history_ride_subtitle,
                                Units.distanceText(ride.distanceKm, units),
                                formatDuration(ride.durationSec),
                                Units.speedText(ride.avgSpeedKmh, units),
                            ),
                            style = AppType.caption,
                            color = palette.textTertiary,
                        )
                    }
                    IconButton(onClick = {
                        pendingRide = ride
                        runCatching { exportRideLauncher.launch(RideExporter.fileName(ride.startedAt)) }
                    }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = stringResource(R.string.history_export_gpx),
                            tint = palette.primary,
                        )
                    }
                    IconButton(onClick = { activeRide = null }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), tint = palette.textSecondary)
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                                stringResource(R.string.history_no_track),
                                style = AppType.body,
                                color = palette.textTertiary,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    // 分圈:用原始轨迹点现算(与骑行中、成绩页同一套算法),
                    // 因此历史记录无需额外落库也能看到分段数据。
                    val laps = remember(rawPoints, ride.lapDistanceM) {
                        if (ride.lapDistanceM > 0.0) Laps.split(rawPoints, ride.lapDistanceM) else emptyList()
                    }
                    if (!loading && laps.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        LapStrip(laps = laps, units = units)
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
            Text(value, style = AppType.display, color = palette.textPrimary)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = AppType.caption, color = palette.textTertiary)
        }
        Text(label, style = AppType.caption, color = palette.textSecondary)
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
    val units = AppTheme.units
    // 心率带为可选外设:有心率数据才拼上心率片段。
    val facts = stringResource(R.string.history_ride_facts, ride.calories, Units.elevationText(ride.elevationGainM, units))
    val hrSuffix = stringResource(R.string.history_ride_hr_suffix, ride.avgHeartRateBpm)
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
                    Units.distanceText(ride.distanceKm, units),
                    style = AppType.subtitle,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniFact(Icons.Outlined.Timer, formatDuration(ride.durationSec))
                    Spacer(Modifier.width(Space.md))
                    MiniFact(Icons.Outlined.Place, stringResource(R.string.history_avg_speed_short, Units.speedText(ride.avgSpeedKmh, units)))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    facts + if (ride.avgHeartRateBpm > 0) hrSuffix else "",
                    style = AppType.caption,
                    color = palette.textSecondary,
                )
                Text(dateText, style = AppType.caption, color = palette.textTertiary)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
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
        Text(text, style = AppType.caption, color = palette.textSecondary)
    }
}

/** 分圈横向条:每条记录一格,圈数多时可横向滑动。 */
@Composable
private fun LapStrip(laps: List<LapSplit>, units: UnitSystem) {
    val palette = AppTheme.palette
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                stringResource(R.string.laps_title),
                style = AppType.caption,
                color = palette.textSecondary,
            )
        }
        Spacer(Modifier.height(Space.xs))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            laps.forEach { lap -> LapChip(lap, units) }
        }
    }
}

@Composable
private fun LapChip(lap: LapSplit, units: UnitSystem) {
    val palette = AppTheme.palette
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
    ) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
            Text(
                stringResource(R.string.lap_number, lap.index) +
                    if (lap.isComplete) "" else " · " + stringResource(R.string.lap_in_progress),
                style = AppType.caption,
                color = if (lap.isComplete) palette.textSecondary else palette.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                Units.distanceText(lap.distanceKm, units),
                style = AppType.label,
                color = palette.textPrimary,
            )
            Text(
                formatDuration(lap.durationSec),
                style = AppType.caption,
                color = palette.textTertiary,
            )
            Text(
                Units.speedText(lap.avgSpeedKmh, units),
                style = AppType.caption,
                color = palette.textSecondary,
            )
        }
    }
}

private fun formatDuration(sec: Long): String =
    "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
