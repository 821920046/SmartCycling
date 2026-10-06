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
 * 支持两类外设,互相独立:
 *  - **速度/踏频传感器**(CSC 0x1816,如迈金 S314)—— 核心外设,连接成功后进入主界面。
 *  - **心率带**(HRS 0x180D)—— 可选外设,后台静默连接,连上后骑行页自动多出心率行。
 *
 * 连接方式:
 *  - 自动连接:CSC 识别到广播含 0x1816 或名称含 S314/Magene;心率带识别到广播含 0x180D。
 *  - 手动点选:应对传感器广播名不含型号(如显示为"36079-1")的情况,用户直接选择。
 *  - 记住回连:曾连过的心率带(MAC 已持久化)再次出现时自动回连。
 */
class PairingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container
    private val repo = container.pairingRepository
    private val manager = container.sensorManager
    private val hrManager = container.heartRateManager
    private val settings = container.settings

    /** 速度/踏频传感器连接状态(决定配对页是否放行进入主界面)。 */
    val connection: StateFlow<ConnectionState> = manager.connection

    /** 心率带连接状态(可选,仅用于界面提示)。 */
    val hrConnection: StateFlow<ConnectionState> = hrManager.connection

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private var scanJob: Job? = null
    private var autoConnected = false
    private var hrAutoConnected = false

    /** 开始(或重新)扫描。已在扫描则忽略。 */
    fun startScan() {
        if (scanJob != null) return
        autoConnected = false
        hrAutoConnected = false
        _devices.value = emptyList()
        scanJob = viewModelScope.launch {
            repo.scan().collect { d ->
                // 按设备地址去重,保留最新 RSSI/名称,按信号强度降序。
                val byAddr = LinkedHashMap<String, DiscoveredDevice>()
                _devices.value.forEach { byAddr[it.device.address] = it }
                val prev = byAddr[d.device.address]
                val name = d.name.ifBlank { prev?.name ?: "" }
                byAddr[d.device.address] = d.copy(
                    name = name,
                    hasCsc = d.hasCsc || prev?.hasCsc == true,
                    hasHrs = d.hasHrs || prev?.hasHrs == true,
                )
                _devices.value = byAddr.values.sortedByDescending { it.rssi }

                // —— 速度/踏频自动连接 ——
                if (!autoConnected && (d.hasCsc || repo.isTargetSensor(name))) {
                    autoConnected = true
                    connectCsc(d)
                }
                // —— 心率带自动连接 ——
                val savedHr = settings.hrDeviceAddress
                val looksLikeSaved = savedHr.isNotBlank() && d.device.address == savedHr
                if (!hrAutoConnected && (d.hasHrs || looksLikeSaved)) {
                    hrAutoConnected = true
                    connectHr(d)
                }
            }
        }
    }

    /** 手动点选连接:按设备类型自动路由到对应管理器。 */
    fun connect(device: DiscoveredDevice) {
        val isHeartRate = device.hasHrs || (repo.isHeartRateDevice(device.name) && !repo.isTargetSensor(device.name))
        if (isHeartRate) {
            connectHr(device)
            return
        }
        // CSC 连接前停扫,避免边扫边连互相干扰。
        scanJob?.cancel()
        scanJob = null
        manager.connectTo(device.device)
    }

    private fun connectCsc(device: DiscoveredDevice) {
        scanJob?.cancel()
        scanJob = null
        manager.connectTo(device.device)
    }

    private fun connectHr(device: DiscoveredDevice) {
        hrManager.connectTo(device.device)
        settings.hrDeviceAddress = device.device.address
        settings.hrDeviceName = device.name
    }
}
