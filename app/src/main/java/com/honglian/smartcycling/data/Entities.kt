package com.honglian.smartcycling.data

import androidx.room.ColumnInfo
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
    // 注意:以下两个字段是 v1→v2 迁移时用 `ADD COLUMN ... DEFAULT 0` 加的,当时未标注
    // defaultValue。为不改动既有设备上的实际 schema(避免校验不一致),这里保持原样不动。
    val calories: Double = 0.0,
    val elevationGainM: Double = 0.0,
    /**
     * 平均心率(bpm);未连接心率带时为 0。
     *
     * 标注 `defaultValue = "0"` 是**必需**的:新增列只能以
     * `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0` 方式加入,
     * 若实体不声明同样的默认值,Room 的 schema 校验会判定迁移未正确执行而抛异常。
     */
    @ColumnInfo(defaultValue = "0") val avgHeartRateBpm: Double = 0.0,
    /** 最大心率(bpm);未连接心率带时为 0。 */
    @ColumnInfo(defaultValue = "0") val maxHeartRateBpm: Int = 0,
    /**
     * 本次骑行使用的自动分圈距离(米);0 表示未开启分圈。
     *
     * 存下来是为了让分圈**可重算**:分圈边界由"轨迹点 + 这个阈值"共同决定,
     * 不存阈值的话,用户日后改设置会让历史记录的分圈跟着变。
     */
    @ColumnInfo(defaultValue = "0") val lapDistanceM: Double = 0.0,
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
    /** 海拔(米,来自 GPS 高程);0 表示无效值。用于 GPX 导出与爬升回放。 */
    @ColumnInfo(defaultValue = "0") val elevationM: Double = 0.0,
    /**
     * 该轨迹点时刻的心率(bpm);未连接心率带或该时刻无读数为 0。
     *
     * 逐点存心率是**分圈心率**的前提:分圈由轨迹点推导,若点上没有心率,
     * 就只能给出整段的平均值,间歇训练复盘时基本没用。
     */
    @ColumnInfo(defaultValue = "0") val heartRateBpm: Int = 0,
)
