package com.example.domain.models

enum class TransitMode(val label: String, val shortLabel: String) {
    METRO("Delhi Metro", "Metro"),
    BUS("DTC Bus", "Bus"),
    RAILWAY("Regional Rail", "Rail"),
    RRTS("Namo Bharat RRTS", "RRTS"),
    WALK("Walking", "Walk")
}

enum class DataProvenance(val badgeText: String, val description: String) {
    LIVE("LIVE", "Verified real-time transit feed"),
    SCHEDULED("SCHEDULED", "Published GTFS timetable"),
    SIMULATED("SIMULATED", "Sample simulation data for demonstration"),
    ESTIMATED("ESTIMATED", "Algorithmically estimated duration or fare")
}

enum class ServiceStatus(val label: String) {
    NORMAL("Normal Service"),
    MINOR_DELAY("Minor Delay"),
    HEAVY_DELAY("Heavy Delay"),
    DISRUPTED("Service Disruption")
}

enum class CrowdLevel(val label: String) {
    LOW("Low Crowd"),
    MODERATE("Moderate Crowd"),
    HIGH("High Crowd")
}

enum class RoutePreference(val label: String) {
    FASTEST("Fastest"),
    CHEAPEST("Cheapest"),
    BALANCED("Balanced")
}

enum class RouteBadgeType(val label: String, val subtitle: String) {
    FASTEST("FASTEST", "Minimum total journey time"),
    CHEAPEST("CHEAPEST", "Lowest estimated fare"),
    BALANCED("BALANCED", "Best mix of time, fare & transfers")
}

enum class MapTileProvider(
    val id: String,
    val displayName: String,
    val tileUrls: List<String>,
    val styleUri: String? = null,
    val attribution: String
) {
    OPENFREEMAP_POSITRON(
        id = "openfreemap_positron",
        displayName = "OpenFreeMap Light",
        tileUrls = emptyList(),
        styleUri = "https://tiles.openfreemap.org/styles/positron",
        attribution = "© OpenStreetMap contributors · OpenFreeMap"
    ),
    OPENFREEMAP_LIBERTY(
        id = "openfreemap_liberty",
        displayName = "OpenFreeMap Liberty",
        tileUrls = emptyList(),
        styleUri = "https://tiles.openfreemap.org/styles/liberty",
        attribution = "© OpenStreetMap contributors · OpenFreeMap"
    ),
    OSM_STANDARD(
        id = "osm_standard",
        displayName = "OpenStreetMap Standard",
        tileUrls = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        attribution = "© OpenStreetMap contributors"
    ),
    OPENFREEMAP_DARK(
        id = "openfreemap_dark",
        displayName = "OpenFreeMap Dark",
        tileUrls = emptyList(),
        styleUri = "https://tiles.openfreemap.org/styles/dark",
        attribution = "© OpenStreetMap contributors · OpenFreeMap"
    );

    companion object {
        // Legacy IDs (e.g. carto_dark, carto_voyager, openfreemap_fiord) seamlessly fall back to OPENFREEMAP_POSITRON.
        fun fromId(id: String): MapTileProvider = entries.find { it.id == id } ?: OPENFREEMAP_POSITRON
    }
}

data class PlaceLocation(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val modeHint: TransitMode? = null,
    val source: String = "Delhi NCR Transit"
)

data class TransitAgency(
    val agencyId: String,
    val name: String,
    val url: String,
    val timezone: String
)

data class TransitStop(
    val stopId: String,
    val stopCode: String,
    val name: String,
    val description: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val primaryMode: TransitMode
)

data class TransitRouteInfo(
    val routeId: String,
    val agencyId: String,
    val shortName: String,
    val longName: String,
    val mode: TransitMode,
    val colorHex: String
)

data class GtfsTrip(
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val headsign: String,
    val directionId: Int
)

data class GtfsStopTime(
    val tripId: String,
    val arrivalSeconds: Int,
    val departureSeconds: Int,
    val stopId: String,
    val stopSequence: Int
)

data class GtfsCalendar(
    val serviceId: String,
    val daysActive: BooleanArray, // Mon..Sun
    val startDate: String,
    val endDate: String
)

data class GtfsCalendarDate(
    val serviceId: String,
    val date: String,
    val exceptionType: Int // 1 = added, 2 = removed
)

data class GtfsFrequency(
    val tripId: String,
    val startSeconds: Int,
    val endSeconds: Int,
    val headwaySeconds: Int
)

