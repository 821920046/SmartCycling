package com.honglian.smartcycling.core

import android.content.Context

/**
 * 本地设置存储(SharedPreferences)。目前保存车轮尺寸预设。
 */
class Settings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("smartcycling", Context.MODE_PRIVATE)

    var wheelPreset: WheelPreset
        get() = runCatching {
            WheelPreset.valueOf(prefs.getString(KEY_WHEEL, null) ?: "")
        }.getOrDefault(WheelPreset.DEFAULT)
        set(value) {
            prefs.edit().putString(KEY_WHEEL, value.name).apply()
        }

    /** 当前车轮周长(米)。 */
    val wheelCircumferenceM: Double get() = wheelPreset.circumferenceM

    companion object {
        private const val KEY_WHEEL = "wheel_preset"
    }
}
