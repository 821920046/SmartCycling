package com.honglian.smartcycling.cloud

import com.honglian.smartcycling.BuildConfig
import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 骑行结束后把记录上传到 Cloudflare 中控。
 * - 地址/令牌来自 BuildConfig(由 gradle.properties / -P / CI 注入)。
 * - 若未配置 CLOUD_SYNC_URL 则自动跳过,不影响本地记录。
 * - 使用 HttpURLConnection,无需额外依赖;失败不抛出,返回 Result。
 */
class CloudSyncRepository {

    val enabled: Boolean get() = BuildConfig.CLOUD_SYNC_URL.isNotBlank()

    suspend fun upload(
        deviceId: String,
        rider: String,
        ride: RideEntity,
        points: List<TrackPointEntity>,
        customUrl: String = "",
        customToken: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val base = (if (customUrl.isNotBlank()) customUrl else BuildConfig.CLOUD_SYNC_URL).trim().trimEnd('/')
        if (base.isBlank()) return@withContext Result.success(Unit)

        runCatching {
            val body = JSONObject().apply {
                put("id", "$deviceId-${ride.startedAt}")
                put("deviceId", deviceId)
                put("rider", rider)
                put("startedAt", ride.startedAt)
                put("endedAt", ride.endedAt)
                put("durationSec", ride.durationSec)
                put("distanceKm", ride.distanceKm)
                put("avgSpeedKmh", ride.avgSpeedKmh)
                put("maxSpeedKmh", ride.maxSpeedKmh)
                put("avgCadenceRpm", ride.avgCadenceRpm)
                val arr = JSONArray()
                points.forEach { p ->
                    arr.put(
                        JSONObject().apply {
                            put("lat", p.latitude)
                            put("lng", p.longitude)
                            put("speedKmh", p.speedKmh)
                            put("ts", p.timestampMs)
                        },
                    )
                }
                put("points", arr)
            }.toString()

            val conn = (URL("$base/api/rides").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val activeToken = if (customToken.isNotBlank()) customToken else BuildConfig.CLOUD_SYNC_TOKEN
                if (activeToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $activeToken")
                }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            if (code !in 200..299) error("云端返回 $code")
            Unit
        }
    }
}

