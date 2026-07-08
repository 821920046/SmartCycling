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

    fun select(preset: WheelPreset) {
        container.settings.wheelPreset = preset
        container.sensorManager.wheelCircumferenceM = preset.circumferenceM
        _wheel.value = preset
    }
}
