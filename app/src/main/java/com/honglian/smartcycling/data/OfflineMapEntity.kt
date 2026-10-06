package com.honglian.smartcycling.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 一个已导入的离线地图包(注册表)。
 *
 * 设计取舍:这里**不存瓦片本身**,只存元数据;瓦片仍然留在磁盘上的原始容器里
 * (MBTiles/ZIP 文件或瓦片目录)。理由:瓦片动辄数 GB,复制进数据库会带来双倍磁盘占用
 * 与巨大的迁移成本,而渲染引擎(osmdroid)本来就支持直接读取这些容器。
 */
@Entity(tableName = "offline_maps")
data class OfflineMapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 展示名(默认取文件名,可改)。 */
    val name: String,
    /** 相对 `filesDir/offline_maps/` 的路径:文件则为其文件名,目录则为其目录名。 */
    val relativePath: String,
    /** 是否为目录型(瓦片文件夹)。 */
    val isDirectory: Boolean,
    /** [com.honglian.smartcycling.offline.OfflineMapFormat] 的 name。 */
    val format: String,
    /** [com.honglian.smartcycling.offline.MapCrs] 的 name。 */
    val crs: String,
    val minZoom: Int,
    val maxZoom: Int,
    val tileSize: Int,
    /** 该包实际覆盖的瓦片数量(估算,用于展示与健康检查)。 */
    val tileCount: Long,
    /** 容器占用字节数。 */
    val sizeBytes: Long,
    /** 地理范围 `south,west,north,east`(包自身坐标系),未知为 null。 */
    val bounds: String?,
    /** 导入来源备注(原文件名),便于溯源。 */
    val sourceName: String?,
    val importedAt: Long,
)

@Dao
interface OfflineMapDao {
    @Query("SELECT * FROM offline_maps ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<OfflineMapEntity>>

    @Query("SELECT * FROM offline_maps WHERE id = :id")
    suspend fun findById(id: Long): OfflineMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OfflineMapEntity): Long

    @Query("UPDATE offline_maps SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE offline_maps SET crs = :crs WHERE id = :id")
    suspend fun updateCrs(id: Long, crs: String)

    @Query("DELETE FROM offline_maps WHERE id = :id")
    suspend fun delete(id: Long)
}
