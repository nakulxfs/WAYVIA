package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GtfsCacheMetaEntity
import com.example.data.local.UserPreferences
import com.example.domain.models.MapTileProvider
import com.example.domain.models.RoutePreference
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitMode
import com.example.services.location.DeviceLocationState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.components.modeAccentColor
import com.example.ui.components.modeIcon
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    deviceLocation: DeviceLocationState,
    gtfsMeta: GtfsCacheMetaEntity?,
    isSyncingGtfs: Boolean,
    onUpdateUserName: (String) -> Unit,
    onUpdateDefaultPreference: (RoutePreference) -> Unit,
    onTogglePreferredMode: (TransitMode) -> Unit,
    onUpdateMaxWalkMeters: (Int) -> Unit,
    onUpdateTileProvider: (MapTileProvider) -> Unit,
    onUpdateBackendUrl: (String) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onSyncGtfsFeed: () -> Unit,
    onRefreshLocation: () -> Unit
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(preferences.userName) { mutableStateOf(preferences.userName) }
    var backendUrlDraft by remember(preferences.backendApiBaseUrl) { mutableStateOf(preferences.backendApiBaseUrl) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshLocation()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Profile & Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            // User Identity Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(PureWhite)
                                .border(1.5.dp, GlassBorderStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = PrimaryInk,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preferences.userName,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Delhi NCR Region · Liquid Glass Light Theme",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { showEditNameDialog = true },
                            modifier = Modifier.testTag("profile_edit_name_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit profile name", tint = PrimaryInk)
                        }
                    }
                }
            }

            // Default Route Preference
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Default Route Preference",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoutePreference.entries.forEach { pref ->
                                val accent = when (pref) {
                                    RoutePreference.FASTEST -> PrimaryInk
                                    RoutePreference.CHEAPEST -> MutedMint
                                    RoutePreference.BALANCED -> SoftViolet
                                }
                                GlassPill(
                                    text = pref.label,
                                    selected = preferences.defaultRoutePreference == pref,
                                    accentColor = accent,
                                    modifier = Modifier.testTag("pref_route_${pref.name.lowercase()}"),
                                    onClick = { onUpdateDefaultPreference(pref) }
                                )
                            }
                        }
                    }
                }
            }

            // Preferred Transport Modes
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Preferred Transport Modes",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Toggle modes used by the multimodal graph routing engine (keep at least 1 active).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(TransitMode.METRO, TransitMode.BUS, TransitMode.RAILWAY, TransitMode.RRTS).forEach { mode ->
                                GlassPill(
                                    text = mode.shortLabel,
                                    icon = modeIcon(mode),
                                    selected = mode in preferences.preferredModes,
                                    accentColor = modeAccentColor(mode),
                                    modifier = Modifier.testTag("pref_mode_${mode.name.lowercase()}"),
                                    onClick = { onTogglePreferredMode(mode) }
                                )
                            }
                        }
                    }
                }
            }

            // Maximum Walking Distance Slider
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Maximum Walking Distance",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = TimeFormatters.formatDistance(preferences.maxWalkMeters),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                color = PrimaryInk
                            )
                        }
                        Slider(
                            value = preferences.maxWalkMeters.toFloat(),
                            onValueChange = { onUpdateMaxWalkMeters(it.toInt()) },
                            valueRange = 300f..2000f,
                            steps = 16,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryInk,
                                activeTrackColor = PrimaryInk
                            )
                        )
                        Text(
                            text = "Controls pedestrian transfer radius between metro stations, bus stops, and railway terminals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // Configurable Map Tile Provider
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Map Tile Provider (OpenStreetMap)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapTileProvider.entries.forEach { provider ->
                                GlassPill(
                                    text = provider.displayName,
                                    selected = preferences.mapTileProvider == provider,
                                    accentColor = PrimaryInk,
                                    onClick = { onUpdateTileProvider(provider) }
                                )
                            }
                        }
                    }
                }
            }

            // Location Permission & Offline Data Settings
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MutedMint)
                                Column {
                                    Text("Location Permission", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        text = if (deviceLocation.hasPermission) "Granted · ${deviceLocation.label}" else "Not granted · Using Delhi NCR default",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            if (!deviceLocation.hasPermission) {
                                TextButton(
                                    onClick = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                ) {
                                    Text("Enable", color = ButtonGrey)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline-First Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    text = "Skip remote Photon geocoding calls and use cached Delhi NCR GTFS data only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = preferences.offlineFirstMode,
                                onCheckedChange = onToggleOfflineMode,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureWhite,
                                    checkedTrackColor = PrimaryInk
                                )
                            )
                        }
                    }
                }
            }

            // GTFS Cache & Configurable Backend URL
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "GTFS Data & Backend API",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        val updatedLabel = remember(gtfsMeta?.updatedAtEpochMs) {
                            gtfsMeta?.updatedAtEpochMs?.let {
                                SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(it))
                            } ?: "Bundled Seed"
                        }
                        Text(
                            text = "Dataset: ${gtfsMeta?.sourceLabel ?: "Delhi NCR Seeded GTFS"}\n" +
                                "Nodes: ${gtfsMeta?.stopCount ?: 0} stops · ${gtfsMeta?.routeCount ?: 0} routes · ${gtfsMeta?.tripCount ?: 0} trips\n" +
                                "Freshness: $updatedLabel (${gtfsMeta?.provenance ?: "SCHEDULED"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        OutlinedTextField(
                            value = backendUrlDraft,
                            onValueChange = { backendUrlDraft = it },
                            label = { Text("FastAPI Backend Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onUpdateBackendUrl(backendUrlDraft)
                                    onSyncGtfsFeed()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_sync_gtfs_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ButtonGrey,
                                    contentColor = PureWhite
                                )
                            ) {
                                if (isSyncingGtfs) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = PureWhite,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh GTFS Cache", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // About & Attributions
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.commutebuddy_logo),
                            contentDescription = "Final_Build_0.1 logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorderStrong, RoundedCornerShape(12.dp))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("About Final_Build_0.1", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                text = "Smart multimodal public transit planner for Delhi NCR supporting DMRC Metro, DTC City Buses, Indian Railways Regional Trains, and NCRTC Namo Bharat Rapid Rail.\n\n" +
                                    "Open Data Attribution: OpenStreetMap contributors, MapLibre Native SDK, Photon Komoot Geocoding, and Delhi Open Transit Data (OTD).",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = PureWhite,
            title = { Text("Edit Profile Name", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text("Display Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUserName(nameDraft)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonGrey, contentColor = PureWhite)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
