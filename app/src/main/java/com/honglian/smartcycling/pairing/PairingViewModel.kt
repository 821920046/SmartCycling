package com.honglian.smartcycling.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.ble.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 配对视图模型:扫描附近 BLE 设备并维护列表。
 *
 * 支持两种连接方式:
 * - 自动连接:识别到广播含 CSC 服务(0x1816)或名称含 S314/Magene 时自动连。
 * - 手动点选:应对传感器广播名不含型号(如显示为“36079-1”)的情况,用户直接选择。
 */
class PairingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container
    private val repo = container.pairingRepository
    private val manager = container.sensorManager

    val connection: StateFlow<ConnectionState> = manager.connection

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private var scanJob: Job? = null
    private var autoConnected = false

    /** 开始(或重新)扫描。已在扫描则忽略。 */
    fun startScan() {
        if (scanJob != null) return
        autoConnected = false
        _devices.value = emptyList()
        scanJob = viewModelScope.launch {
            repo.scan().collect { d ->
                // 按设备地址去重,保留最新 RSSI/名称,按信号强度降序。
                val byAddr = LinkedHashMap<String, DiscoveredDevice>()
                _devices.value.forEach { byAddr[it.device.address] = it }
                val prev = byAddr[d.device.address]
                val name = d.name.ifBlank { prev?.name ?: "" }
                byAddr[d.device.address] = d.copy(name = name, hasCsc = d.hasCsc || prev?.hasCsc == true)
                _devices.value = byAddr.values.sortedByDescending { it.rssi }

                if (!autoConnected && (repo.isTargetSensor(name) || d.hasCsc)) {
                    autoConnected = true
                    connect(d)
                }
            }
        }
    }

    /** 停止扫描并连接指定设备(连接前停扫,避免边扫边连互相干扰)。 */
    fun connect(device: DiscoveredDevice) {
        scanJob?.cancel()
        scanJob = null
        manager.connectTo(device.device)
    }
}
