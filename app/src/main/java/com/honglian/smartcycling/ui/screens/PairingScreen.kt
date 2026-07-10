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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.pairing.DiscoveredDevice
import com.honglian.smartcycling.ui.theme.*


/**
 * 开屏配对界面。
 * 1) 先检测蓝牙与定位服务是否开启;任一未开则弹窗提醒并引导去系统设置开启,
 *    从设置返回后自动重新检测,两者都开启才进入权限申请与扫描阶段(避免关闭蓝牙时扫描崩溃)。
 * 2) 申请蓝牙/定位权限,然后扫描并列出附近设备。
 * 自动识别传感器会直接连;若未自动连,用户可手动点选(如显示为“36079-1”的设备)。
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

    // 蓝牙/定位开关状态,回到前台时重新检测
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

    // 仅当蓝牙+定位都开启后才检查权限并开始扫描
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
            .background(Brush.verticalGradient(listOf(PanelBgTop, PanelBgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = "智能骑行传感器配对",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SpeedText,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(16.dp))

            if (!hardwareReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请先开启蓝牙和定位服务", fontSize = 16.sp, color = DataLabel)
                }
            } else {
                // 扫描雷达与连接状态
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarScanView(scanning = connection == ConnectionState.DISCONNECTED)
                }

                Spacer(Modifier.height(16.dp))

                when (connection) {
                    ConnectionState.CONNECTING -> {
                        CircularProgressIndicator(color = BrandCyan)
                        Text(
                            "正在建立加密信道连接…",
                            fontSize = 15.sp,
                            color = BrandCyan,
                            modifier = Modifier.padding(top = 16.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ConnectionState.READY -> {
                        Text(
                            "连接成功！正在载入骑行中枢…",
                            fontSize = 15.sp,
                            color = BrandGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ConnectionState.DISCONNECTING -> {
                        Text("传感器断开中…", fontSize = 15.sp, color = DataLabel)
                    }
                    ConnectionState.DISCONNECTED -> {
                        Text(
                            "正在搜索附近的传感器…",
                            fontSize = 15.sp,
                            color = DataLabel,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "转动一下轮子或曲柄可主动唤醒传感器",
                            fontSize = 12.sp,
                            color = DataLabel.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        
                        // 设备列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        ) {
                            if (devices.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("无活跃信号，等待发现…", fontSize = 14.sp, color = DataLabel.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            items(devices) { d ->
                                DeviceRow(d) { onConnect(d) }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color(0xFF060913)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("重新扫描雷达", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // 弹窗提醒开启蓝牙/定位(不允许点外部关闭,开启后自动继续)
    if (!hardwareReady) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = CardBg,
            titleContentColor = SpeedText,
            textContentColor = DataLabel,
            title = { Text("需要开启蓝牙和定位", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (!bluetoothOn) Text("• 蓝牙未开启", fontSize = 15.sp, color = StopRed, fontWeight = FontWeight.Bold)
                    if (!locationOn) Text("• 定位服务未开启", fontSize = 15.sp, color = StopRed, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "“智能骑行”需要蓝牙连接骑行传感器以获取精准速度、需要定位进行高精度导航规划。请开启后返回，应用会自动感知并继续工作。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!bluetoothOn) openBluetoothSettings(context) else openLocationSettings(context)
                }) {
                    Text(if (!bluetoothOn) "去开启蓝牙" else "去开启定位", color = BrandCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = if (!bluetoothOn && !locationOn) {
                {
                    TextButton(onClick = { openLocationSettings(context) }) { 
                        Text("去开启定位", color = BrandCyan) 
                    }
                }
            } else {
                null
            },
        )
    }
}

/** 动态科幻雷达扫描效果 */
@Composable
private fun RadarScanView(scanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_angle"
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = size / 2.0f
        val maxRadius = size.minDimension / 2.0f

        // 1. 绘制雷达外圆盘
        drawCircle(
            color = BrandCyan.copy(alpha = 0.15f),
            radius = maxRadius,
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = BrandCyan.copy(alpha = 0.08f),
            radius = maxRadius * 0.6f,
            style = Stroke(1.dp.toPx())
        )
        drawCircle(
            color = BrandCyan.copy(alpha = 0.08f),
            radius = maxRadius * 0.3f,
            style = Stroke(1.dp.toPx())
        )

        // 2. 绘制扩散脉冲波环
        if (scanning) {
            drawCircle(
                color = BrandCyan.copy(alpha = 0.3f * (1f - pulseScale)),
                radius = maxRadius * pulseScale,
                style = Stroke(2.dp.toPx())
            )
        }

        // 3. 绘制旋转扫描渐变弧
        if (scanning) {
            rotate(angle) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(RadarEdge, BrandCyan.copy(alpha = 0.4f)),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true
                )
            }
        }

        // 4. 绘制中心亮核心
        drawCircle(
            color = BrandCyan,
            radius = 6.dp.toPx()
        )
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 蓝牙小指示圈
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (device.hasCsc) BrandGreen.copy(alpha = 0.2f) else BrandCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (device.hasCsc) "🚴" else "📶",
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    val title = device.name.ifBlank { "未知设备" }
                    Text(
                        text = if (device.hasCsc) "$title [CSC]" else title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpeedText
                    )
                    Text(
                        text = device.device.address,
                        fontSize = 11.sp,
                        color = DataLabel,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // 信号强度
            Text(
                text = "${device.rssi} dBm",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (device.rssi >= -70) BrandGreen else DataLabel,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** 蓝牙是否已开启(读取适配器状态,不需要运行时权限)。 */
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

