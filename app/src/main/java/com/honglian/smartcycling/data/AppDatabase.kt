package com.honglian.smartcycling.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideEntity::class, TrackPointEntity::class, OfflineMapEntity::class],
    version = 3,
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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_cycling.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // 兜底:未来若再忘记写迁移,至少不会因 schema 不匹配而直接崩溃。
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }

    }
}
