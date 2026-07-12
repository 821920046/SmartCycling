package com.honglian.smartcycling.core

import android.content.Context
import java.util.UUID

/**
 * 本地设置存储(SharedPreferences)。保存车轮尺寸、设备标识与骑行者名称。
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

    /** 设备唯一标识(首次生成后持久化),用于云端去重。 */
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            return id
        }

    /** 骑行者显示名(默认用手机型号)。 */
    var riderName: String
        get() = prefs.getString(KEY_RIDER, null)?.takeIf { it.isNotBlank() }
            ?: (android.os.Build.MODEL ?: "rider")
        set(value) {
            prefs.edit().putString(KEY_RIDER, value).apply()
        }

    /** 自定义云同步中控 URL。 */
    var cloudSyncUrl: String
        get() = prefs.getString(KEY_SYNC_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SYNC_URL, value.trim()).apply()
        }

    /** 自定义云同步中控 Token。 */
    var cloudSyncToken: String
        get() = prefs.getString(KEY_SYNC_TOKEN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SYNC_TOKEN, value.trim()).apply()
        }

    /** 地图样式类型: 1=标准 2=卫星 3=夜间 (对应高德常量) */
    var mapType: Int
        get() = prefs.getInt(KEY_MAP_TYPE, 3) // 默认夜间 HUD 贴合
        set(value) {
            prefs.edit().putInt(KEY_MAP_TYPE, value).apply()
        }

    /** 骑手体重(kg),用于卡路里估算。 */
    var riderWeightKg: Float
        get() = prefs.getFloat(KEY_WEIGHT, 65f)
        set(value) { prefs.edit().putFloat(KEY_WEIGHT, value).apply() }

    /** 是否开启自动暂停(静止自动暂停计时)。 */
    var autoPauseEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_PAUSE, value).apply() }

    /** 自动暂停触发的速度阈值(km/h)。 */
    var autoPauseThresholdKmh: Float
        get() = prefs.getFloat(KEY_AUTO_PAUSE_TH, 1.5f)
        set(value) { prefs.edit().putFloat(KEY_AUTO_PAUSE_TH, value).apply() }

    /** 日照高对比模式(强光下提升仪表盘可读性)。 */
    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) { prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply() }

    /** 首次引导是否已展示。 */
    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING, value).apply() }

    companion object {
        private const val KEY_WHEEL = "wheel_preset"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_RIDER = "rider_name"
        private const val KEY_SYNC_URL = "sync_url"
        private const val KEY_SYNC_TOKEN = "sync_token"
        private const val KEY_MAP_TYPE = "map_type"
        private const val KEY_WEIGHT = "rider_weight_kg"
        private const val KEY_AUTO_PAUSE = "auto_pause_enabled"
        private const val KEY_AUTO_PAUSE_TH = "auto_pause_threshold"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_ONBOARDING = "onboarding_shown"
    }
}

