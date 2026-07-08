package com.honglian.smartcycling.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

/** 扫描结果。 */
data class DiscoveredDevice(val device: BluetoothDevice, val name: String, val rssi: Int)

/**
 * 负责扫描支持 CSC 服务的传感器。扫描阶段不按服务 UUID 过滤
 * (多数传感器广播包不含 0x1816),扫到后按名称(S314 / Magene)确认,实现“打开即自动配对”。
 */
class PairingRepository(private val context: Context) {

    private val scanner get() = BluetoothLeScannerCompat.getScanner()

    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        // 迈金 S314 等踏频/速度传感器通常不会在广播包里携带 CSC 服务 UUID(0x1816),
        // 该服务只有连接后做 GATT 服务发现才可见。若按服务 UUID 过滤,onScanResult 将永远不触发
        // → 永远扫不到设备。因此此处不加过滤,扫到后在回调里按名称确认。
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareFilteringIfSupported(true)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name.orEmpty()
                trySend(DiscoveredDevice(result.device, name, result.rssi))
            }
        }
        scanner.startScan(filters, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    /** 自动配对:命中名称含 S314/Magene 的第一个设备即返回。 */
    fun isTargetSensor(name: String): Boolean =
        name.contains("S314", ignoreCase = true) || name.contains("Magene", ignoreCase = true)
}
