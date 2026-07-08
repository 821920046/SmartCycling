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

/** 连接状态。 */
enum class ConnectionState { DISCONNECTED, CONNECTING, READY, DISCONNECTING }

/**
 * 迈金 S314 传感器的 BLE 管理器(基于 Nordic Android-BLE-Library)。
 * 订阅 CSC Measurement,实时输出解析后的 [SensorReading]。
 */
class S314Manager(context: Context) : BleManager(context) {

    private var measurementChar: BluetoothGattCharacteristic? = null
    private val calculator = CscCalculator()

    private val _readings = MutableStateFlow(SensorReading())
    val readings: StateFlow<SensorReading> = _readings.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    var wheelCircumferenceM: Double
        get() = calculator.wheelCircumferenceM
        set(value) { calculator.wheelCircumferenceM = value }

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) { _connection.value = ConnectionState.CONNECTING }
            override fun onDeviceConnected(device: BluetoothDevice) {}
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) { _connection.value = ConnectionState.DISCONNECTED }
            override fun onDeviceReady(device: BluetoothDevice) { _connection.value = ConnectionState.READY }
            override fun onDeviceDisconnecting(device: BluetoothDevice) { _connection.value = ConnectionState.DISCONNECTING }
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) { _connection.value = ConnectionState.DISCONNECTED }
        })
    }

    override fun getGattCallback(): BleManagerGattCallback = S314GattCallback()

    private inner class S314GattCallback : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(CscUuids.SERVICE) ?: return false
            measurementChar = service.getCharacteristic(CscUuids.MEASUREMENT)
            return measurementChar != null
        }

        override fun initialize() {
            calculator.reset()
            setNotificationCallback(measurementChar).with { _, data ->
                val bytes = data.value ?: return@with
                runCatching { CscParser.parse(bytes) }
                    .onSuccess { _readings.value = calculator.update(it) }
            }
            enableNotifications(measurementChar).enqueue()
        }

        override fun onServicesInvalidated() {
            measurementChar = null
        }
    }

    fun connectTo(device: BluetoothDevice) {
        connect(device)
            .retry(3, 200)
            // 首次连接必须用 autoConnect=false(直连,秒级建连);true 会让首连长时间挂起甚至连不上。
            .useAutoConnect(false)
            .timeout(15_000)
            .enqueue()
    }

    fun disconnectDevice() {
        disconnect().enqueue()
    }
}
