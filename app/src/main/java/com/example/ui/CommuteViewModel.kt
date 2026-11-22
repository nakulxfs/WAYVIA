package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.GtfsCacheMetaEntity
import com.example.data.local.RecentSearchEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.local.ShortcutPlaceEntity
import com.example.data.local.UserPreferences
import com.example.domain.models.ChatMessage
import com.example.domain.models.CrowdLevel
import com.example.domain.models.JourneyRoute
import com.example.domain.models.MapTileProvider
import com.example.domain.models.PlaceLocation
import com.example.domain.models.RouteBadgeType
import com.example.domain.models.RoutePreference
import com.example.domain.models.RoutingOptions
import com.example.domain.models.StationCrowdStatus
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import com.example.domain.models.UpcomingDeparture
import com.example.services.location.DeviceLocationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SearchFieldFocus {
    NONE, ORIGIN, DESTINATION
}

data class CommuteUiState(
    val originText: String = "Mathura Junction",
    val destinationText: String = "Connaught Place, New Delhi",
    val selectedOrigin: PlaceLocation? = null,
    val selectedDestination: PlaceLocation? = null,
    val activeFieldFocus: SearchFieldFocus = SearchFieldFocus.NONE,
    val suggestions: List<PlaceLocation> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val departureSeconds: Int = 8 * 3600 + 30 * 60, // 08:30 AM default active window
    val departurePresetLabel: String = "08:30 AM",
    val isSearchingRoutes: Boolean = false,
    val hasSearched: Boolean = false,
    val routeResults: List<JourneyRoute> = emptyList(),
    val selectedRoute: JourneyRoute? = null,
    val routeErrorMessage: String? = null,
    val activeTrackingSegmentIndex: Int = 0,
    val isJourneyTrackingActive: Boolean = false,
    val deviceLocation: DeviceLocationState = DeviceLocationState(),
    val exploreMapMode: Boolean = true,
    val exploreModeFilter: TransitMode? = null,
    val nearbyStops: List<Pair<TransitStop, Int>> = emptyList(),
    val selectedExploreStop: TransitStop? = null,
    val selectedStopDepartures: List<UpcomingDeparture> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isAssistantTyping: Boolean = false,
    val isSyncingGtfs: Boolean = false,
    val statusBannerMessage: String? = null
)

class CommuteViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.transitRepository
    private val prefsRepo = container.preferencesRepository
    private val awareness = container.awarenessEngine
    private val locationService = container.locationService
    private val chatEngine = container.chatEngine

    val userPreferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    val savedRoutes: StateFlow<List<SavedRouteEntity>> = repo.savedRoutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentSearches: StateFlow<List<RecentSearchEntity>> = repo.recentSearches.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val shortcuts: StateFlow<List<ShortcutPlaceEntity>> = repo.shortcuts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val gtfsMetadata: StateFlow<GtfsCacheMetaEntity?> = repo.gtfsMetadata.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val alerts: StateFlow<List<TransitAlert>> = awareness.alerts
    val crowdStatuses: StateFlow<List<StationCrowdStatus>> = awareness.crowdStatuses

    private val _uiState = MutableStateFlow(CommuteUiState())
    val uiState: StateFlow<CommuteUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null

    init {
        val defaultOrigin = repo.curatedDelhiLandmarks.find { it.id == "lm_mtj" }
        val defaultDest = repo.curatedDelhiLandmarks.find { it.id == "lm_cp" }
        val initialDepSec = computeSmartInitialDepartureSeconds()
        _uiState.update {
            it.copy(
                selectedOrigin = defaultOrigin,
                selectedDestination = defaultDest,
                originText = defaultOrigin?.name ?: "Mathura Junction",
                destinationText = defaultDest?.name ?: "Connaught Place, New Delhi",
                departureSeconds = initialDepSec.first,
                departurePresetLabel = initialDepSec.second,
                nearbyStops = repo.getNearbyStops(28.6315, 77.2167),
                chatMessages = listOf(chatEngine.createWelcomeMessage(null))
            )
        }
        viewModelScope.launch {
            repo.initializeCacheMetadataAndDefaults()
            refreshDeviceLocation()
            // Pre-seed default shortcuts if none exist
            if (shortcuts.value.isEmpty()) {
                val home = repo.curatedDelhiLandmarks.find { it.id == "lm_hk" }
                val work = repo.curatedDelhiLandmarks.find { it.id == "lm_cp" }
                val college = repo.curatedDelhiLandmarks.find { it.id == "lm_ndls" }
                if (home != null) repo.upsertShortcut("HOME", home)
                if (work != null) repo.upsertShortcut("WORK", work)
                if (college != null) repo.upsertShortcut("COLLEGE", college)
            }
            // Run initial search for the signature Mathura Junction -> Connaught Place journey so results are immediately demonstrable
            searchRoutes(recordHistory = false)
        }
    }

    private fun computeSmartInitialDepartureSeconds(): Pair<Int, String> {
        val cal = Calendar.getInstance()
        val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60
        // If current time is during Delhi NCR transit service hours (05:30 - 21:30), use live clock; else default to 08:30 AM peak demo window
        return if (nowSec in (5 * 3600 + 30 * 60)..(21 * 3600 + 30 * 60)) {
            nowSec to "Now"
        } else {
            (8 * 3600 + 30 * 60) to "08:30 AM"
        }
    }

    fun refreshDeviceLocation() {
        viewModelScope.launch {
            val loc = locationService.fetchCurrentLocation()
            _uiState.update { state ->
                state.copy(
                    deviceLocation = loc,
                    nearbyStops = repo.getNearbyStops(loc.lat, loc.lon, state.exploreModeFilter)
                )
            }
        }
    }

    fun useCurrentLocationAsOrigin() {
        viewModelScope.launch {
            val loc = locationService.fetchCurrentLocation()
            val place = locationService.toPlaceLocation(loc)
            _uiState.update {
                it.copy(
                    deviceLocation = loc,
                    selectedOrigin = place,
                    originText = place.name,
                    activeFieldFocus = SearchFieldFocus.NONE,
                    suggestions = emptyList()
                )
            }
        }
    }

    fun onOriginTextChanged(value: String) {
        _uiState.update {
            it.copy(
                originText = value,
                selectedOrigin = if (value == it.selectedOrigin?.name) it.selectedOrigin else null,
                activeFieldFocus = SearchFieldFocus.ORIGIN
            )
        }
        fetchSuggestions(value)
    }

    fun onDestinationTextChanged(value: String) {
        _uiState.update {
            it.copy(
                destinationText = value,
                selectedDestination = if (value == it.selectedDestination?.name) it.selectedDestination else null,
                activeFieldFocus = SearchFieldFocus.DESTINATION
            )
        }
        fetchSuggestions(value)
    }

    fun onFieldFocused(focus: SearchFieldFocus) {
        _uiState.update { it.copy(activeFieldFocus = focus) }
        val query = when (focus) {
            SearchFieldFocus.ORIGIN -> _uiState.value.originText
            SearchFieldFocus.DESTINATION -> _uiState.value.destinationText
            SearchFieldFocus.NONE -> ""
        }
        if (focus != SearchFieldFocus.NONE) {
            fetchSuggestions(query)
        }
    }

    fun dismissSuggestions() {
        _uiState.update { it.copy(activeFieldFocus = SearchFieldFocus.NONE, suggestions = emptyList()) }
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSuggestions = true) }
            delay(160)
            val results = repo.searchLocations(query, offlineOnly = userPreferences.value.offlineFirstMode)
            _uiState.update { it.copy(suggestions = results, isLoadingSuggestions = false) }
        }
    }

    fun selectSuggestion(place: PlaceLocation) {
        val focus = _uiState.value.activeFieldFocus
        _uiState.update { state ->
            when (focus) {
                SearchFieldFocus.ORIGIN -> state.copy(
                    selectedOrigin = place,
                    originText = place.name,
                    activeFieldFocus = SearchFieldFocus.NONE,
                    suggestions = emptyList()
                )
                SearchFieldFocus.DESTINATION -> state.copy(
                    selectedDestination = place,
                    destinationText = place.name,
                    activeFieldFocus = SearchFieldFocus.NONE,
                    suggestions = emptyList()
                )
                SearchFieldFocus.NONE -> state
            }
        }
    }

    fun swapOriginAndDestination() {
        _uiState.update { state ->
            state.copy(
                originText = state.destinationText,
                destinationText = state.originText,
                selectedOrigin = state.selectedDestination,
                selectedDestination = state.selectedOrigin,
                activeFieldFocus = SearchFieldFocus.NONE,
                suggestions = emptyList()
            )
        }
    }

    fun setDepartureWindow(seconds: Int, label: String) {
        _uiState.update { it.copy(departureSeconds = seconds, departurePresetLabel = label) }
        if (_uiState.value.hasSearched) {
            searchRoutes(recordHistory = false)
        }
    }

    fun searchRoutes(
        preferredBadgeOverride: RoutePreference? = null,
        recordHistory: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingRoutes = true,
                    routeErrorMessage = null,
                    activeFieldFocus = SearchFieldFocus.NONE,
                    suggestions = emptyList()
                )
            }
            val state = _uiState.value
            val origin = state.selectedOrigin ?: repo.searchLocations(state.originText, offlineOnly = userPreferences.value.offlineFirstMode).firstOrNull()
            val destination = state.selectedDestination ?: repo.searchLocations(state.destinationText, offlineOnly = userPreferences.value.offlineFirstMode).firstOrNull()

            if (origin == null || destination == null) {
                _uiState.update {
                    it.copy(
                        isSearchingRoutes = false,
                        hasSearched = true,
                        routeResults = emptyList(),
                        routeErrorMessage = "Could not resolve origin or destination in Delhi NCR. Pick a suggestion from the list."
                    )
                }
                return@launch
            }

            if (origin.name.equals(destination.name, ignoreCase = true) && origin.stopId == destination.stopId) {
                _uiState.update {
                    it.copy(
                        isSearchingRoutes = false,
                        hasSearched = true,
                        routeResults = emptyList(),
                        routeErrorMessage = "Origin and destination are the same location. Please enter two distinct points."
                    )
                }
                return@launch
            }

            val prefs = userPreferences.value
            val options = RoutingOptions(
                departureSeconds = state.departureSeconds,
                allowedModes = prefs.preferredModes,
                maxWalkMeters = prefs.maxWalkMeters,
                maxTransfers = 4
            )
            val routes = repo.computeRoutes(origin, destination, options, awareness.alerts.value)
            if (recordHistory) {
                repo.recordRecentSearch(origin, destination)
            }

            val targetPref = preferredBadgeOverride ?: prefs.defaultRoutePreference
            val orderedRoutes = reorderByPreference(routes, targetPref)
            val primarySelection = orderedRoutes.firstOrNull()

            _uiState.update {
                it.copy(
                    selectedOrigin = origin,
                    selectedDestination = destination,
                    originText = origin.name,
                    destinationText = destination.name,
                    isSearchingRoutes = false,
                    hasSearched = true,
                    routeResults = orderedRoutes,
                    selectedRoute = primarySelection,
                    isJourneyTrackingActive = false,
                    activeTrackingSegmentIndex = 0,
                    routeErrorMessage = if (orderedRoutes.isEmpty()) {
                        "No feasible transit route found for ${state.departurePresetLabel} with your enabled modes. Try adjusting departure time or enabling Metro/Bus/Rail."
                    } else null
                )
            }
        }
    }

    private fun reorderByPreference(routes: List<JourneyRoute>, pref: RoutePreference): List<JourneyRoute> {
        val preferredBadge = when (pref) {
            RoutePreference.FASTEST -> RouteBadgeType.FASTEST
            RoutePreference.CHEAPEST -> RouteBadgeType.CHEAPEST
            RoutePreference.BALANCED -> RouteBadgeType.BALANCED
        }
        return routes.sortedWith(
            compareByDescending<JourneyRoute> { it.badgeType == preferredBadge }
                .thenBy { it.badgeType.ordinal }
        )
    }

    fun selectRoute(route: JourneyRoute) {
        _uiState.update {
            it.copy(
                selectedRoute = route,
                isJourneyTrackingActive = false,
                activeTrackingSegmentIndex = 0
            )
        }
    }

    fun startJourneyTracking() {
        _uiState.update { it.copy(isJourneyTrackingActive = true, activeTrackingSegmentIndex = 0) }
    }

    fun advanceJourneyTrackingStep() {
        val maxIdx = (_uiState.value.selectedRoute?.segments?.lastIndex ?: 0).coerceAtLeast(0)
        _uiState.update { state ->
            val next = (state.activeTrackingSegmentIndex + 1).coerceAtMost(maxIdx)
            state.copy(activeTrackingSegmentIndex = next)
        }
    }

    fun stopJourneyTracking() {
        _uiState.update { it.copy(isJourneyTrackingActive = false, activeTrackingSegmentIndex = 0) }
    }

    fun repeatSearch(originName: String, originLat: Double, originLon: Double, originStopId: String?, destName: String, destLat: Double, destLon: Double, destStopId: String?) {
        val origin = PlaceLocation("rep_o", originName, "Saved Origin", originLat, originLon, originStopId)
        val dest = PlaceLocation("rep_d", destName, "Saved Destination", destLat, destLon, destStopId)
        _uiState.update {
            it.copy(
                originText = origin.name,
                destinationText = dest.name,
                selectedOrigin = origin,
                selectedDestination = dest
            )
        }
        searchRoutes(recordHistory = true)
    }

    fun routeToShortcut(shortcut: ShortcutPlaceEntity) {
        val loc = locationService.toPlaceLocation(_uiState.value.deviceLocation)
        val dest = PlaceLocation(
            id = "sc_${shortcut.slotKey}",
            name = shortcut.placeName,
            subtitle = shortcut.subtitle,
            lat = shortcut.lat,
            lon = shortcut.lon,
            stopId = shortcut.stopId
        )
        _uiState.update {
            it.copy(
                originText = loc.name,
                selectedOrigin = loc,
                destinationText = dest.name,
                selectedDestination = dest
            )
        }
        searchRoutes(recordHistory = true)
    }

    // Explore Map & Nearby Stops
    fun toggleExploreMapMode(showMap: Boolean) {
        _uiState.update { it.copy(exploreMapMode = showMap) }
    }

    fun setExploreModeFilter(mode: TransitMode?) {
        val loc = _uiState.value.deviceLocation
        _uiState.update {
            it.copy(
                exploreModeFilter = mode,
                nearbyStops = repo.getNearbyStops(loc.lat, loc.lon, mode)
            )
        }
    }

    fun selectExploreStop(stop: TransitStop?) {
        if (stop == null) {
            _uiState.update { it.copy(selectedExploreStop = null, selectedStopDepartures = emptyList()) }
            return
        }
        val departures = repo.getUpcomingDepartures(
            stopId = stop.stopId,
            currentSeconds = _uiState.value.departureSeconds,
            alerts = awareness.alerts.value
        )
        _uiState.update {
            it.copy(
                selectedExploreStop = stop,
                selectedStopDepartures = departures
            )
        }
    }

    fun setStopAsDestinationAndSearch(stop: TransitStop) {
        val loc = locationService.toPlaceLocation(_uiState.value.deviceLocation)
        val dest = PlaceLocation(
            id = "stop_${stop.stopId}",
            name = stop.name,
            subtitle = stop.primaryMode.label,
            lat = stop.lat,
            lon = stop.lon,
            stopId = stop.stopId,
            modeHint = stop.primaryMode
        )
        _uiState.update {
            it.copy(
                originText = loc.name,
                selectedOrigin = loc,
                destinationText = dest.name,
                selectedDestination = dest
            )
        }
        searchRoutes(recordHistory = true)
    }

    // AI Chat Assistant
    fun primeChatForActiveRoute() {
        val active = _uiState.value.selectedRoute
        if (active != null) {
            val contextMsg = chatEngine.createWelcomeMessage(active)
            _uiState.update { state ->
                val lastText = state.chatMessages.lastOrNull()?.text
                if (lastText == contextMsg.text) state else state.copy(chatMessages = state.chatMessages + contextMsg)
            }
        }
    }

    fun sendChatMessage(input: String) {
        val clean = input.trim()
        if (clean.isEmpty()) return
        val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = clean,
            timestampLabel = timestamp
        )
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isAssistantTyping = true
            )
        }
        viewModelScope.launch {
            delay(320) // Smooth realistic typing indicator without stalling
            val state = _uiState.value
            val reply = chatEngine.respond(
                userQuery = clean,
                activeRoute = state.selectedRoute,
                allRoutes = state.routeResults,
                alerts = awareness.alerts.value,
                crowdStatuses = awareness.crowdStatuses.value
            )
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + reply,
                    isAssistantTyping = false
                )
            }
        }
    }

    fun clearChatHistory() {
        _uiState.update {
            it.copy(chatMessages = listOf(chatEngine.createWelcomeMessage(it.selectedRoute)))
        }
    }

    // Saved Routes & Shortcuts
    fun saveCurrentOrRoute(route: JourneyRoute, customName: String? = null) {
        viewModelScope.launch {
            repo.saveJourney(route, customName)
            showBanner("Saved '${customName ?: "${route.origin.name} → ${route.destination.name}"}' offline.")
        }
    }

    fun renameSavedRoute(id: Long, newName: String) {
        viewModelScope.launch {
            repo.renameSavedRoute(id, newName)
            showBanner("Route renamed.")
        }
    }

    fun deleteSavedRoute(id: Long) {
        viewModelScope.launch {
            repo.deleteSavedRoute(id)
            showBanner("Saved route removed.")
        }
    }

    fun updateShortcut(slotKey: String, place: PlaceLocation) {
        viewModelScope.launch {
            repo.upsertShortcut(slotKey, place)
            showBanner("Updated $slotKey shortcut to ${place.name}.")
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { repo.clearRecentSearches() }
    }

    // Delay & Crowd Simulation Controls
    fun simulateBusDelay(minutes: Int) {
        awareness.simulateBusDelay(minutes)
        if (_uiState.value.hasSearched) searchRoutes(recordHistory = false)
        showBanner("SIMULATED: DTC Bus delay set to +${minutes}m.")
    }

    fun simulateTrainDelay(minutes: Int) {
        awareness.simulateTrainDelay(minutes)
        if (_uiState.value.hasSearched) searchRoutes(recordHistory = false)
        showBanner("SIMULATED: Regional Rail delay set to +${minutes}m.")
    }

    fun cycleMetroCrowd() {
        awareness.cycleMetroCrowd()
        showBanner("SIMULATED: Metro crowd levels updated.")
    }

    fun setStationCongestion(stopId: String, level: CrowdLevel) {
        awareness.simulateStationCongestion(stopId, level)
        showBanner("SIMULATED: Station congestion set to ${level.label}.")
    }

    fun resetAwarenessSimulation() {
        awareness.resetSimulation()
        if (_uiState.value.hasSearched) searchRoutes(recordHistory = false)
        showBanner("SIMULATED alerts & crowd reset to baseline.")
    }

    // Profile & GTFS Feed Management
    fun updateUserName(name: String) = viewModelScope.launch { prefsRepo.updateUserName(name) }
    fun updateDefaultRoutePreference(pref: RoutePreference) = viewModelScope.launch {
        prefsRepo.updateDefaultRoutePreference(pref)
        if (_uiState.value.routeResults.isNotEmpty()) {
            _uiState.update { it.copy(routeResults = reorderByPreference(it.routeResults, pref)) }
        }
    }
    fun togglePreferredMode(mode: TransitMode) = viewModelScope.launch {
        prefsRepo.toggleTransitMode(mode)
        delay(80)
        if (_uiState.value.hasSearched) searchRoutes(recordHistory = false)
    }
    fun updateMaxWalkMeters(meters: Int) = viewModelScope.launch { prefsRepo.updateMaxWalkMeters(meters) }
    fun updateMapTileProvider(provider: MapTileProvider) = viewModelScope.launch { prefsRepo.updateMapTileProvider(provider) }
    fun updateBackendUrl(url: String) = viewModelScope.launch { prefsRepo.updateBackendApiBaseUrl(url) }
    fun updateOfflineMode(enabled: Boolean) = viewModelScope.launch { prefsRepo.updateOfflineFirstMode(enabled) }

    fun syncGtfsDataset() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingGtfs = true) }
            val result = repo.refreshGtfsFeed(userPreferences.value.backendApiBaseUrl)
            _uiState.update {
                it.copy(
                    isSyncingGtfs = false,
                    nearbyStops = repo.getNearbyStops(it.deviceLocation.lat, it.deviceLocation.lon, it.exploreModeFilter)
                )
            }
            showBanner(result.getOrNull() ?: "GTFS sync finished.")
        }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun getAllStops(): List<TransitStop> = repo.transitGraph.stops.values.toList()
    fun getCuratedLandmarks(): List<PlaceLocation> = repo.curatedDelhiLandmarks

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CommuteViewModel(container) as T
            }
        }
    }
}
