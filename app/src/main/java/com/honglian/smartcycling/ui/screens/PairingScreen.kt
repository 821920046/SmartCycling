package com.honglian.smartcycling.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.honglian.smartcycling.ble.ConnectionState
import com.honglian.smartcycling.pairing.DiscoveredDevice

/**
 * 开屏配对界面。先申请蓝牙/定位权限,然后扫描并列出附近设备。
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) onStartScan()
    }
    LaunchedEffect(Unit) {
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
