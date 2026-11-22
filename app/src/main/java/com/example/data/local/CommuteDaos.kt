package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAtEpochMs DESC")
    fun observeSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRoute(route: SavedRouteEntity): Long

    @Query("UPDATE saved_routes SET customName = :newName WHERE id = :id")
    suspend fun renameSavedRoute(id: Long, newName: String)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteSavedRoute(id: Long)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAtEpochMs DESC LIMIT 10")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE originName = :originName AND destName = :destName")
    suspend fun deleteDuplicate(originName: String, destName: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}

@Dao
interface ShortcutPlaceDao {
    @Query("SELECT * FROM shortcut_places ORDER BY slotKey ASC")
    fun observeShortcuts(): Flow<List<ShortcutPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShortcut(shortcut: ShortcutPlaceEntity)

    @Query("DELETE FROM shortcut_places WHERE slotKey = :slotKey")
    suspend fun deleteShortcut(slotKey: String)
}

@Dao
interface GtfsCacheMetaDao {
    @Query("SELECT * FROM gtfs_cache_meta WHERE datasetKey = 'delhi_ncr_primary' LIMIT 1")
    fun observeMetadata(): Flow<GtfsCacheMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(meta: GtfsCacheMetaEntity)
}
