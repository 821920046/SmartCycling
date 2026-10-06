package com.honglian.smartcycling.offline

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid 全局初始化。
 *
 * 必须在**任何 MapView 创建之前**完成,否则:
 *  - `userAgentValue` 为空 → 若将来启用在线瓦片会被服务方封禁;
 *  - `osmdroidBasePath` 未设 → 在 Android 10+ 上 osmdroid 会尝试落到公共存储,
 *    可能因分区存储限制而反复失败。
 *
 * 这里把根目录指向应用私有目录,既免权限,也避免污染用户相册/下载目录。
 */
object OfflineMapEngine {

    private const val PREFS = "osmdroid_config"
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            runCatching {
                val cfg = Configuration.getInstance()
                cfg.load(
                    context.applicationContext,
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                )
                cfg.userAgentValue = context.packageName
                val base = File(context.filesDir, "osmdroid")
                val cache = File(base, "tiles")
                if (!cache.exists()) cache.mkdirs()
                cfg.osmdroidBasePath = base
                cfg.osmdroidTileCache = cache
            }
        }
    }
}
