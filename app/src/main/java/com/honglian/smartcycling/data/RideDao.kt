package com.honglian.smartcycling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    /** 覆写整行。断点续记靠它把"进行中"的占位记录逐步更新为最终成绩。 */
    @Update
    suspend fun updateRide(ride: RideEntity)

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Transaction
    suspend fun saveRide(ride: RideEntity, points: List<TrackPointEntity>): Long {
        val rideId = insertRide(ride)
        if (points.isNotEmpty()) {
            insertTrackPoints(points.map { it.copy(rideId = rideId) })
        }
        return rideId
    }

    /**
     * 历史列表**只包含已完成**的骑行。
     *
     * `endedAt = 0` 是"进行中/未收尾"的哨兵值(见 RideViewModel 的断点续记):
     * 骑行一开始就落库,若进程被杀,这行会留在库里等待恢复。
     * 不把它过滤掉的话,历史里会出现一条永远不结束的记录。
     */
    @Query("SELECT * FROM rides WHERE endedAt > 0 ORDER BY startedAt DESC")
    fun observeRides(): Flow<List<RideEntity>>

    /** 取最近一条未收尾的骑行(用于崩溃/被杀后恢复)。 */
    @Query("SELECT * FROM rides WHERE endedAt = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinishedRide(): RideEntity?

    /** 已落库的轨迹点数量;断点续记据此只追加"还没写过"的点。 */
    @Query("SELECT COUNT(*) FROM track_points WHERE rideId = :rideId")
    suspend fun trackPointCount(rideId: Long): Int

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun ride(rideId: Long): RideEntity?

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestampMs ASC")
    suspend fun trackPoints(rideId: Long): List<TrackPointEntity>

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: Long)

    @Query("DELETE FROM rides")
    suspend fun deleteAllRides()

    @Query("DELETE FROM track_points")
    suspend fun deleteAllTrackPoints()

    @Transaction
    suspend fun clearAll() {
        deleteAllTrackPoints()
        deleteAllRides()
    }
}
