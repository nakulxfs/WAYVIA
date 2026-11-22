package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.models.ChatMessage
import com.example.domain.models.JourneyRoute
import com.example.domain.models.TimeFormatters
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPill
import com.example.ui.theme.ButtonGrey
import com.example.ui.theme.ButtonGreyBorder
import com.example.ui.theme.ButtonGreySurface
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.MutedMint
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun AssistantScreen(
    messages: List<ChatMessage>,
    activeRoute: JourneyRoute?,
    isTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onClearConversation: () -> Unit,
    onSaveActiveRoute: (JourneyRoute) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val quickChips = remember(activeRoute) {
        if (activeRoute != null) {
            listOf(
                "Which route is the cheapest?",
                "How many transfers are required?",
                "Is there a faster alternative?",
                "What happens if my bus is delayed?",
                "How much walking is involved?",
                "Can I save this journey?"
            )
        } else {
            listOf(
                "Any active delays?",
                "Which stations are crowded?",
                "How does multimodal routing work?"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 102.dp)
        ) {
            // Header
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SoftViolet.copy(alpha = 0.18f))
                            .border(1.dp, SoftViolet.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = SoftViolet,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Transit Assistant",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Itinerary-aware · Offline deterministic engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onClearConversation,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ButtonGreySurface)
                        .border(1.dp, ButtonGreyBorder, CircleShape)
                        .testTag("chat_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Journey Context Banner
            if (activeRoute != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    borderColor = GlassBorderStrong,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE ITINERARY · ${activeRoute.badgeType.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryInk
                            )
                            Text(
                                text = "${activeRoute.origin.name} → ${activeRoute.destination.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${TimeFormatters.formatDuration(activeRoute.totalDurationMinutes)} · ₹${activeRoute.totalFareInr}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedMint
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        activeRoute = activeRoute,
                        onSaveRoute = onSaveActiveRoute
                    )
                }

                if (isTyping) {
                    item {
                        TypingIndicatorBubble()
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Suggested Question Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { chipText ->
                    GlassPill(
                        text = chipText,
                        accentColor = SoftViolet,
                        onClick = { onSendMessage(chipText) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PureWhite)
                    .border(1.dp, GlassBorderStrong, RoundedCornerShape(20.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    cursorBrush = SolidColor(PrimaryInk),
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Ask about fares, transfers, delays...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextTertiary
                            )
                        }
                        inner()
                    }
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ButtonGrey)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send question",
                        tint = PureWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    activeRoute: JourneyRoute?,
    onSaveRoute: (JourneyRoute) -> Unit
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleBg = if (message.isUser) PrimaryInk else Color(0xF2FFFFFF)
    val bubbleBorder = if (message.isUser) PrimaryInk else GlassBorder
    val messageTextColor = if (message.isUser) PureWhite else TextPrimary
    val timestampColor = if (message.isUser) Color(0xB3FFFFFF) else TextTertiary
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, bubbleShape)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = messageTextColor
            )

            if (message.canSaveActiveRoute && activeRoute != null) {
                Button(
                    onClick = { onSaveRoute(activeRoute) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonGrey,
                        contentColor = PureWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save This Journey", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = message.timestampLabel,
                style = MaterialTheme.typography.labelSmall,
                color = timestampColor,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dot1 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 140), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 280), RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF2FFFFFF))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { alphaVal ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alphaVal)
                    .clip(CircleShape)
                    .background(PrimaryInk)
            )
        }
    }
}
