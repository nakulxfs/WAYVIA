package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ShortcutPlaceEntity
import com.example.data.local.UserPreferences
import com.example.domain.models.JourneyRoute
import com.example.domain.models.RoutePreference
import com.example.domain.models.StationCrowdStatus
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitStop
import com.example.ui.CommuteUiState
import com.example.ui.SearchFieldFocus
import com.example.ui.components.CommuteMapView
import com.example.ui.components.CrowdLevelPill
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.ModeIconBadge
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.RouteComparisonCard
import com.example.ui.components.ServiceStatusBadge
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.DelayAmber
import com.example.ui.theme.DisruptionCoral
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMist
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: CommuteUiState,
    preferences: UserPreferences,
    alerts: List<TransitAlert>,
    crowdStatuses: List<StationCrowdStatus>,
    recentSearches: List<RecentSearchEntity>,
    shortcuts: List<ShortcutPlaceEntity>,
    allStops: List<TransitStop>,
    onOriginChanged: (String) -> Unit,
    onDestinationChanged: (String) -> Unit,
    onFieldFocused: (SearchFieldFocus) -> Unit,
    onSuggestionSelected: (com.example.domain.models.PlaceLocation) -> Unit,
    onDismissSuggestions: () -> Unit,
    onSwapLocations: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRefreshLocation: () -> Unit,
    onSetDepartureWindow: (Int, String) -> Unit,
    onSearchRoutes: (RoutePreference?) -> Unit,
    onRouteClicked: (JourneyRoute) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenProfile: () -> Unit,
    onCycleTileProvider: () -> Unit,
    onShortcutClicked: (ShortcutPlaceEntity) -> Unit,
    onRecentSearchClicked: (RecentSearchEntity) -> Unit,
    onSimulateBusDelay: (Int) -> Unit,
    onSimulateTrainDelay: (Int) -> Unit,
    onCycleMetroCrowd: () -> Unit,
    onResetSimulation: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var swapCount by remember { mutableIntStateOf(0) }
    val swapRotation by animateFloatAsState(targetValue = swapCount * 180f, label = "swap_rot")
    var showAlertsSheet by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshLocation()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.commutebuddy_logo),
                            contentDescription = "Final_Build_0.1 logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .border(1.dp, ButtonGreyBorder, RoundedCornerShape(13.dp))
                                .testTag("home_app_logo")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Where are we heading?",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!uiState.deviceLocation.hasPermission) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } else {
                                        onRefreshLocation()
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = "Current location",
                                tint = PrimaryInk,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = uiState.deviceLocation.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeDelayCount = alerts.count { it.delayMinutes > 0 }
                        Box {
                            IconButton(
                                onClick = { showAlertsSheet = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ButtonGreySurface)
                                    .border(1.dp, ButtonGreyBorder, CircleShape)
                                    .testTag("home_alerts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = "Transit service alerts",
                                    tint = if (activeDelayCount > 0) DelayAmber else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (activeDelayCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(DelayAmber)
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonGreySurface)
                                .border(1.dp, ButtonGreyBorder, CircleShape)
                                .testTag("home_profile_avatar")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Profile and settings",
                                tint = PrimaryInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Main Glassmorphism Journey Search Component
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    borderColor = GlassBorderStrong,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlassSearchField(
                                    label = "FROM",
                                    value = uiState.originText,
                                    placeholder = "Mathura Junction, Hauz Khas...",
                                    dotColor = MutedMint,
                                    testTag = "origin_input",
                                    onValueChange = onOriginChanged,
                                    onFocused = { onFieldFocused(SearchFieldFocus.ORIGIN) },
                                    onClear = { onOriginChanged("") }
                                )

                                GlassSearchField(
                                    label = "TO",
                                    value = uiState.destinationText,
                                    placeholder = "Connaught Place, IGI Airport...",
                                    dotColor = PrimaryInk,
                                    testTag = "destination_input",
                                    onValueChange = onDestinationChanged,
                                    onFocused = { onFieldFocused(SearchFieldFocus.DESTINATION) },
                                    onClear = { onDestinationChanged("") }
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swapCount++
                                    onSwapLocations()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ButtonGreySurface)
                                    .border(1.dp, ButtonGreyBorder, CircleShape)
                                    .testTag("swap_locations_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = "Swap source and destination",
                                    tint = PrimaryInk,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(swapRotation)
                                )
                            }
                        }

                        // Current GPS shortcut + Departure window selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassPill(
                                text = "Use GPS",
                                icon = Icons.Rounded.MyLocation,
                                accentColor = MutedMint,
                                onClick = {
                                    if (!uiState.deviceLocation.hasPermission) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                    onUseCurrentLocation()
                                }
                            )
                            val nowCal = Calendar.getInstance()
                            val nowSecs = nowCal.get(Calendar.HOUR_OF_DAY) * 3600 + nowCal.get(Calendar.MINUTE) * 60
                            val presets = listOf(
                                "Now" to nowSecs,
                                "08:30 AM" to (8 * 3600 + 30 * 60),
                                "01:00 PM" to (13 * 3600),
                                "06:30 PM" to (18 * 3600 + 30 * 60)
                            )
                            presets.forEach { (label, secs) ->
                                GlassPill(
                                    text = label,
                                    icon = Icons.Rounded.Schedule,
                                    selected = uiState.departurePresetLabel == label,
                                    accentColor = SoftViolet,
                                    onClick = { onSetDepartureWindow(secs, label) }
                                )
                            }
                        }

                        // Inline Autocomplete Suggestions (GTFS + Photon OSM)
                        AnimatedVisibility(
                            visible = uiState.activeFieldFocus != SearchFieldFocus.NONE &&
                                (uiState.suggestions.isNotEmpty() || uiState.isLoadingSuggestions),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PureWhite)
                                    .border(1.dp, GlassBorderStrong, RoundedCornerShape(16.dp))
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isLoadingSuggestions) "Searching Delhi NCR & OSM..." else "Select a station or place",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close suggestions",
                                        tint = TextTertiary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDismissSuggestions() }
                                    )
                                }
                                uiState.suggestions.take(6).forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSuggestionSelected(suggestion) }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (suggestion.modeHint != null) {
                                            ModeIconBadge(mode = suggestion.modeHint, size = 28.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = PrimaryInk,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = suggestion.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = suggestion.source,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Search Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchRoutes(null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("search_routes_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonGrey,
                                contentColor = PureWhite
                            )
                        ) {
                            if (uiState.isSearchingRoutes) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = PureWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Find Multimodal Routes",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Quick Actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassPill(
                        text = "Find Fastest Route",
                        icon = Icons.Rounded.Bolt,
                        accentColor = PrimaryInk,
                        selected = preferences.defaultRoutePreference == RoutePreference.FASTEST,
                        modifier = Modifier.testTag("quick_fastest_button"),
                        onClick = { onSearchRoutes(RoutePreference.FASTEST) }
                    )
                    GlassPill(
                        text = "Find Cheapest Route",
                        icon = Icons.Rounded.CurrencyRupee,
                        accentColor = MutedMint,
                        selected = preferences.defaultRoutePreference == RoutePreference.CHEAPEST,
                        modifier = Modifier.testTag("quick_cheapest_button"),
                        onClick = { onSearchRoutes(RoutePreference.CHEAPEST) }
                    )
                    GlassPill(
                        text = "Explore Nearby Stations",
                        icon = Icons.Rounded.Explore,
                        accentColor = SoftViolet,
                        modifier = Modifier.testTag("quick_nearby_button"),
                        onClick = onOpenExplore
                    )
                }
            }

            // 4. Multimodal Route Comparison Results
            if (uiState.routeErrorMessage != null) {
                item {
                    GlassCard(
                        borderColor = DisruptionCoral.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = uiState.routeErrorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DisruptionCoral
                        )
                    }
                }
            }

            if (uiState.routeResults.isNotEmpty()) {
                // Lightweight MapLibre launch card (keeps OpenGL MapView dedicated to Explore & JourneyDetail without LazyColumn surface recycling)
                item {
                    val activePreview = uiState.selectedRoute ?: uiState.routeResults.first()
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_open_maplibre_card"),
                        cornerRadius = 20.dp,
                        borderColor = GlassBorderStrong,
                        contentPadding = PaddingValues(14.dp),
                        onClick = onOpenExplore
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PrimaryInk)
                                    .border(1.dp, PrimaryInk, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = "Open interactive MapLibre map",
                                    tint = PureWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "View Route on Interactive MapLibre Map",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${activePreview.badgeType.label} · ${activePreview.segments.size} segments · ${preferences.mapTileProvider.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Map →",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryInk
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multimodal Alternatives",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "${uiState.originText} → ${uiState.destinationText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${uiState.routeResults.size} routes",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryInk
                        )
                    }
                }

                items(uiState.routeResults, key = { it.id }) { route ->
                    RouteComparisonCard(
                        route = route,
                        isSelected = uiState.selectedRoute?.id == route.id,
                        onClick = { onRouteClicked(route) }
                    )
                }
            }

            // 5. Transit Service Alerts Preview (Clearly Tagged SIMULATED)
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAlertsSheet = true }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Transit Service Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                ProvenanceBadge(provenance = alerts.firstOrNull()?.provenance ?: com.example.domain.models.DataProvenance.SIMULATED)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Open simulator",
                                    tint = PrimaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Simulate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryInk
                                )
                            }
                        }

                        alerts.take(2).forEach { alert ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ModeIconBadge(mode = alert.mode, size = 32.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alert.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = alert.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ServiceStatusBadge(status = alert.status, delayMinutes = alert.delayMinutes)
                            }
                        }
                    }
                }
            }

            // 6. Saved Destination Shortcuts
            if (shortcuts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Saved Destinations",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            shortcuts.forEach { sc ->
                                GlassCard(
                                    modifier = Modifier.width(164.dp),
                                    cornerRadius = 18.dp,
                                    contentPadding = PaddingValues(12.dp),
                                    onClick = { onShortcutClicked(sc) }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = sc.slotKey,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryInk
                                        )
                                        Text(
                                            text = sc.placeName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Tap to route now",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Recent Journeys
            if (recentSearches.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent Journeys",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        GlassCard(contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp)) {
                            Column {
                                recentSearches.take(3).forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onRecentSearchClicked(item) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = SoftViolet,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.originName} → ${item.destName}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Tap to recompute schedule",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextTertiary
                                            )
                                        }
                                    }
                                    if (index < recentSearches.take(3).lastIndex) {
                                        HorizontalDivider(color = GlassBorder)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Nearby Transit Stops Preview
            if (uiState.nearbyStops.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby Transit Stops",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            TextButton(onClick = onOpenExplore) {
                                Text("Open Map", color = ButtonGrey)
                            }
                        }
                        uiState.nearbyStops.take(3).forEach { (stop, dist) ->
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                contentPadding = PaddingValues(12.dp),
                                onClick = onOpenExplore
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ModeIconBadge(mode = stop.primaryMode, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stop.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${stop.primaryMode.label} · ${stop.description.ifBlank { stop.zoneId }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = TimeFormatters.formatDistance(dist),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAlertsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlertsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = PureWhite,
            contentColor = TextPrimary
        ) {
            AwarenessSimulatorSheetContent(
                alerts = alerts,
                crowdStatuses = crowdStatuses,
                onSimulateBusDelay = onSimulateBusDelay,
                onSimulateTrainDelay = onSimulateTrainDelay,
                onCycleMetroCrowd = onCycleMetroCrowd,
                onResetSimulation = onResetSimulation
            )
        }
    }
}

