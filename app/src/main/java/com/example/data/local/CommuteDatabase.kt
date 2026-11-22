package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedRouteEntity::class,
        RecentSearchEntity::class,
        ShortcutPlaceEntity::class,
        GtfsCacheMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CommuteDatabase : RoomDatabase() {
    abstract fun savedRouteDao(): SavedRouteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun shortcutPlaceDao(): ShortcutPlaceDao
    abstract fun gtfsCacheMetaDao(): GtfsCacheMetaDao

    companion object {
        @Volatile
        private var INSTANCE: CommuteDatabase? = null

        fun getInstance(context: Context): CommuteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CommuteDatabase::class.java,
                    "commutebuddy.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
