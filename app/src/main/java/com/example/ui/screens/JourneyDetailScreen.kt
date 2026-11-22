package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.models.JourneyRoute
import com.example.domain.models.JourneySegment
import com.example.domain.models.MapTileProvider
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import com.example.ui.components.CommuteMapView
import com.example.ui.components.GlassCard
import com.example.ui.components.ModeIconBadge
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.modeAccentColor
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.DelayAmber
import com.example.ui.theme.GlassBorder
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
fun JourneyDetailScreen(
    route: JourneyRoute,
    allStops: List<TransitStop>,
    tileProvider: MapTileProvider,
    isTrackingActive: Boolean,
    activeStepIndex: Int,
    onBack: () -> Unit,
    onCycleTileProvider: () -> Unit,
    onStartJourney: () -> Unit,
    onAdvanceStep: () -> Unit,
    onStopJourney: () -> Unit,
    onSaveRoute: () -> Unit,
    onAskAi: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val expandedSegments = remember(route.id) { mutableStateMapOf<String, Boolean>() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Top Bar (pinned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ButtonGreySurface)
                        .border(1.dp, ButtonGreyBorder, CircleShape)
                        .testTag("journey_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${route.badgeType.label} Itinerary",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${route.origin.name} → ${route.destination.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProvenanceBadge(provenance = route.scheduleProvenance)
            }

            // Full Interactive MapLibre Route Map (pinned outside LazyColumn to prevent GL buffer detachment on scroll)
            CommuteMapView(
                tileProvider = tileProvider,
                stops = allStops,
                selectedStopId = route.segments.getOrNull(activeStepIndex)?.fromStopId,
                activeRoute = route,
                focusLat = route.origin.lat,
                focusLon = route.origin.lon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .border(1.dp, GlassBorder, RoundedCornerShape(22.dp)),
                showZoomControls = true,
                showLegend = true,
                onCycleTileProvider = onCycleTileProvider
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Journey Overview Metrics
                item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = TimeFormatters.formatDuration(route.totalDurationMinutes),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${TimeFormatters.formatClock(route.departureSeconds)} – ${TimeFormatters.formatClock(route.arrivalSeconds)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${route.totalFareInr}",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MutedMint
                                )
                                Text(
                                    text = "${route.transferCount} transfers · ${TimeFormatters.formatDistance(route.totalWalkingMeters)} walk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isTrackingActive) onStopJourney() else onStartJourney()
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(46.dp)
                                    .testTag("start_journey_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTrackingActive) DelayAmber else ButtonGrey,
                                    contentColor = PureWhite
                                )
                            ) {
                                Icon(
                                    imageVector = if (isTrackingActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTrackingActive) "End Trip" else "Start Journey",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            IconActionPill(
                                icon = Icons.Rounded.BookmarkAdd,
                                description = "Save route",
                                tint = MutedMint,
                                tag = "save_route_button",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveRoute()
                                }
                            )
                            IconActionPill(
                                icon = Icons.Rounded.Share,
                                description = "Share route",
                                tint = TextPrimary,
                                tag = "share_route_button",
                                onClick = {
                                    val shareText = buildShareSummary(route)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Final_Build_0.1 Route: ${route.origin.name} → ${route.destination.name}")
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Journey"))
                                }
                            )
                            IconActionPill(
                                icon = Icons.Rounded.AutoAwesome,
                                description = "Ask AI about this journey",
                                tint = SoftViolet,
                                tag = "ask_ai_journey_button",
                                onClick = onAskAi
                            )
                        }
                    }
                }
            }

            // Live Journey Progress Interface when Start Journey is tapped
            if (isTrackingActive) {
                item {
                    val safeIdx = activeStepIndex.coerceIn(0, route.segments.lastIndex.coerceAtLeast(0))
                    val currentSeg = route.segments[safeIdx]
                    val progress = (safeIdx + 1).toFloat() / route.segments.size.coerceAtLeast(1).toFloat()
                    val isFinalStep = safeIdx == route.segments.lastIndex

                    GlassCard(
                        borderColor = PrimaryInk.copy(alpha = 0.55f),
                        backgroundColor = PureWhite
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "JOURNEY IN PROGRESS · STEP ${safeIdx + 1}/${route.segments.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryInk
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedMint
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = PrimaryInk,
                                trackColor = Color(0x220F172A)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ModeIconBadge(mode = currentSeg.mode, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentSeg.instruction,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${TimeFormatters.formatClock(currentSeg.departureSeconds)} → ${TimeFormatters.formatClock(currentSeg.arrivalSeconds)} (${currentSeg.durationMinutes} min)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isFinalStep) onStopJourney() else onAdvanceStep()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = ButtonGreySurface,
                                        contentColor = TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, ButtonGreyBorder),
                                    modifier = Modifier.testTag("journey_next_step_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MutedMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFinalStep) "Complete Arrival" else "Mark Step Done & Next",
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Segment Timeline
            item {
                Text(
                    text = "Step-by-Step Timeline",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            itemsIndexed(route.segments, key = { _, seg -> seg.id }) { idx, seg ->
                val isExpanded = expandedSegments[seg.id] ?: (idx == 0)
                val isActiveStep = isTrackingActive && idx == activeStepIndex
                val isDoneStep = isTrackingActive && idx < activeStepIndex

                SegmentTimelineCard(
                    stepNumber = idx + 1,
                    segment = seg,
                    isExpanded = isExpanded,
                    isHighlighted = isActiveStep,
                    isCompleted = isDoneStep,
                    onToggleExpand = { expandedSegments[seg.id] = !isExpanded }
                )
            }
            }
        }
    }
}

@Composable
private fun IconActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ButtonGreySurface)
            .border(1.dp, ButtonGreyBorder, RoundedCornerShape(14.dp))
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SegmentTimelineCard(
    stepNumber: Int,
    segment: JourneySegment,
    isExpanded: Boolean,
    isHighlighted: Boolean,
    isCompleted: Boolean,
    onToggleExpand: () -> Unit
) {
    val accent = modeAccentColor(segment.mode)
    val borderColor = when {
        isHighlighted -> PrimaryInk
        isCompleted -> MutedMint.copy(alpha = 0.55f)
        else -> GlassBorder
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_card_$stepNumber"),
        borderColor = borderColor,
        onClick = onToggleExpand
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeIconBadge(mode = segment.mode, size = 38.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Segment $stepNumber · ${segment.routeShortName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Completed",
                                tint = MutedMint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = segment.instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${segment.durationMinutes}m",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                        color = accent
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse segment" else "Expand segment",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftMist)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailMetricRow("Board / Start", "${segment.fromName} · ${TimeFormatters.formatClock(segment.departureSeconds)}")
                    DetailMetricRow("Exit / Arrive", "${segment.toName} · ${TimeFormatters.formatClock(segment.arrivalSeconds)}")
                    DetailMetricRow("Transport Mode", "${segment.mode.label} (${segment.routeLongName})")
                    if (segment.waitSeconds > 0) {
                        DetailMetricRow("Waiting Time", "${(segment.waitSeconds / 60).coerceAtLeast(1)} min at ${segment.fromName}")
                    }
                    DetailMetricRow(
                        label = if (segment.mode == TransitMode.WALK) "Walking Distance" else "Segment Distance",
                        value = TimeFormatters.formatDistance(segment.distanceMeters)
                    )
                    if (segment.fareInr > 0) {
                        DetailMetricRow("Estimated Leg Fare", "₹${segment.fareInr} (ESTIMATED)")
                    }
                    if (segment.intermediateStops.isNotEmpty()) {
                        DetailMetricRow("Via Stops (${segment.stopCount} hops)", segment.intermediateStops.joinToString(" · "))
                    }
                    if (segment.delayMinutes > 0) {
                        DetailMetricRow("Delay Impact", "+${segment.delayMinutes} min (${segment.delayProvenance.badgeText})", valueColor = DelayAmber)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetricRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun buildShareSummary(route: JourneyRoute): String = buildString {
    appendLine("Final_Build_0.1 Itinerary (${route.badgeType.label})")
    appendLine("${route.origin.name} → ${route.destination.name}")
    appendLine("Duration: ${TimeFormatters.formatDuration(route.totalDurationMinutes)} | Est. Fare: ₹${route.totalFareInr}")
    appendLine("Time: ${TimeFormatters.formatClock(route.departureSeconds)} → ${TimeFormatters.formatClock(route.arrivalSeconds)}")
    appendLine("Transfers: ${route.transferCount} | Walking: ${TimeFormatters.formatDistance(route.totalWalkingMeters)}")
    appendLine()
    route.segments.forEachIndexed { i, seg ->
        appendLine("${i + 1}. [${seg.mode.shortLabel}] ${seg.instruction} (${TimeFormatters.formatClock(seg.departureSeconds)} → ${TimeFormatters.formatClock(seg.arrivalSeconds)})")
    }
}