@Composable
private fun GlassSearchField(
    label: String,
    value: String,
    placeholder: String,
    dotColor: Color,
    testTag: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftMist)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(PrimaryInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .onFocusChanged { if (it.isFocused) onFocused() },
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear $label",
                tint = TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onClear() }
            )
        }
    }
}

@Composable
fun AwarenessSimulatorSheetContent(
    alerts: List<TransitAlert>,
    crowdStatuses: List<StationCrowdStatus>,
    onSimulateBusDelay: (Int) -> Unit,
    onSimulateTrainDelay: (Int) -> Unit,
    onCycleMetroCrowd: () -> Unit,
    onResetSimulation: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delay & Crowd Awareness",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Clearly labeled sample simulator — never misrepresented as live feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                ProvenanceBadge(provenance = com.example.domain.models.DataProvenance.SIMULATED)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    text = "+15m Bus Delay",
                    accentColor = DelayAmber,
                    onClick = { onSimulateBusDelay(15) }
                )
                GlassPill(
                    text = "+20m Train Delay",
                    accentColor = DisruptionCoral,
                    onClick = { onSimulateTrainDelay(20) }
                )
                GlassPill(
                    text = "Cycle Metro Crowd",
                    accentColor = SoftViolet,
                    onClick = onCycleMetroCrowd
                )
                GlassPill(
                    text = "Reset Baseline",
                    accentColor = MutedMint,
                    onClick = onResetSimulation
                )
            }
        }

        item {
            Text("Service Alerts", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        items(alerts, key = { it.id }) { alert ->
            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeIconBadge(mode = alert.mode)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alert.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(alert.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ServiceStatusBadge(status = alert.status, delayMinutes = alert.delayMinutes)
                        ProvenanceBadge(provenance = alert.provenance)
                    }
                }
            }
        }

        item {
            Text("Station Crowd Indicators", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        items(crowdStatuses, key = { it.stopId }) { crowd ->
            GlassCard(contentPadding = PaddingValues(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(crowd.stopName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            text = "Platform buffer: +${crowd.estimatedWaitExtraMinutes}m · ${crowd.provenance.badgeText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    CrowdLevelPill(level = crowd.crowdLevel)
                }
            }
        }
    }
}
