package com.honglian.smartcycling.data

import kotlinx.coroutines.flow.Flow

class RideRepository(private val dao: RideDao) {

    fun observeRides(): Flow<List<RideEntity>> = dao.observeRides()

    suspend fun saveRide(ride: RideEntity, points: List<TrackPointEntity>): Long =
        dao.saveRide(ride, points)

    suspend fun trackPoints(rideId: Long): List<TrackPointEntity> = dao.trackPoints(rideId)

    suspend fun deleteRide(rideId: Long) = dao.deleteRide(rideId)
}
