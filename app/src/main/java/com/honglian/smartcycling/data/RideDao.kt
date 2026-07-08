package com.honglian.smartcycling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Insert
    suspend fun insertRide(ride: RideEntity): Long

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

    @Query("SELECT * FROM rides ORDER BY startedAt DESC")
    fun observeRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestampMs ASC")
    suspend fun trackPoints(rideId: Long): List<TrackPointEntity>

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: Long)
}