data class GtfsDataset(
    val agencies: Map<String, TransitAgency>,
    val stops: Map<String, TransitStop>,
    val routes: Map<String, TransitRouteInfo>,
    val trips: Map<String, GtfsTrip>,
    val stopTimesByTrip: Map<String, List<GtfsStopTime>>,
    val calendars: Map<String, GtfsCalendar>,
    val calendarDates: List<GtfsCalendarDate>,
    val frequencies: Map<String, List<GtfsFrequency>>,
    val sourceLabel: String,
    val provenance: DataProvenance,
    val loadedAtEpochMs: Long
)

data class GeoPoint(val lat: Double, val lon: Double)

data class JourneySegment(
    val id: String,
    val mode: TransitMode,
    val routeId: String?,
    val routeShortName: String,
    val routeLongName: String,
    val headsign: String,
    val colorHex: String,
    val fromStopId: String?,
    val fromName: String,
    val fromLat: Double,
    val fromLon: Double,
    val toStopId: String?,
    val toName: String,
    val toLat: Double,
    val toLon: Double,
    val departureSeconds: Int,
    val arrivalSeconds: Int,
    val waitSeconds: Int,
    val distanceMeters: Int,
    val fareInr: Int,
    val stopCount: Int,
    val intermediateStops: List<String>,
    val pathCoordinates: List<GeoPoint>,
    val delayMinutes: Int = 0,
    val delayProvenance: DataProvenance = DataProvenance.SCHEDULED,
    val instruction: String
) {
    val durationMinutes: Int get() = ((arrivalSeconds - departureSeconds) / 60).coerceAtLeast(1)
}

data class JourneyRoute(
    val id: String,
    val badgeType: RouteBadgeType,
    val origin: PlaceLocation,
    val destination: PlaceLocation,
    val departureSeconds: Int,
    val arrivalSeconds: Int,
    val totalDurationMinutes: Int,
    val totalFareInr: Int,
    val transferCount: Int,
    val totalWalkingMeters: Int,
    val segments: List<JourneySegment>,
    val scheduleProvenance: DataProvenance,
    val fareProvenance: DataProvenance = DataProvenance.ESTIMATED,
    val activeDelayMinutes: Int = 0,
    val delayNotice: String? = null,
    val delayProvenance: DataProvenance = DataProvenance.SIMULATED,
    val modesUsed: List<TransitMode>
)

data class TransitAlert(
    val id: String,
    val title: String,
    val summary: String,
    val mode: TransitMode,
    val affectedRouteId: String?,
    val affectedStopId: String?,
    val status: ServiceStatus,
    val delayMinutes: Int,
    val provenance: DataProvenance,
    val updatedAtEpochMs: Long
)

data class StationCrowdStatus(
    val stopId: String,
    val stopName: String,
    val mode: TransitMode,
    val crowdLevel: CrowdLevel,
    val estimatedWaitExtraMinutes: Int,
    val provenance: DataProvenance,
    val updatedAtEpochMs: Long
)

data class UpcomingDeparture(
    val routeShortName: String,
    val headsign: String,
    val mode: TransitMode,
    val colorHex: String,
    val departureSeconds: Int,
    val minutesAway: Int,
    val delayMinutes: Int,
    val provenance: DataProvenance
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampLabel: String,
    val canSaveActiveRoute: Boolean = false,
    val suggestedFollowUps: List<String> = emptyList()
)

data class RoutingOptions(
    val departureSeconds: Int,
    val allowedModes: Set<TransitMode> = setOf(
        TransitMode.METRO,
        TransitMode.BUS,
        TransitMode.RAILWAY,
        TransitMode.RRTS
    ),
    val maxWalkMeters: Int = 1200,
    val maxTransfers: Int = 4,
    val activeDayOfWeekIndex: Int = 0 // 0=Mon..6=Sun
)

object TimeFormatters {
    fun formatClock(secondsSinceMidnight: Int): String {
        val normalized = ((secondsSinceMidnight % 86400) + 86400) % 86400
        val hours24 = normalized / 3600
        val minutes = (normalized % 3600) / 60
        val suffix = if (hours24 >= 12) "PM" else "AM"
        val hours12 = when {
            hours24 == 0 -> 12
            hours24 > 12 -> hours24 - 12
            else -> hours24
        }
        return "%d:%02d %s".format(hours12, minutes, suffix)
    }

    fun formatDuration(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    fun formatDistance(meters: Int): String {
        return if (meters < 1000) "${meters}m" else "%.1f km".format(meters / 1000.0)
    }
}
