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
    }
}

