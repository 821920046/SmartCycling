package com.honglian.smartcycling.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honglian.smartcycling.SmartCyclingApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置视图模型:管理车轮尺寸、主题模式与地图数据源。
 * 所有修改同时持久化并即时生效。
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

    // 这几项直接复用 Settings 内的共享流(而非各自 new 一个 MutableStateFlow),
    // 以保证与 OfflineMapsViewModel 等其它写入方始终读到同一份最新状态。
    val mapType: StateFlow<Int> = container.settings.mapTypeFlow
    val themeMode: StateFlow<ThemeMode> = container.settings.themeModeFlow
    val mapSource: StateFlow<MapSource> = container.settings.mapSourceFlow
    val activeOfflineMapId: StateFlow<Long> = container.settings.activeOfflineMapIdFlow

    private val _riderWeightKg = MutableStateFlow(container.settings.riderWeightKg)
    val riderWeightKg: StateFlow<Float> = _riderWeightKg.asStateFlow()

    private val _autoPauseEnabled = MutableStateFlow(container.settings.autoPauseEnabled)
    val autoPauseEnabled: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    private val _autoPauseThresholdKmh = MutableStateFlow(container.settings.autoPauseThresholdKmh)
    val autoPauseThresholdKmh: StateFlow<Float> = _autoPauseThresholdKmh.asStateFlow()

    private val _highContrast = MutableStateFlow(container.settings.highContrast)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val _localOnly = MutableStateFlow(container.settings.localOnlyMode)
    val localOnly: StateFlow<Boolean> = _localOnly.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(container.settings.keepScreenOnWhileRiding)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _lockOrientation = MutableStateFlow(container.settings.lockOrientationWhileRiding)
    val lockOrientation: StateFlow<Boolean> = _lockOrientation.asStateFlow()

    // 单位制与主题一样是全局显示偏好,直接复用 Settings 内的共享流。
    val unitSystem: StateFlow<UnitSystem> = container.settings.unitSystemFlow

    private val _autoLapEnabled = MutableStateFlow(container.settings.autoLapEnabled)
    val autoLapEnabled: StateFlow<Boolean> = _autoLapEnabled.asStateFlow()

    private val _autoLapDistanceKm = MutableStateFlow(container.settings.autoLapDistanceKm)
    val autoLapDistanceKm: StateFlow<Float> = _autoLapDistanceKm.asStateFlow()

    fun select(preset: WheelPreset) {
        container.settings.wheelPreset = preset
        container.sensorManager.wheelCircumferenceM = preset.circumferenceM
        _wheel.value = preset
    }

    fun updateRiderName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        container.settings.riderName = trimmed
        _riderName.value = trimmed
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
    }

    fun updateThemeMode(mode: ThemeMode) {
        container.settings.themeMode = mode
    }

    fun updateMapSource(source: MapSource) {
        container.settings.mapSource = source
    }

    /** 选中某张离线地图并自动切到离线引擎。 */
    fun activateOfflineMap(id: Long) {
        container.settings.activeOfflineMapId = id
        if (id > 0) container.settings.mapSource = MapSource.OFFLINE
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

    fun updateHighContrast(enabled: Boolean) {
        container.settings.highContrast = enabled
        _highContrast.value = enabled
    }

    fun updateLocalOnly(enabled: Boolean) {
        container.settings.localOnlyMode = enabled
        _localOnly.value = enabled
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        container.settings.keepScreenOnWhileRiding = enabled
        _keepScreenOn.value = enabled
    }

    fun updateLockOrientation(enabled: Boolean) {
        container.settings.lockOrientationWhileRiding = enabled
        _lockOrientation.value = enabled
    }

    fun updateUnitSystem(system: UnitSystem) {
        container.settings.unitSystem = system
    }

    fun updateAutoLapEnabled(enabled: Boolean) {
        container.settings.autoLapEnabled = enabled
        _autoLapEnabled.value = enabled
    }

    fun updateAutoLapDistance(km: Float) {
        val v = km.coerceIn(0.5f, 50f)
        container.settings.autoLapDistanceKm = v
        _autoLapDistanceKm.value = v
    }

    /** 清空本机全部骑行记录与轨迹点。 */
    fun clearAllRides() {
        viewModelScope.launch {
            container.rideRepository.clearAll()
        }
    }
}
