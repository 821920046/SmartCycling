package com.honglian.smartcycling.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.honglian.smartcycling.SmartCyclingApp
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
}
