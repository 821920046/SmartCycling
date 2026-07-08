package com.honglian.smartcycling.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.honglian.smartcycling.ble.CscUuids
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

/** 扫描结果。 */
data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
    /** 广播包中是否声明了 CSC 服务(0x1816),用于自动识别骑行传感器。 */
    val hasCsc: Boolean = false,
)

/**
 * 扫描附近的 BLE 设备。
 *
 * 第一性原则:许多速度/踏频传感器(包括迈金 S314)并不在广播包里携带 CSC
 * 服务 UUID(0x1816 需要连接后做 GATT 服务发现才可见),且广播名可能是编号(如
 * “36079-1”)而非型号。因此扫描阶段不按服务 UUID 过滤,而是上报所有设备由上层
 * 自动识别(名称/CSC)或用户手动选择。
 */
class PairingRepository(private val context: Context) {

    private val scanner get() = BluetoothLeScannerCompat.getScanner()

    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        // 不按服务 UUID 过滤:S314 等传感器广播包不包含 0x1816,过滤会导致永远扫不到。
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareFilteringIfSupported(true)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name.orEmpty()
                val hasCsc = result.scanRecord?.serviceUuids?.any { it.uuid == CscUuids.SERVICE } == true
                trySend(DiscoveredDevice(result.device, name, result.rssi, hasCsc))
            }
        }
        scanner.startScan(filters, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    /** 名称含 S314/Magene 则认为是目标传感器(仅用于自动连接的快速命中)。 */
    fun isTargetSensor(name: String): Boolean =
        name.contains("S314", ignoreCase = true) || name.contains("Magene", ignoreCase = true)
}
