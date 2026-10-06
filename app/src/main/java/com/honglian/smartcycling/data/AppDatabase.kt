package com.honglian.smartcycling.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideEntity::class, TrackPointEntity::class, OfflineMapEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun offlineMapDao(): OfflineMapDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 → v2:新增离线地图注册表。
         *
         * 这里**必须**提供显式迁移:旧代码只配了 `fallbackToDestructiveMigration()`,
         * 一旦升级版本号而没有迁移,用户已有的全部骑行记录会被直接清空。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_cycling.db",
                )
                .addMigrations(MIGRATION_1_2)
                // 兜底:未来若再忘记写迁移,至少不会因 schema 不匹配而直接崩溃。
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }

    }
}
