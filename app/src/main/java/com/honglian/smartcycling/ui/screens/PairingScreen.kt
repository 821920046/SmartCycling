package com.honglian.smartcycling.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.pairing.DiscoveredDevice
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space

/**
 * 开屏配对页。
 *
 * 流程(与旧版一致,只重做外观):
 *  1) 先检测蓝牙与定位是否开启;未开则弹窗引导,回到前台自动复检;
 *  2) 申请蓝牙/定位权限 → 扫描 → 自动或手动连接传感器。
 */
@Composable
fun PairingScreen(
    connection: ConnectionState,
    devices: List<DiscoveredDevice>,
    onConnect: (DiscoveredDevice) -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = AppTheme.palette

    var bluetoothOn by remember { mutableStateOf(isBluetoothOn(context)) }
    var locationOn by remember { mutableStateOf(isLocationOn(context)) }
    val hardwareReady = bluetoothOn && locationOn

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bluetoothOn = isBluetoothOn(context)
                locationOn = isLocationOn(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) onStartScan()
    }

    LaunchedEffect(hardwareReady) {
        if (!hardwareReady) return@LaunchedEffect
        val perms = blePermissions()
        val granted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onStartScan() else permissionLauncher.launch(perms)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.xxl))
            Text(
                text = "连接骑行传感器",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = "智能骑行 · 迈金 S314 速度 / 踏频",
                style = MaterialTheme.typography.label,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(Space.xl))

            if (!hardwareReady) {
                HardwareBlocked(
                    bluetoothOn = bluetoothOn,
                    locationOn = locationOn,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier.size(168.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ScanRadar(scanning = connection == ConnectionState.DISCONNECTED)
                }
                Spacer(Modifier.height(Space.lg))
                ConnectionStatus(connection)
                Spacer(Modifier.height(Space.md))

                if (connection == ConnectionState.DISCONNECTED) {
                    Text(
                        text = "转动一下轮子或曲柄可主动唤醒传感器",
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                    Spacer(Modifier.height(Space.md))
                    DeviceList(devices = devices, onConnect = onConnect, modifier = Modifier.weight(1f))
                    Spacer(Modifier.height(Space.md))
                    Button(
                        onClick = onStartScan,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text("重新扫描", style = MaterialTheme.typography.subtitle)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (!hardwareReady) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text("需要开启蓝牙与定位", style = MaterialTheme.typography.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (!bluetoothOn) Text("• 蓝牙未开启", color = palette.danger, style = MaterialTheme.typography.label)
                    if (!locationOn) Text("• 定位服务未开启", color = palette.danger, style = MaterialTheme.typography.label)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "蓝牙用于连接骑行传感器获取精准速度与踏频;定位用于轨迹记录与导航。开启后返回本页会自动继续。",
                        style = MaterialTheme.typography.body,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!bluetoothOn) openBluetoothSettings(context) else openLocationSettings(context)
                }) {
                    Text(if (!bluetoothOn) "去开启蓝牙" else "去开启定位", color = palette.primary)
                }
            },
            dismissButton = {
                if (!bluetoothOn && !locationOn) {
                    TextButton(onClick = { openLocationSettings(context) }) {
                        Text("去开启定位", color = palette.primary)
                    }
                } else null
            },
        )
    }
}

@Composable
private fun HardwareBlocked(
    bluetoothOn: Boolean,
    locationOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (!bluetoothOn) Icons.Outlined.Bluetooth else Icons.Outlined.LocationOff,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = if (!bluetoothOn) "请先开启蓝牙" else "请先开启定位服务",
                style = MaterialTheme.typography.subtitle,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun ConnectionStatus(connection: ConnectionState) {
    val palette = AppTheme.palette
    when (connection) {
        ConnectionState.CONNECTING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp)
            Spacer(Modifier.height(Space.md))
            Text("正在建立连接…", style = MaterialTheme.typography.subtitle, color = palette.primary)
        }
        ConnectionState.READY -> Text(
            "连接成功,正在载入…",
            style = MaterialTheme.typography.subtitle,
            color = palette.success,
        )
        ConnectionState.DISCONNECTING -> Text(
            "传感器断开中…",
            style = MaterialTheme.typography.body,
            color = palette.textSecondary,
        )
        ConnectionState.DISCONNECTED -> Text(
            "正在搜索附近的传感器…",
            style = MaterialTheme.typography.subtitle,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<DiscoveredDevice>,
    onConnect: (DiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    LazyColumn(modifier.fillMaxWidth()) {
        if (devices.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "未发现设备,保持传感器处于活动状态",
                        style = MaterialTheme.typography.body,
                        color = palette.textTertiary,
                    )
                }
            }
        }
        items(devices, key = { it.device.address }) { d ->
            DeviceRow(d) { onConnect(d) }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    val palette = AppTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.hasCsc) palette.success.copy(alpha = 0.14f)
                            else palette.primary.copy(alpha = 0.12f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (device.hasCsc) Icons.Outlined.DirectionsBike else Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        tint = if (device.hasCsc) palette.success else palette.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column {
                    Text(
                        text = device.name.ifBlank { "未知设备" } + if (device.hasCsc) "  · CSC" else "",
                        style = MaterialTheme.typography.subtitle,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = device.device.address,
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                }
            }
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.label,
                color = if (device.rssi >= -70) palette.success else palette.textTertiary,
            )
        }
    }
}

/** 克制的扫描动效:同心圆 + 一道匀速扫描扇形,不使用霓虹发光。 */
@Composable
private fun ScanRadar(scanning: Boolean) {
    val palette = AppTheme.palette
    val transition = rememberInfiniteTransition(label = "scan")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f
        drawCircle(palette.primary.copy(alpha = 0.06f), maxR, center)
        listOf(1f, 0.66f, 0.33f).forEach { k ->
            drawCircle(
                color = palette.outline,
                radius = maxR * k,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        if (scanning) {
            drawCircle(
                color = palette.primary.copy(alpha = 0.35f * (1f - pulse)),
                radius = maxR * pulse,
                center = center,
                style = Stroke(1.5.dp.toPx()),
            )
            rotate(angle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, palette.primary.copy(alpha = 0.28f)),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxR, center.y - maxR),
                    size = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2),
                )
            }
        }
        drawCircle(palette.primary, 6.dp.toPx(), center)
    }
}

/** 蓝牙是否已开启(读适配器状态,无需运行时权限)。 */
private fun isBluetoothOn(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    manager?.adapter?.isEnabled == true
}.getOrDefault(false)

/** 定位服务(GPS/网络)是否已开启。 */
private fun isLocationOn(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    manager != null && LocationManagerCompat.isLocationEnabled(manager)
}.getOrDefault(false)

private fun openBluetoothSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openLocationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 根据系统版本返回扫描/连接所需的运行时权限。 */
private fun blePermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
