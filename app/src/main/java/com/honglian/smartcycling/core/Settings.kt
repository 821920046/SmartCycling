package com.honglian.smartcycling.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** 主题模式:跟随系统 / 强制亮色 / 强制暗色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 地图数据源:高德在线引擎 / 本地离线引擎。 */
enum class MapSource { ONLINE, OFFLINE }

/**
 * 本地设置存储(SharedPreferences)。保存车轮尺寸、设备标识与骑行者名称。
 */
class Settings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("smartcycling", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 跨页面共享状态
    //
    // 第一性原理:mapSource / activeOfflineMapId 会被"设置页"(SettingsViewModel)与
    // "离线地图页"(OfflineMapsViewModel)**两个不同的 ViewModel** 读写。
    // 若各自缓存一份 MutableStateFlow,写入方更新后读取方不会感知 ——
    // 具体表现就是"在离线地图页激活了包,回到地图页却仍然显示高德在线图,直到重启 App"。
    //
    // 因此把这几项收敛为 Settings 内的**单一可观察数据源**,所有 ViewModel 共享同一条流,
    // 从根上消除"多份状态副本"这一缺陷类别。
    private val _mapType = MutableStateFlow(prefs.getInt(KEY_MAP_TYPE, 3))
    val mapTypeFlow: StateFlow<Int> = _mapType

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode

    private val _mapSource = MutableStateFlow(
        runCatching { MapSource.valueOf(prefs.getString(KEY_MAP_SOURCE, null) ?: "") }
            .getOrDefault(MapSource.ONLINE),
    )
    val mapSourceFlow: StateFlow<MapSource> = _mapSource

    private val _activeOfflineMapId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_MAP, 0L))
    val activeOfflineMapIdFlow: StateFlow<Long> = _activeOfflineMapId

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

    /** 地图样式类型: 1=标准 2=卫星 3=夜间 (对应高德常量)。默认夜间 HUD 贴合。 */
    var mapType: Int
        get() = _mapType.value
        set(value) {
            prefs.edit().putInt(KEY_MAP_TYPE, value).apply()
            _mapType.value = value
        }

    /** 主题模式,默认跟随系统。 */
    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
            _themeMode.value = value
        }

    /** 地图数据源:默认在线(高德);用户导入离线包后可切到离线。 */
    var mapSource: MapSource
        get() = _mapSource.value
        set(value) {
            prefs.edit().putString(KEY_MAP_SOURCE, value.name).apply()
            _mapSource.value = value
        }

    /** 当前激活的离线地图包 id;0 表示未选择。 */
    var activeOfflineMapId: Long
        get() = _activeOfflineMapId.value
        set(value) {
            prefs.edit().putLong(KEY_ACTIVE_MAP, value).apply()
            _activeOfflineMapId.value = value
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

    /** 仅本地模式:开启后骑行记录只保存在本机,不上传云端。 */
    var localOnlyMode: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_ONLY, false)
        set(value) { prefs.edit().putBoolean(KEY_LOCAL_ONLY, value).apply() }

    /**
     * 骑行时保持屏幕常亮(默认开)。
     *
     * 第一性原理:常亮只在"骑行中"这一种状态下才有意义 —— 用户需要随时瞥一眼仪表盘。
     * 若在全局无条件开启,则浏览历史/翻设置时屏幕也永不熄灭,纯属耗电。
     * 因此该开关只在进入骑行页时生效,退出骑行立即清除。
     */
    var keepScreenOnWhileRiding: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) { prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply() }

    /**
     * 骑行时锁定当前屏幕方向(默认关)。
     *
     * 手机固定在车把支架上时,路面颠簸会让重力感应误判方向,导致横竖屏来回切换。
     * 开启后进入骑行会锁定为进入瞬间的方向,退出骑行恢复自适应。
     */
    var lockOrientationWhileRiding: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ORIENTATION, false)
        set(value) { prefs.edit().putBoolean(KEY_LOCK_ORIENTATION, value).apply() }

    /**
     * 单位制(公制 / 英制)。默认公制。
     *
     * 只影响显示层:内部计算与落库永远是米 / km/h(见 [Units])。
     */
    private val _unitSystem = MutableStateFlow(
        runCatching { UnitSystem.valueOf(prefs.getString(KEY_UNIT_SYSTEM, null) ?: "") }
            .getOrDefault(UnitSystem.METRIC),
    )
    val unitSystemFlow: StateFlow<UnitSystem> = _unitSystem

    var unitSystem: UnitSystem
        get() = _unitSystem.value
        set(value) {
            prefs.edit().putString(KEY_UNIT_SYSTEM, value.name).apply()
            _unitSystem.value = value
        }

    /** 是否开启自动分圈。 */
    var autoLapEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAP, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_LAP, value).apply() }

    /**
     * 自动分圈距离(公里)。内部始终以公里存储,显示时再按单位制换算 ——
     * 这样"切成英制再切回公制"不会因为反复换算产生累计误差。
     */
    var autoLapDistanceKm: Float
        get() = prefs.getFloat(KEY_AUTO_LAP_DIST, 5.0f)
        set(value) { prefs.edit().putFloat(KEY_AUTO_LAP_DIST, value).apply() }

    /** 已记住的心率带 MAC 地址;非空时扫描到该设备会自动回连。 */
    var hrDeviceAddress: String
        get() = prefs.getString(KEY_HR_ADDR, "") ?: ""
        set(value) { prefs.edit().putString(KEY_HR_ADDR, value).apply() }

    /** 已记住的心率带名称(仅用于界面展示)。 */
    var hrDeviceName: String
        get() = prefs.getString(KEY_HR_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_HR_NAME, value).apply() }

    companion object {
        private const val KEY_WHEEL = "wheel_preset"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_RIDER = "rider_name"
        private const val KEY_SYNC_URL = "sync_url"
        private const val KEY_SYNC_TOKEN = "sync_token"
        private const val KEY_MAP_TYPE = "map_type"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_MAP_SOURCE = "map_source"
        private const val KEY_ACTIVE_MAP = "active_offline_map_id"
        private const val KEY_WEIGHT = "rider_weight_kg"
        private const val KEY_AUTO_PAUSE = "auto_pause_enabled"
        private const val KEY_AUTO_PAUSE_TH = "auto_pause_threshold"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_ONBOARDING = "onboarding_shown"
        private const val KEY_LOCAL_ONLY = "local_only_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_while_riding"
        private const val KEY_LOCK_ORIENTATION = "lock_orientation_while_riding"
        private const val KEY_UNIT_SYSTEM = "unit_system"
        private const val KEY_AUTO_LAP = "auto_lap_enabled"
        private const val KEY_AUTO_LAP_DIST = "auto_lap_distance_km"
        private const val KEY_HR_ADDR = "hr_device_address"
        private const val KEY_HR_NAME = "hr_device_name"
    }
}

