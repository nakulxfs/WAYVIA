package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.models.JourneyRoute
import com.example.domain.models.RouteBadgeType
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitMode
import com.example.ui.theme.DelayAmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun RouteComparisonCard(
    route: JourneyRoute,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeAccent = when (route.badgeType) {
        RouteBadgeType.FASTEST -> PrimaryInk
        RouteBadgeType.CHEAPEST -> MutedMint
        RouteBadgeType.BALANCED -> SoftViolet
    }
    val borderColor = if (isSelected) badgeAccent.copy(alpha = 0.65f) else GlassBorder

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_card_${route.badgeType.name.lowercase()}"),
        borderColor = borderColor,
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top badge + fare & duration hero metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFastest = route.badgeType == RouteBadgeType.FASTEST
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFastest) PrimaryInk else badgeAccent.copy(alpha = 0.14f))
                            .border(1.dp, if (isFastest) PrimaryInk else badgeAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = route.badgeType.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isFastest) PureWhite else badgeAccent
                        )
                    }
                    ProvenanceBadge(provenance = route.scheduleProvenance)
                }

                Text(
                    text = "₹${route.totalFareInr}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MutedMint
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary travel duration & clock departure-arrival
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
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
                        text = "${TimeFormatters.formatClock(route.departureSeconds)} → ${TimeFormatters.formatClock(route.arrivalSeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Transfers",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (route.transferCount == 0) "Direct" else "${route.transferCount} transfer${if (route.transferCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            contentDescription = "Walking distance",
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${TimeFormatters.formatDistance(route.totalWalkingMeters)} walk",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multimodal leg breakdown timeline strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                route.segments.forEachIndexed { idx, seg ->
                    val segColor = modeAccentColor(seg.mode)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(segColor.copy(alpha = 0.14f))
                            .border(1.dp, segColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon(seg.mode),
                            contentDescription = seg.mode.label,
                            tint = segColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (seg.mode == TransitMode.WALK) "${seg.durationMinutes}m" else seg.routeShortName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                    if (idx < route.segments.lastIndex) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (route.delayNotice != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DelayAmber.copy(alpha = 0.12f))
                        .border(1.dp, DelayAmber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Delay alert",
                            tint = DelayAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = route.delayNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = DelayAmber
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    ProvenanceBadge(provenance = route.delayProvenance)
                }
            }
        }
    }
}
