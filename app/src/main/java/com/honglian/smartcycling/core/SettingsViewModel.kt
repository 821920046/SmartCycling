package com.honglian.smartcycling.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.honglian.smartcycling.SmartCyclingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置视图模型:管理车轮尺寸选择。
 * 选择后同时持久化并实时代入传感器速度/里程计算。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container

    private val _wheel = MutableStateFlow(container.settings.wheelPreset)
    val wheel: StateFlow<WheelPreset> = _wheel.asStateFlow()

    private val _riderName = MutableStateFlow(container.settings.riderName)
    val riderName: StateFlow<String> = _riderName.asStateFlow()

    private val _cloudSyncUrl = MutableStateFlow(container.settings.cloudSyncUrl)
    val cloudSyncUrl: StateFlow<String> = _cloudSyncUrl.asStateFlow()

    private val _cloudSyncToken = MutableStateFlow(container.settings.cloudSyncToken)
    val cloudSyncToken: StateFlow<String> = _cloudSyncToken.asStateFlow()

    private val _mapType = MutableStateFlow(container.settings.mapType)
    val mapType: StateFlow<Int> = _mapType.asStateFlow()

    private val _riderWeightKg = MutableStateFlow(container.settings.riderWeightKg)
    val riderWeightKg: StateFlow<Float> = _riderWeightKg.asStateFlow()

    private val _autoPauseEnabled = MutableStateFlow(container.settings.autoPauseEnabled)
    val autoPauseEnabled: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    private val _autoPauseThresholdKmh = MutableStateFlow(container.settings.autoPauseThresholdKmh)
    val autoPauseThresholdKmh: StateFlow<Float> = _autoPauseThresholdKmh.asStateFlow()

    fun select(preset: WheelPreset) {
        container.settings.wheelPreset = preset
        container.sensorManager.wheelCircumferenceM = preset.circumferenceM
        _wheel.value = preset
    }

    fun updateRiderName(name: String) {
        container.settings.riderName = name
        _riderName.value = name
    }

    fun updateCloudSyncUrl(url: String) {
        container.settings.cloudSyncUrl = url
        _cloudSyncUrl.value = url
    }

    fun updateCloudSyncToken(token: String) {
        container.settings.cloudSyncToken = token
        _cloudSyncToken.value = token
    }

    fun updateMapType(type: Int) {
        container.settings.mapType = type
        _mapType.value = type
    }

    fun updateRiderWeight(kg: Float) {
        val v = kg.coerceIn(30f, 200f)
        container.settings.riderWeightKg = v
        _riderWeightKg.value = v
    }

    fun updateAutoPauseEnabled(enabled: Boolean) {
        container.settings.autoPauseEnabled = enabled
        _autoPauseEnabled.value = enabled
    }

    fun updateAutoPauseThreshold(kmh: Float) {
        val v = kmh.coerceIn(0.5f, 5f)
        container.settings.autoPauseThresholdKmh = v
        _autoPauseThresholdKmh.value = v
    }
}

