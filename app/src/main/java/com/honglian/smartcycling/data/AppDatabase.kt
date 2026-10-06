package com.honglian.smartcycling.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideEntity::class, TrackPointEntity::class, OfflineMapEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun offlineMapDao(): OfflineMapDao

    companion object {
        /**
         * v1 → v2:为 rides 表新增热量与爬升列(默认 0,历史记录不丢失)。
         *
         * 这里**必须**提供显式迁移:旧代码只配了 `fallbackToDestructiveMigration()`,
         * 一旦升级版本号而没有迁移,用户已有的全部骑行记录会被直接清空。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN calories REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN elevationGainM REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 → v3:新增离线地图注册表 `offline_maps`。
         *
         * 说明:离线地图能力与"热量/爬升"来自两条并行开发线,合并后统一收敛到 v3。
         * 无论设备当前处于 v1(只跑了 1→2)还是 v2(其它分支构建),都能平滑升到 v3。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_maps` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `isDirectory` INTEGER NOT NULL,
                        `format` TEXT NOT NULL,
                        `crs` TEXT NOT NULL,
                        `minZoom` INTEGER NOT NULL,
                        `maxZoom` INTEGER NOT NULL,
                        `tileSize` INTEGER NOT NULL,
                        `tileCount` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `bounds` TEXT,
                        `sourceName` TEXT,
                        `importedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v3 → v4:为 rides 表新增心率列、为 track_points 新增海拔列。
         *
         * - 心率:可选外设(标准 BLE 心率带),未连接时为 0。
         * - 海拔:来自 GPS 高程,用于 GPX 导出与爬升回放。
         *
         * 三列均为 `NOT NULL DEFAULT 0`,与实体上的 `@ColumnInfo(defaultValue = "0")` 严格对应,
         * 否则 Room 的迁移后 schema 校验会失败。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN avgHeartRateBpm REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN maxHeartRateBpm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN elevationM REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 → v5:新增分圈与逐点心率。
         *
         * - `rides.lapDistanceM`:本次骑行用的自动分圈距离(0 = 未开启),让分圈可重算。
         * - `track_points.heartRateBpm`:逐点心率,是"分圈心率"的前提。
         *
         * 两列均为 `NOT NULL DEFAULT 0`,与实体上的 `@ColumnInfo(defaultValue = "0")` 严格对应。
         * 历史记录两列都取默认 0 → 表现为"没有分圈、没有心率",UI 自动降级,不会报错。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN lapDistanceM REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE track_points ADD COLUMN heartRateBpm INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_cycling.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // 兜底:未来若再忘记写迁移,至少不会因 schema 不匹配而直接崩溃。
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }

    }
}
