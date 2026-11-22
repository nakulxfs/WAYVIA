package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SavedRouteEntity
import com.example.data.local.ShortcutPlaceEntity
import com.example.domain.models.PlaceLocation
import com.example.domain.models.TimeFormatters
import com.example.ui.components.GlassCard
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.DisruptionCoral
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftMist
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun SavedScreen(
    savedRoutes: List<SavedRouteEntity>,
    shortcuts: List<ShortcutPlaceEntity>,
    availableLandmarks: List<PlaceLocation>,
    onRepeatSavedRoute: (SavedRouteEntity) -> Unit,
    onRenameSavedRoute: (Long, String) -> Unit,
    onDeleteSavedRoute: (Long) -> Unit,
    onShortcutClicked: (ShortcutPlaceEntity) -> Unit,
    onUpdateShortcut: (String, PlaceLocation) -> Unit
) {
    var renamingRoute by remember { mutableStateOf<SavedRouteEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editingShortcutSlot by remember { mutableStateOf<String?>(null) }

    val shortcutByKey = remember(shortcuts) { shortcuts.associateBy { it.slotKey } }
    val shortcutSlots = listOf(
        Triple("HOME", Icons.Rounded.Home, MutedMint),
        Triple("WORK", Icons.Rounded.Work, PrimaryInk),
        Triple("COLLEGE", Icons.Rounded.School, SoftViolet)
    )

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
                Column {
                    Text(
                        text = "Saved Journeys",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Stored offline on your device via Room Database",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Commuter Shortcuts (Home, Work, College)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        shortcutSlots.forEach { (slot, icon, accent) ->
                            val existing = shortcutByKey[slot]
                            GlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("shortcut_card_${slot.lowercase()}"),
                                cornerRadius = 18.dp,
                                borderColor = accent.copy(alpha = 0.35f),
                                contentPadding = PaddingValues(12.dp),
                                onClick = {
                                    if (existing != null) {
                                        onShortcutClicked(existing)
                                    } else {
                                        editingShortcutSlot = slot
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = slot,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit $slot shortcut",
                                            tint = TextTertiary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { editingShortcutSlot = slot }
                                        )
                                    }
                                    Text(
                                        text = slot,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                    Text(
                                        text = existing?.placeName ?: "Tap to set",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Bookmarked Routes (${savedRoutes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            if (savedRoutes.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = PrimaryInk,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No saved routes yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Open any computed journey on Home and tap Save to keep it permanently available offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(savedRoutes, key = { it.id }) { saved ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("saved_route_card_${saved.id}"),
                        onClick = { onRepeatSavedRoute(saved) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = saved.customName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${saved.originName} → ${saved.destName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            renamingRoute = saved
                                            renameText = saved.customName
                                        },
                                        modifier = Modifier.testTag("rename_saved_${saved.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Rename saved route",
                                            tint = PrimaryInk,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSavedRoute(saved.id) },
                                        modifier = Modifier.testTag("delete_saved_${saved.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete saved route",
                                            tint = DisruptionCoral,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = TimeFormatters.formatDuration(saved.durationMinutes),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "₹${saved.fareInr}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                        color = MutedMint
                                    )
                                    Text(
                                        text = "${saved.transferCount} transfers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextTertiary
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ButtonGreySurface)
                                        .border(1.dp, ButtonGreyBorder, RoundedCornerShape(10.dp))
                                        .clickable { onRepeatSavedRoute(saved) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay,
                                        contentDescription = "Repeat search",
                                        tint = PrimaryInk,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Repeat",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrimaryInk
                                    )
                                }
                            }

                            Text(
                                text = saved.modesSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftViolet
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    renamingRoute?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingRoute = null },
            containerColor = PureWhite,
            title = { Text("Rename Saved Journey", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Journey name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSavedRoute(target.id, renameText)
                        renamingRoute = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonGrey, contentColor = PureWhite)
                ) {
                    Text("Save Name")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingRoute = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Shortcut Picker Dialog
    editingShortcutSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { editingShortcutSlot = null },
            containerColor = PureWhite,
            title = { Text("Assign $slot Location", color = TextPrimary) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableLandmarks, key = { it.id }) { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SoftMist)
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    onUpdateShortcut(slot, place)
                                    editingShortcutSlot = null
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(place.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(place.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editingShortcutSlot = null }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}
