package com.example.data.repositories

import android.content.Context
import com.example.data.local.CommuteDatabase
import com.example.data.local.GtfsCacheMetaEntity
import com.example.data.local.RecentSearchEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.local.ShortcutPlaceEntity
import com.example.data.remote.NetworkClientFactory
import com.example.domain.gtfs.GtfsParser
import com.example.domain.models.DataProvenance
import com.example.domain.models.GtfsDataset
import com.example.domain.models.JourneyRoute
import com.example.domain.models.PlaceLocation
import com.example.domain.models.RoutingOptions
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import com.example.domain.models.UpcomingDeparture
import com.example.domain.routing.MultimodalRoutingEngine
import com.example.domain.routing.TransitGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class TransitRepository(
    private val context: Context,
    private val database: CommuteDatabase,
    private val gtfsParser: GtfsParser = GtfsParser()
) {
    private val savedRouteDao = database.savedRouteDao()
    private val recentSearchDao = database.recentSearchDao()
    private val shortcutDao = database.shortcutPlaceDao()
    private val cacheMetaDao = database.gtfsCacheMetaDao()

    private val _dataset = MutableStateFlow<GtfsDataset>(loadInitialDataset())
    val dataset: StateFlow<GtfsDataset> = _dataset.asStateFlow()

    @Volatile
    private var graph: TransitGraph = TransitGraph(_dataset.value)

    @Volatile
    private var engine: MultimodalRoutingEngine = MultimodalRoutingEngine(graph)

    val routingEngine: MultimodalRoutingEngine get() = engine
    val transitGraph: TransitGraph get() = graph

    val savedRoutes: Flow<List<SavedRouteEntity>> = savedRouteDao.observeSavedRoutes()
    val recentSearches: Flow<List<RecentSearchEntity>> = recentSearchDao.observeRecentSearches()
    val shortcuts: Flow<List<ShortcutPlaceEntity>> = shortcutDao.observeShortcuts()
    val gtfsMetadata: Flow<GtfsCacheMetaEntity?> = cacheMetaDao.observeMetadata()

    val curatedDelhiLandmarks: List<PlaceLocation> = listOf(
        PlaceLocation("lm_cp", "Connaught Place, New Delhi", "Central Delhi Commercial Hub · Near Rajiv Chowk", 28.6315, 77.2167, "METRO_RAJIV_CHOWK", TransitMode.METRO),
        PlaceLocation("lm_mtj", "Mathura Junction", "NCR Intercity & MEMU Railway Hub", 27.4765, 77.6744, "RAIL_MATHURA", TransitMode.RAILWAY),
        PlaceLocation("lm_ndls", "New Delhi Railway Station", "Paharganj / Ajmeri Gate Rail & Metro Hub", 28.6429, 77.2191, "RAIL_NEW_DELHI", TransitMode.RAILWAY),
        PlaceLocation("lm_igi", "IGI Airport Terminal 3", "Airport Express Metro & DTC Express Bay", 28.5567, 77.0867, "METRO_IGI_T3", TransitMode.METRO),
        PlaceLocation("lm_hk", "Hauz Khas Village / IIT Gate", "South Delhi · Yellow & Magenta Interchange", 28.5433, 77.2066, "METRO_HAUZ_KHAS", TransitMode.METRO),
        PlaceLocation("lm_cyber", "DLF Cyber Hub, Gurugram", "Gurugram IT Corridor · Yellow Line & DTC", 28.4949, 77.0895, "BUS_CYBER_HUB", TransitMode.BUS),
        PlaceLocation("lm_noida18", "Noida Sector 18 / GIP Mall", "Noida Commercial Hub · Blue Line", 28.5708, 77.3261, "METRO_NOIDA_SEC18", TransitMode.METRO),
        PlaceLocation("lm_india_gate", "India Gate, New Delhi", "Central Vista · DTC 405 & Central Secretariat", 28.6129, 77.2295, "BUS_INDIA_GATE", TransitMode.BUS),
        PlaceLocation("lm_meerut", "Meerut South RRTS", "Namo Bharat Rapid Rail Corridor", 28.9150, 77.6410, "RRTS_MEERUT_SOUTH", TransitMode.RRTS),
        PlaceLocation("lm_anand_vihar", "Anand Vihar ISBT & Terminal", "East Delhi Multimodal Hub (Metro/Rail/RRTS/Bus)", 28.6469, 77.3160, "METRO_ANAND_VIHAR", TransitMode.RRTS)
    )

    private fun loadInitialDataset(): GtfsDataset {
        val cachedZip = File(context.filesDir, "gtfs_cache/delhi_ncr_latest.zip")
        if (cachedZip.exists() && cachedZip.length() > 512) {
            runCatching {
                cachedZip.inputStream().use { stream ->
                    val parsed = gtfsParser.parseFromZipStream(
                        inputStream = stream,
                        sourceLabel = "Cached Delhi OTD GTFS Feed",
                        provenance = DataProvenance.SCHEDULED
                    )
                    if (parsed.stops.isNotEmpty() && parsed.trips.isNotEmpty()) {
                        return parsed
                    }
                }
            }
        }
        return gtfsParser.parseFromAssets(context)
    }

    suspend fun initializeCacheMetadataAndDefaults() = withContext(Dispatchers.IO) {
        val ds = _dataset.value
        cacheMetaDao.upsertMetadata(
            GtfsCacheMetaEntity(
                sourceLabel = ds.sourceLabel,
                stopCount = ds.stops.size,
                routeCount = ds.routes.size,
                tripCount = ds.trips.size,
                provenance = ds.provenance.badgeText,
                updatedAtEpochMs = ds.loadedAtEpochMs
            )
        )
    }

    suspend fun searchLocations(
        query: String,
        offlineOnly: Boolean = false
    ): List<PlaceLocation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val allLocalStops = graph.stops.values.map { stop ->
            PlaceLocation(
                id = "stop_${stop.stopId}",
                name = stop.name,
                subtitle = "${stop.primaryMode.label} · ${stop.description.ifBlank { stop.zoneId }}",
                lat = stop.lat,
                lon = stop.lon,
                stopId = stop.stopId,
                modeHint = stop.primaryMode,
                source = "GTFS Stop"
            )
        }
        val combinedLocal = (curatedDelhiLandmarks + allLocalStops).distinctBy { it.name.lowercase() }

        if (trimmed.isBlank()) {
            return@withContext curatedDelhiLandmarks.take(8)
        }

        val lower = trimmed.lowercase()
        val localMatches = combinedLocal.filter {
            it.name.lowercase().contains(lower) || it.subtitle.lowercase().contains(lower)
        }.take(8)

        if (offlineOnly || trimmed.length < 3) {
            return@withContext localMatches
        }

        // Enrich with live Photon OpenStreetMap Geocoding API (biased to Delhi NCR)
        val remoteMatches = runCatching {
            val response = NetworkClientFactory.photonApi.searchPlaces(query = "$trimmed Delhi NCR")
            response.features.mapNotNull { feature ->
                val coords = feature.geometry?.coordinates ?: return@mapNotNull null
                if (coords.size < 2) return@mapNotNull null
                val lon = coords[0]
                val lat = coords[1]
                // Keep results within broader NCR / Mathura / Meerut bounding box
                if (lat !in 27.2..29.3 || lon !in 76.6..78.1) return@mapNotNull null
                val props = feature.properties ?: return@mapNotNull null
                val name = props.name ?: props.street ?: return@mapNotNull null
                val subtitle = listOfNotNull(props.street, props.city, props.state)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { "OpenStreetMap · Delhi NCR" }
                PlaceLocation(
                    id = "osm_${lat}_${lon}",
                    name = name,
                    subtitle = subtitle,
                    lat = lat,
                    lon = lon,
                    stopId = null,
                    modeHint = null,
                    source = "Photon OSM"
                )
            }
        }.getOrDefault(emptyList())

        (localMatches + remoteMatches).distinctBy { it.name.lowercase() }.take(10)
    }

    suspend fun computeRoutes(
        origin: PlaceLocation,
        destination: PlaceLocation,
        options: RoutingOptions,
        activeAlerts: List<TransitAlert>
    ): List<JourneyRoute> = withContext(Dispatchers.Default) {
        engine.findMultimodalRoutes(origin, destination, options, activeAlerts)
    }

    fun getNearbyStops(lat: Double, lon: Double, modeFilter: TransitMode? = null, limit: Int = 20): List<Pair<TransitStop, Int>> {
        return graph.stops.values
            .filter { modeFilter == null || it.primaryMode == modeFilter }
            .map { stop -> stop to TransitGraph.haversineMeters(lat, lon, stop.lat, stop.lon) }
            .sortedBy { it.second }
            .take(limit)
    }

    fun getUpcomingDepartures(stopId: String, currentSeconds: Int, alerts: List<TransitAlert>): List<UpcomingDeparture> {
        return engine.getUpcomingDepartures(stopId, currentSeconds, alerts)
    }

    suspend fun refreshGtfsFeed(backendBaseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val cacheZip = File(context.filesDir, "gtfs_cache/delhi_ncr_latest.zip")
        val candidateUrls = listOfNotNull(
            backendBaseUrl.takeIf { it.startsWith("http") }?.trimEnd('/')?.plus("/api/transit/gtfs.zip"),
            "https://otd.delhi.gov.in/data/static/GTFS.zip"
        )
        for (url in candidateUrls) {
            val downloaded = NetworkClientFactory.downloadGtfsZipToCache(url, cacheZip)
            if (downloaded != null) {
                val parsed = runCatching {
                    downloaded.inputStream().use {
                        gtfsParser.parseFromZipStream(it, "Delhi OTD Cached Feed", DataProvenance.SCHEDULED)
                    }
                }.getOrNull()
                if (parsed != null && parsed.stops.isNotEmpty() && parsed.trips.isNotEmpty()) {
                    _dataset.value = parsed
                    graph = TransitGraph(parsed)
                    engine = MultimodalRoutingEngine(graph)
                    cacheMetaDao.upsertMetadata(
                        GtfsCacheMetaEntity(
                            sourceLabel = parsed.sourceLabel,
                            stopCount = parsed.stops.size,
                            routeCount = parsed.routes.size,
                            tripCount = parsed.trips.size,
                            provenance = parsed.provenance.badgeText,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                    return@withContext Result.success("Synced live GTFS archive (${parsed.stops.size} stops, ${parsed.routes.size} routes).")
                }
            }
        }
        // Gracefully reload bundled Delhi NCR GTFS & refresh timestamp so offline users stay operational
        val seeded = gtfsParser.parseFromAssets(context)
        _dataset.value = seeded
        graph = TransitGraph(seeded)
        engine = MultimodalRoutingEngine(graph)
        cacheMetaDao.upsertMetadata(
            GtfsCacheMetaEntity(
                sourceLabel = seeded.sourceLabel,
                stopCount = seeded.stops.size,
                routeCount = seeded.routes.size,
                tripCount = seeded.trips.size,
                provenance = seeded.provenance.badgeText,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        Result.success("Remote OTD feed unreachable — verified bundled Delhi NCR GTFS cache (${seeded.stops.size} stops, ${seeded.routes.size} routes).")
    }

    suspend fun saveJourney(route: JourneyRoute, customName: String? = null) {
        val label = customName?.takeIf { it.isNotBlank() } ?: "${route.origin.name} → ${route.destination.name}"
        val modes = route.modesUsed.joinToString(" → ") { it.shortLabel }
        savedRouteDao.insertSavedRoute(
            SavedRouteEntity(
                customName = label,
                originName = route.origin.name,
                originLat = route.origin.lat,
                originLon = route.origin.lon,
                originStopId = route.origin.stopId,
                destName = route.destination.name,
                destLat = route.destination.lat,
                destLon = route.destination.lon,
                destStopId = route.destination.stopId,
                badgeType = route.badgeType.label,
                durationMinutes = route.totalDurationMinutes,
                fareInr = route.totalFareInr,
                transferCount = route.transferCount,
                walkingMeters = route.totalWalkingMeters,
                modesSummary = modes
            )
        )
    }

    suspend fun renameSavedRoute(id: Long, newName: String) {
        if (newName.isNotBlank()) savedRouteDao.renameSavedRoute(id, newName.trim())
    }

    suspend fun deleteSavedRoute(id: Long) {
        savedRouteDao.deleteSavedRoute(id)
    }

    suspend fun recordRecentSearch(origin: PlaceLocation, destination: PlaceLocation) {
        recentSearchDao.deleteDuplicate(origin.name, destination.name)
        recentSearchDao.insertRecentSearch(
            RecentSearchEntity(
                originName = origin.name,
                originLat = origin.lat,
                originLon = origin.lon,
                originStopId = origin.stopId,
                destName = destination.name,
                destLat = destination.lat,
                destLon = destination.lon,
                destStopId = destination.stopId
            )
        )
    }

    suspend fun upsertShortcut(slotKey: String, place: PlaceLocation) {
        shortcutDao.upsertShortcut(
            ShortcutPlaceEntity(
                slotKey = slotKey,
                placeName = place.name,
                subtitle = place.subtitle,
                lat = place.lat,
                lon = place.lon,
                stopId = place.stopId
            )
        )
    }

    suspend fun clearRecentSearches() {
        recentSearchDao.clearAll()
    }
}
