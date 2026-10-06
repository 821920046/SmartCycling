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
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.honglian.smartcycling.R
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.pairing.DiscoveredDevice
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.AppType
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
    hrConnection: ConnectionState = ConnectionState.DISCONNECTED,
    onSkip: () -> Unit = {},
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

    // 权限使用说明弹窗(先解释用途再申请,符合应用商店合规要求)
    var showPermRationale by remember { mutableStateOf(false) }

    // 仅当蓝牙+定位都开启后才检查权限并开始扫描
    LaunchedEffect(hardwareReady) {
        if (!hardwareReady) return@LaunchedEffect
        val perms = blePermissions()
        val granted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onStartScan() else showPermRationale = true
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
                text = stringResource(R.string.pairing_title),
                style = AppType.title,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.pairing_subtitle),
                style = AppType.label,
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
                HrStatus(hrConnection)
                Spacer(Modifier.height(Space.md))

                if (connection == ConnectionState.DISCONNECTED) {
                    Text(
                        text = stringResource(R.string.pairing_wake_hint),
                        style = AppType.caption,
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
                        Text(stringResource(R.string.pairing_rescan), style = AppType.subtitle)
                    }
                    Spacer(Modifier.height(Space.sm))
                    // 传感器没电/不在身边时不应把用户卡死在配对页:GPS 本身即可完成基础骑行记录。
                    TextButton(onClick = onSkip) {
                        Text(
                            stringResource(R.string.pairing_skip),
                            style = AppType.label,
                            color = palette.textTertiary,
                        )
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
            title = { Text(stringResource(R.string.pairing_need_hw_title), style = AppType.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (!bluetoothOn) Text(stringResource(R.string.pairing_bt_off), color = palette.danger, style = AppType.label)
                    if (!locationOn) Text(stringResource(R.string.pairing_loc_off), color = palette.danger, style = AppType.label)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        stringResource(R.string.pairing_hw_rationale),
                        style = AppType.body,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!bluetoothOn) openBluetoothSettings(context) else openLocationSettings(context)
                }) {
                    Text(
                        stringResource(if (!bluetoothOn) R.string.pairing_open_bt else R.string.pairing_open_loc),
                        color = palette.primary,
                    )
                }
            },
            dismissButton = {
                if (!bluetoothOn && !locationOn) {
                    TextButton(onClick = { openLocationSettings(context) }) {
                        Text(stringResource(R.string.pairing_open_loc), color = palette.primary)
                    }
                } else null
            },
        )
    }

    // 权限使用说明:授权前明确告知用途(蓝牙近距离扫描传感器、定位用于导航)
    if (showPermRationale) {
        AlertDialog(
            onDismissRequest = { showPermRationale = false },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text(stringResource(R.string.pairing_perm_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.pairing_perm_bt), fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pairing_perm_loc), fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pairing_perm_note), fontSize = 12.sp, color = palette.primary, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermRationale = false
                    permissionLauncher.launch(blePermissions())
                }) { Text(stringResource(R.string.pairing_perm_agree), color = palette.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPermRationale = false }) { Text(stringResource(R.string.pairing_perm_later), color = palette.textSecondary) }
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
                text = stringResource(if (!bluetoothOn) R.string.pairing_need_bt else R.string.pairing_need_loc),
                style = AppType.subtitle,
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
            Text(stringResource(R.string.pairing_connecting), style = AppType.subtitle, color = palette.primary)
        }
        ConnectionState.READY -> Text(
            stringResource(R.string.pairing_connected),
            style = AppType.subtitle,
            color = palette.success,
        )
        ConnectionState.DISCONNECTING -> Text(
            stringResource(R.string.pairing_disconnecting),
            style = AppType.body,
            color = palette.textSecondary,
        )
        ConnectionState.DISCONNECTED -> Text(
            stringResource(R.string.pairing_searching),
            style = AppType.subtitle,
            color = palette.textSecondary,
        )
    }
}

/**
 * 心率带状态提示。心率带是可选项:未连接时不打扰用户,只给一行弱提示。
 */
@Composable
private fun HrStatus(hrConnection: ConnectionState) {
    val palette = AppTheme.palette
    Spacer(Modifier.height(Space.xs))
    when (hrConnection) {
        ConnectionState.READY -> Text(
            stringResource(R.string.pairing_hr_connected),
            style = AppType.caption,
            color = palette.success,
        )
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Text(
            stringResource(R.string.pairing_hr_connecting),
            style = AppType.caption,
            color = palette.textSecondary,
        )
        ConnectionState.DISCONNECTED -> Text(
            stringResource(R.string.pairing_hr_hint),
            style = AppType.caption,
            color = palette.textTertiary,
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
                        stringResource(R.string.pairing_no_device),
                        style = AppType.body,
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
    // ifBlank 的 lambda 不是组合上下文,回退文案需在此提前取好。
    val unknownDevice = stringResource(R.string.pairing_unknown_device)
    val tag = buildList {
        if (device.hasCsc) add("CSC")
        if (device.hasHrs) add("HR")
    }.joinToString(" · ").let { if (it.isEmpty()) "" else "  · $it" }
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
                            when {
                                device.hasCsc -> palette.success.copy(alpha = 0.14f)
                                device.hasHrs -> palette.danger.copy(alpha = 0.14f)
                                else -> palette.primary.copy(alpha = 0.12f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            device.hasCsc -> Icons.Outlined.DirectionsBike
                            device.hasHrs -> Icons.Outlined.Favorite
                            else -> Icons.Outlined.Bluetooth
                        },
                        contentDescription = null,
                        tint = when {
                            device.hasCsc -> palette.success
                            device.hasHrs -> palette.danger
                            else -> palette.primary
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column {
                    Text(
                        text = device.name.ifBlank { unknownDevice } + tag,
                        style = AppType.subtitle,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = device.device.address,
                        style = AppType.caption,
                        color = palette.textTertiary,
                    )
                }
            }
            Text(
                text = "${device.rssi} dBm",
                style = AppType.label,
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
