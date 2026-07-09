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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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

    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("连接骑行传感器", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        if (!hardwareReady) {
            Text("请先开启蓝牙和定位服务", fontSize = 16.sp)
        } else {
            when (connection) {
                ConnectionState.CONNECTING -> {
                    CircularProgressIndicator()
                    Text("正在连接…", fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp))
                }
                ConnectionState.READY -> {
                    Text("已连接,正在进入地图…", fontSize = 16.sp)
                }
                ConnectionState.DISCONNECTING -> {
                    Text("断开中…", fontSize = 16.sp)
                }
                ConnectionState.DISCONNECTED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(
                            "正在搜索附近的传感器…",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Text(
                        "找到你的迈金 S314 后点击连接。它可能显示为编号(如 36079-1),转一下轮子/曲柄可唤醒。",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(devices) { d ->
                            DeviceRow(d) { onConnect(d) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartScan) { Text("重新搜索") }
                }
            }
        }
    }

    // 弹窗提醒开启蓝牙/定位(不允许点外部关闭,开启后自动继续)
    if (!hardwareReady) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("需要开启蓝牙和定位") },
            text = {
                Column {
                    if (!bluetoothOn) Text("• 蓝牙未开启", fontSize = 15.sp)
                    if (!locationOn) Text("• 定位服务未开启", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "“智能骑行”需要蓝牙连接踏频传感器、需要定位进行导航。请开启后返回,应用会自动继续。",
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!bluetoothOn) openBluetoothSettings(context) else openLocationSettings(context)
                }) {
                    Text(if (!bluetoothOn) "开启蓝牙" else "开启定位")
                }
            },
            dismissButton = if (!bluetoothOn && !locationOn) {
                {
                    TextButton(onClick = { openLocationSettings(context) }) { Text("开启定位") }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                val title = device.name.ifBlank { device.device.address }
                Text(
                    if (device.hasCsc) "$title  · 传感器" else title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(device.device.address, fontSize = 12.sp)
            }
            Text("${device.rssi} dBm", fontSize = 12.sp)
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
