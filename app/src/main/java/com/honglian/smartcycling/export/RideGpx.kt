package com.honglian.smartcycling.export

import com.honglian.smartcycling.data.RideEntity
import com.honglian.smartcycling.data.TrackPointEntity
import com.honglian.smartcycling.offline.GeoTransform
import com.honglian.smartcycling.offline.MapCrs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 把库内的骑行记录转换为 GPX 轨迹。
 *
 * **坐标契约(关键)**:应用内部所有来自高德的坐标都是 **GCJ-02**(火星坐标),
 * 而 GPX 是国际通用格式,行业约定使用 **WGS-84**。若原样导出,导入 Strava / Komoot /
 * Google Earth 后会整体偏移 300~600 米 —— 这是"导出轨迹看起来对、放到别的软件里却偏了"
 * 的根因。因此导出前统一做一次 GCJ-02 → WGS-84 纠偏。
 */
object RideGpx {

    fun fromRide(ride: RideEntity, points: List<TrackPointEntity>): GpxTrack {
        val wgs = points.map { p ->
            val w = GeoTransform.convert(p.latitude, p.longitude, MapCrs.GCJ02, MapCrs.WGS84)
            GpxPoint(
                latitude = w[0],
                longitude = w[1],
                elevationM = p.elevationM.takeIf { it != 0.0 },
                timeMs = p.timestampMs.takeIf { it > 0L },
            )
        }
        return GpxTrack(
            name = defaultName(ride.startedAt),
            points = wgs,
            startTimeMs = ride.startedAt,
        )
    }

    /** 批量转换(用于"导出全部骑行")。pointsOf 为挂起函数,直接对接 Room 查询。 */
    suspend fun fromRides(
        rides: List<RideEntity>,
        pointsOf: suspend (Long) -> List<TrackPointEntity>,
    ): List<GpxTrack> = rides.map { fromRide(it, pointsOf(it.id)) }

    /** 文件名 / 轨迹名:SmartCycling-20241006-1830。 */
    fun defaultName(startedAtMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return "SmartCycling-" + fmt.format(Date(startedAtMs))
    }
}
