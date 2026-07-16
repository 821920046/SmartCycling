package com.honglian.smartcycling.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一次完整骑行记录。 */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val durationSec: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val avgCadenceRpm: Double,
    val calories: Double = 0.0,
    val elevationGainM: Double = 0.0,
)

/** 轨迹点,关联到具体骑行。 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rideId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val timestampMs: Long,
)
