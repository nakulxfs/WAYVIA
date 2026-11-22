package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.models.CrowdLevel
import com.example.domain.models.DataProvenance
import com.example.domain.models.ServiceStatus
import com.example.domain.models.TransitMode
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.CrowdHigh
import com.example.ui.theme.CrowdLow
import com.example.ui.theme.CrowdModerate
import com.example.ui.theme.DelayAmber
import com.example.ui.theme.DisruptionCoral
import com.example.ui.theme.ElevatedGlass
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.LightCanvas
import com.example.ui.theme.ModeBusColor
import com.example.ui.theme.ModeMetroColor
import com.example.ui.theme.ModeRailColor
import com.example.ui.theme.ModeRrtsColor
import com.example.ui.theme.ModeWalkColor
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightCanvas)
            .drawBehind {
                // Soft luminous white and pearl-mist radial glow for Light Liquid Glass depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xE6FFFFFF),
                            Color(0x55FFFFFF),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.08f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.16f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x166D28D9),
                            Color(0x0CCBD5E1),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.62f),
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = Offset(size.width * 0.88f, size.height * 0.62f)
                )
            },
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = ElevatedGlass,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val haptic = LocalHapticFeedback.current
    val clickableMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = PrimaryInk.copy(alpha = 0.12f))
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GlassHighlight,
                        Color.Transparent,
                        Color(0x0A0F172A)
                    )
                )
            )
            .border(width = 1.dp, color = borderColor, shape = shape)
            .then(clickableMod)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryInk,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val isMonochromeAccent = accentColor == PrimaryInk || accentColor == ButtonGrey
    val bgColor by animateColorAsState(
        targetValue = when {
            selected && isMonochromeAccent -> ButtonGrey
            selected -> accentColor.copy(alpha = 0.14f)
            else -> ButtonGreySurface
        },
        label = "pill_bg"
    )
    val border by animateColorAsState(
        targetValue = when {
            selected && isMonochromeAccent -> ButtonGrey
            selected -> accentColor.copy(alpha = 0.55f)
            else -> ButtonGreyBorder
        },
        label = "pill_border"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            selected && isMonochromeAccent -> PureWhite
            selected -> accentColor
            else -> TextPrimary
        },
        label = "pill_text"
    )
    val shape = RoundedCornerShape(999.dp)
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else Modifier
            ),
        shape = shape,
        color = bgColor,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProvenanceBadge(
    provenance: DataProvenance,
    modifier: Modifier = Modifier
) {
    val color = when (provenance) {
        DataProvenance.LIVE -> MutedMint
        DataProvenance.SCHEDULED -> PrimaryInk
        DataProvenance.SIMULATED -> DelayAmber
        DataProvenance.ESTIMATED -> SoftViolet
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (provenance == DataProvenance.SCHEDULED) PureWhite else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = provenance.badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun ServiceStatusBadge(status: ServiceStatus, delayMinutes: Int = 0) {
    val color = when (status) {
        ServiceStatus.NORMAL -> MutedMint
        ServiceStatus.MINOR_DELAY -> DelayAmber
        ServiceStatus.HEAVY_DELAY -> Color(0xFFEA580C)
        ServiceStatus.DISRUPTED -> DisruptionCoral
    }
    val label = if (delayMinutes > 0) "${status.label} (+${delayMinutes}m)" else status.label
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun CrowdLevelPill(level: CrowdLevel) {
    val color = when (level) {
        CrowdLevel.LOW -> CrowdLow
        CrowdLevel.MODERATE -> CrowdModerate
        CrowdLevel.HIGH -> CrowdHigh
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = level.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

fun modeAccentColor(mode: TransitMode): Color = when (mode) {
    TransitMode.METRO -> ModeMetroColor
    TransitMode.BUS -> ModeBusColor
    TransitMode.RAILWAY -> ModeRailColor
    TransitMode.RRTS -> ModeRrtsColor
    TransitMode.WALK -> ModeWalkColor
}

fun modeIcon(mode: TransitMode): ImageVector = when (mode) {
    TransitMode.METRO -> Icons.Rounded.DirectionsSubway
    TransitMode.BUS -> Icons.Rounded.DirectionsBus
    TransitMode.RAILWAY -> Icons.Rounded.Train
    TransitMode.RRTS -> Icons.Rounded.Tram
    TransitMode.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
}

@Composable
fun ModeIconBadge(
    mode: TransitMode,
    size: Dp = 32.dp
) {
    val color = modeAccentColor(mode)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = modeIcon(mode),
            contentDescription = mode.label,
            tint = color,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

enum class MainNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_tab_home"),
    EXPLORE("explore", "Explore", Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_tab_explore"),
    SAVED("saved", "Saved", Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder, "nav_tab_saved"),
    ASSISTANT("assistant", "Assistant", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_tab_assistant"),
    PROFILE("profile", "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_tab_profile")
}

@Composable
fun LiquidGlassBottomNavBar(
    currentRoute: String?,
    onTabSelected: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xEBFFFFFF))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFFFF), Color(0x0A0F172A))
                    )
                )
                .border(1.dp, GlassBorderStrong, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavTab.entries.forEach { tab ->
                val isSelected = currentRoute == tab.route
                val tint by animateColorAsState(
                    targetValue = if (isSelected) PureWhite else TextTertiary,
                    label = "nav_tint_${tab.route}"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) ButtonGrey else Color.Transparent,
                    label = "nav_bg_${tab.route}"
                )
                val iconSize by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 20.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "nav_size_${tab.route}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(pillBg)
                        .testTag(tab.testTag)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(tab)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) PureWhite else TextTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
