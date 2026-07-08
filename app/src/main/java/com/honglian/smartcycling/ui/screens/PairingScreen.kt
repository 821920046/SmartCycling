package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.ble.ConnectionState

/** 开屏自动配对界面。 */
@Composable
fun PairingScreen(connection: ConnectionState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        val text = when (connection) {
            ConnectionState.DISCONNECTED -> "正在搜索迈金 S314 传感器…"
            ConnectionState.CONNECTING -> "发现设备,正在连接…"
            ConnectionState.READY -> "已连接,正在进入地图…"
            ConnectionState.DISCONNECTING -> "断开中…"
        }
        Text(text, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))
    }
}
