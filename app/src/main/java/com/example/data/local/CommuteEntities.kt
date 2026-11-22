package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customName: String,
    val originName: String,
    val originLat: Double,
    val originLon: Double,
    val originStopId: String?,
    val destName: String,
    val destLat: Double,
    val destLon: Double,
    val destStopId: String?,
    val badgeType: String,
    val durationMinutes: Int,
    val fareInr: Int,
    val transferCount: Int,
    val walkingMeters: Int,
    val modesSummary: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originName: String,
    val originLat: Double,
    val originLon: Double,
    val originStopId: String?,
    val destName: String,
    val destLat: Double,
    val destLon: Double,
    val destStopId: String?,
    val searchedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "shortcut_places")
data class ShortcutPlaceEntity(
    @PrimaryKey val slotKey: String, // HOME, WORK, COLLEGE
    val placeName: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val stopId: String?,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "gtfs_cache_meta")
data class GtfsCacheMetaEntity(
    @PrimaryKey val datasetKey: String = "delhi_ncr_primary",
    val sourceLabel: String,
    val stopCount: Int,
    val routeCount: Int,
    val tripCount: Int,
    val provenance: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
