package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.models.MapTileProvider
import com.example.domain.models.StationCrowdStatus
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import com.example.ui.CommuteUiState
import com.example.ui.components.CommuteMapView
import com.example.ui.components.CrowdLevelPill
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.ModeIconBadge
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.modeAccentColor
import com.example.ui.components.modeIcon
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.DelayAmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMist
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    uiState: CommuteUiState,
    tileProvider: MapTileProvider,
    alerts: List<TransitAlert>,
    crowdStatuses: List<StationCrowdStatus>,
    onToggleMapMode: (Boolean) -> Unit,
    onSelectModeFilter: (TransitMode?) -> Unit,
    onSelectStop: (TransitStop?) -> Unit,
    onRouteToStop: (TransitStop) -> Unit,
    onCycleTileProvider: () -> Unit,
    onRecenterLocation: () -> Unit,
    onSimulateBusDelay: (Int) -> Unit,
    onSimulateTrainDelay: (Int) -> Unit,
    onCycleMetroCrowd: () -> Unit,
    onResetSimulation: () -> Unit
) {
    var showSimulatorSheet by remember { mutableStateOf(false) }
    val filteredStops = remember(uiState.nearbyStops) { uiState.nearbyStops.map { it.first } }
    val crowdByStop = remember(crowdStatuses) { crowdStatuses.associateBy { it.stopId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explore Header + Map/List Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Explore Delhi NCR",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "${filteredStops.size} multimodal hubs · OpenStreetMap",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill(
                    text = "Sim",
                    icon = Icons.Rounded.Tune,
                    accentColor = DelayAmber,
                    onClick = { showSimulatorSheet = true }
                )
                GlassPill(
                    text = if (uiState.exploreMapMode) "List" else "Map",
                    icon = if (uiState.exploreMapMode) Icons.AutoMirrored.Rounded.FormatListBulleted else Icons.Rounded.Map,
                    selected = true,
                    accentColor = PrimaryInk,
                    modifier = Modifier.testTag("explore_view_toggle"),
                    onClick = { onToggleMapMode(!uiState.exploreMapMode) }
                )
            }
        }

        // Mode Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassPill(
                text = "All Modes",
                selected = uiState.exploreModeFilter == null,
                accentColor = PrimaryInk,
                onClick = { onSelectModeFilter(null) }
            )
            listOf(TransitMode.METRO, TransitMode.BUS, TransitMode.RAILWAY, TransitMode.RRTS).forEach { mode ->
                GlassPill(
                    text = mode.shortLabel,
                    icon = modeIcon(mode),
                    selected = uiState.exploreModeFilter == mode,
                    accentColor = modeAccentColor(mode),
                    onClick = { onSelectModeFilter(mode) }
                )
            }
        }

        if (uiState.exploreMapMode) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 96.dp)
            ) {
                CommuteMapView(
                    tileProvider = tileProvider,
                    stops = filteredStops,
                    selectedStopId = uiState.selectedExploreStop?.stopId,
                    activeRoute = uiState.selectedRoute,
                    focusLat = uiState.deviceLocation.lat,
                    focusLon = uiState.deviceLocation.lon,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, GlassBorder, RoundedCornerShape(22.dp)),
                    showZoomControls = true,
                    showLegend = true,
                    onStopClicked = { onSelectStop(it) },
                    onCycleTileProvider = onCycleTileProvider,
                    onRecenterClicked = onRecenterLocation
                )

                // Floating Selected Stop Overlay Card on Map
                val selectedStop = uiState.selectedExploreStop
                if (selectedStop != null) {
                    val crowd = crowdByStop[selectedStop.stopId]
                    GlassCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        backgroundColor = Color(0xF5FFFFFF),
                        borderColor = modeAccentColor(selectedStop.primaryMode).copy(alpha = 0.45f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ModeIconBadge(mode = selectedStop.primaryMode, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedStop.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${selectedStop.primaryMode.label} · ${selectedStop.description.ifBlank { selectedStop.zoneId }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onSelectStop(null) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close stop sheet", tint = TextTertiary)
                                }
                            }

                            if (crowd != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CrowdLevelPill(level = crowd.crowdLevel)
                                    ProvenanceBadge(provenance = crowd.provenance)
                                }
                            }

                            if (uiState.selectedStopDepartures.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Next Scheduled Departures",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                    uiState.selectedStopDepartures.take(3).forEach { dep ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(SoftMist)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dep.routeShortName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = modeAccentColor(dep.mode)
                                                )
                                                Text(
                                                    text = dep.headsign,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = TimeFormatters.formatClock(dep.departureSeconds),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = if (dep.delayMinutes > 0) "+${dep.delayMinutes}m (${dep.provenance.badgeText})" else dep.provenance.badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (dep.delayMinutes > 0) DelayAmber else PrimaryInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { onRouteToStop(selectedStop) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ButtonGrey,
                                    contentColor = PureWhite
                                )
                            ) {
                                Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Route Here from Current Location", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // List View of Nearby Stops
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.nearbyStops, key = { it.first.stopId }) { (stop, dist) ->
                    val crowd = crowdByStop[stop.stopId]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSelectStop(stop)
                            onToggleMapMode(true)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ModeIconBadge(mode = stop.primaryMode, size = 40.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = stop.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${stop.primaryMode.label} · ${stop.description.ifBlank { stop.zoneId }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (crowd != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CrowdLevelPill(level = crowd.crowdLevel)
                                        ProvenanceBadge(provenance = crowd.provenance)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = TimeFormatters.formatDistance(dist),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryInk
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "View on map",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSimulatorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSimulatorSheet = false },
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
