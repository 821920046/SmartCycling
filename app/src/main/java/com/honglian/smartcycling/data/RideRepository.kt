package com.honglian.smartcycling.data

import kotlinx.coroutines.flow.Flow

class RideRepository(private val dao: RideDao) {

    fun observeRides(): Flow<List<RideEntity>> = dao.observeRides()

    suspend fun saveRide(ride: RideEntity, points: List<TrackPointEntity>): Long =
        dao.saveRide(ride, points)

    /** 开始骑行:先落一行 `endedAt = 0` 的占位记录,拿到 id 后才能增量追加轨迹点。 */
    suspend fun beginRide(ride: RideEntity): Long = dao.insertRide(ride)

    /** 把占位记录更新为最新状态(断点续记的周期性落盘 + 结束时的最终成绩)。 */
    suspend fun updateRide(ride: RideEntity) = dao.updateRide(ride)

    /** 追加轨迹点(只传"还没写过"的那部分)。 */
    suspend fun appendTrackPoints(points: List<TrackPointEntity>) = dao.insertTrackPoints(points)

    /** 最近一条未收尾的骑行。 */
    suspend fun unfinishedRide(): RideEntity? = dao.unfinishedRide()

    /** 某次骑行已落库的轨迹点数量。 */
    suspend fun trackPointCount(rideId: Long): Int = dao.trackPointCount(rideId)

    suspend fun ride(id: Long): RideEntity? = dao.ride(id)

    suspend fun trackPoints(rideId: Long): List<TrackPointEntity> = dao.trackPoints(rideId)

    suspend fun deleteRide(rideId: Long) = dao.deleteRide(rideId)

    suspend fun clearAll() = dao.clearAll()
}
