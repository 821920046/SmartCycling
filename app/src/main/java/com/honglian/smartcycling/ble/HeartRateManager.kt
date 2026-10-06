package com.honglian.smartcycling.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver

/**
 * 标准 BLE 心率带管理器(Heart Rate Service 0x180D)。
 *
 * 与 [S314Manager] 同构(同样的 Nordic BleManager 用法、同样的断连自动重连策略),
 * 这样"速度/踏频"与"心率"是两个互相独立的连接,任一传感器掉线都不会影响另一个。
 *
 * 心率带属于**可选外设**:未连接时 [bpm] 恒为 0,骑行界面不会展示心率行。
 */
class HeartRateManager(context: Context) : BleManager(context) {

    private var measurementChar: BluetoothGattCharacteristic? = null

    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var lastDevice: BluetoothDevice? = null

    @Volatile
    private var userRequestedDisconnect = false

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                _connection.value = ConnectionState.CONNECTING
            }

            override fun onDeviceConnected(device: BluetoothDevice) {}

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                _connection.value = ConnectionState.DISCONNECTED
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                _connection.value = ConnectionState.READY
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                _connection.value = ConnectionState.DISCONNECTING
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                _connection.value = ConnectionState.DISCONNECTED
                _bpm.value = 0
                // 非人为断连(信号丢失)自动重连
                if (!userRequestedDisconnect && reason == ConnectionObserver.REASON_LINK_LOSS) {
                    lastDevice?.let { d ->
                        connect(d)
                            .useAutoConnect(true)
                            .retry(3, 300)
                            .enqueue()
                    }
                }
            }
        })
    }

    override fun getGattCallback(): BleManagerGattCallback = HrGattCallback()

    private inner class HrGattCallback : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(HrUuids.SERVICE) ?: return false
            measurementChar = service.getCharacteristic(HrUuids.MEASUREMENT)
            return measurementChar != null
        }

        override fun initialize() {
            setNotificationCallback(measurementChar).with { _, data ->
                val bytes = data.value ?: return@with
                HrParser.parse(bytes)?.let { _bpm.value = it }
            }
            enableNotifications(measurementChar).enqueue()
        }

        override fun onServicesInvalidated() {
            measurementChar = null
            _bpm.value = 0
        }
    }

    fun connectTo(device: BluetoothDevice) {
        lastDevice = device
        userRequestedDisconnect = false
        connect(device)
            .retry(3, 200)
            // 与 S314 一致:首次直连用 false,true 会让首次连接长时间挂起。
            .useAutoConnect(false)
            .timeout(15_000)
            .enqueue()
    }

    fun disconnectDevice() {
        userRequestedDisconnect = true
        disconnect().enqueue()
    }
}
