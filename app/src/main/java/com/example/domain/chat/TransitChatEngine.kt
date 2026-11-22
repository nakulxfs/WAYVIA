package com.example.domain.chat

import com.example.domain.models.ChatMessage
import com.example.domain.models.DataProvenance
import com.example.domain.models.JourneyRoute
import com.example.domain.models.RouteBadgeType
import com.example.domain.models.StationCrowdStatus
import com.example.domain.models.TimeFormatters
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import com.example.domain.routing.MultimodalRoutingEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Deterministic itinerary-aware transit chat engine.
 * Grounds every answer strictly in computed routes, GTFS schedules,
 * and labeled delay/crowd states without requiring any external paid AI key.
 */
class TransitChatEngine(
    private val routingEngine: MultimodalRoutingEngine
) {
    private var lastIntentTopic: String? = null

    fun createWelcomeMessage(activeRoute: JourneyRoute?): ChatMessage {
        val body = if (activeRoute != null) {
            "I'm synced with your ${activeRoute.badgeType.label} route from ${activeRoute.origin.name} to ${activeRoute.destination.name} " +
                "(${TimeFormatters.formatDuration(activeRoute.totalDurationMinutes)}, ₹${activeRoute.totalFareInr}). Ask me about fares, transfers, walking legs, delays, or crowd levels."
        } else {
            "Hi! I'm your Final_Build_0.1 transit assistant for Delhi NCR. Search any route on Home or ask about fastest vs cheapest options, transfers, walking distances, or simulated delays."
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = body,
            timestampLabel = currentTimeLabel(),
            suggestedFollowUps = defaultSuggestions(activeRoute != null)
        )
    }

    fun respond(
        userQuery: String,
        activeRoute: JourneyRoute?,
        allRoutes: List<JourneyRoute>,
        alerts: List<TransitAlert>,
        crowdStatuses: List<StationCrowdStatus>
    ): ChatMessage {
        val q = userQuery.trim().lowercase(Locale.getDefault())

        if (activeRoute == null && allRoutes.isEmpty()) {
            return handleNoActiveRouteQuery(q, alerts, crowdStatuses)
        }

        val current = activeRoute ?: allRoutes.first()
        val fastest = allRoutes.minByOrNull { it.totalDurationMinutes } ?: current
        val cheapest = allRoutes.minByOrNull { it.totalFareInr } ?: current
        val transitLegs = current.segments.filter { it.mode != TransitMode.WALK }
        val walkLegs = current.segments.filter { it.mode == TransitMode.WALK }

        val response: Pair<String, Boolean> = when {
            containsAny(q, listOf("cheap", "budget", "cost", "fare", "price", "money", "rupee", "save money")) -> {
                lastIntentTopic = "fare"
                val diffFare = (fastest.totalFareInr - cheapest.totalFareInr).coerceAtLeast(0)
                val diffMin = (cheapest.totalDurationMinutes - fastest.totalDurationMinutes).coerceAtLeast(0)
                val modesSummary = cheapest.segments.filter { it.mode != TransitMode.WALK }
                    .joinToString(" → ") { "${it.routeShortName} (₹${it.fareInr})" }
                val text = buildString {
                    append("The cheapest option is the ${cheapest.badgeType.label} route at an estimated ₹${cheapest.totalFareInr} ")
                    append("(${TimeFormatters.formatDuration(cheapest.totalDurationMinutes)}, ${cheapest.transferCount} transfer(s)).\n\n")
                    append("Breakdown: $modesSummary.\n")
                    if (diffFare > 0) {
                        append("Choosing it saves ₹$diffFare compared to the fastest route, taking ~$diffMin min longer.")
                    } else {
                        append("It already matches the lowest fare among all computed alternatives.")
                    }
                    append("\n[Fare: ESTIMATED · Timetable: ${cheapest.scheduleProvenance.badgeText}]")
                }
                text to false
            }

            containsAny(q, listOf("fast", "quick", "earliest", "sooner", "shorter", "alternative")) -> {
                lastIntentTopic = "fastest"
                val text = if (current.id == fastest.id) {
                    "You're already viewing the fastest route (${TimeFormatters.formatDuration(fastest.totalDurationMinutes)}, arriving ${TimeFormatters.formatClock(fastest.arrivalSeconds)}). " +
                        if (allRoutes.size > 1) {
                            "The budget alternative saves ₹${(fastest.totalFareInr - cheapest.totalFareInr).coerceAtLeast(0)} for ₹${cheapest.totalFareInr} total."
                        } else {
                            "No faster scheduled connection is available in the current departure window."
                        }
                } else {
                    val minsSaved = (current.totalDurationMinutes - fastest.totalDurationMinutes).coerceAtLeast(0)
                    val extraFare = (fastest.totalFareInr - current.totalFareInr).coerceAtLeast(0)
                    "Yes — the FASTEST route takes ${TimeFormatters.formatDuration(fastest.totalDurationMinutes)} " +
                        "(saves $minsSaved min, arriving ${TimeFormatters.formatClock(fastest.arrivalSeconds)}) for ₹${fastest.totalFareInr} " +
                        "(+₹$extraFare) via ${fastest.segments.filter { it.mode != TransitMode.WALK }.joinToString(" → ") { it.routeShortName }}."
                }
                text to false
            }

            containsAny(q, listOf("transfer", "change", "interchange", "metro station", "switch")) -> {
                lastIntentTopic = "transfers"
                val text = if (current.transferCount == 0) {
                    "No transit transfers are required on this journey! You take a single direct ride on ${transitLegs.firstOrNull()?.routeShortName ?: "transit"} from ${transitLegs.firstOrNull()?.fromName} to ${transitLegs.firstOrNull()?.toName}."
                } else {
                    val points = buildList {
                        for (i in 0 until transitLegs.lastIndex) {
                            val incoming = transitLegs[i]
                            val outgoing = transitLegs[i + 1]
                            add("• Change at ${incoming.toName} → board ${outgoing.routeShortName} at ${outgoing.fromName} (${TimeFormatters.formatClock(outgoing.departureSeconds)}, wait ~${outgoing.waitSeconds / 60}m)")
                        }
                    }
                    "This route requires ${current.transferCount} transfer(s):\n" + points.joinToString("\n")
                }
                text to false
            }

            containsAny(q, listOf("walk", "walking", "foot", "steps", "distance")) -> {
                lastIntentTopic = "walking"
                val totalWalk = TimeFormatters.formatDistance(current.totalWalkingMeters)
                val walkDetails = walkLegs.joinToString("\n") { leg ->
                    "• ${leg.fromName} → ${leg.toName}: ${TimeFormatters.formatDistance(leg.distanceMeters)} (~${leg.durationMinutes} min)"
                }
                val text = if (walkLegs.isEmpty()) {
                    "Almost no walking is needed (under 50m total)."
                } else {
                    "Total walking distance is $totalWalk across ${walkLegs.size} segment(s):\n$walkDetails"
                }
                text to false
            }

            containsAny(q, listOf("delay", "late", "miss", "bus is delayed", "train is delayed", "disruption", "late?")) -> {
                lastIntentTopic = "delay"
                val firstTransit = transitLegs.firstOrNull()
                val secondTransit = transitLegs.getOrNull(1)
                val text = buildString {
                    if (current.activeDelayMinutes > 0) {
                        append("Current SIMULATED alert: ${current.delayNotice ?: "+${current.activeDelayMinutes}m delay"} on this corridor.\n\n")
                    } else {
                        append("No active delays are flagged right now [SIMULATED status].\n\n")
                    }
                    if (firstTransit != null && secondTransit != null && secondTransit.fromStopId != null && secondTransit.routeId != null) {
                        val bufferMin = (secondTransit.departureSeconds - firstTransit.arrivalSeconds) / 60
                        val simulatedMissTime = secondTransit.departureSeconds + 15 * 60
                        val nextConn = routingEngine.findNextScheduledDeparture(
                            stopId = secondTransit.fromStopId,
                            routeId = secondTransit.routeId,
                            afterSeconds = simulatedMissTime
                        )
                        append("If ${firstTransit.routeShortName} is delayed beyond your ~${bufferMin.coerceAtLeast(1)} min transfer buffer at ${secondTransit.fromName}, ")
                        if (nextConn != null) {
                            append("the next scheduled ${nextConn.routeShortName} departs at ${TimeFormatters.formatClock(nextConn.departureSeconds)} [SCHEDULED].")
                        } else {
                            append("check the next morning's timetable as no later scheduled connection runs on ${secondTransit.routeShortName} today.")
                        }
                    } else if (firstTransit != null) {
                        append("Since this is a direct ${firstTransit.routeShortName} ride with no connecting transfer, a delay shifts your final arrival time directly without risking a missed interchange.")
                    }
                }
                text to false
            }

            containsAny(q, listOf("crowd", "busy", "rush", "packed", "congestion")) -> {
                lastIntentTopic = "crowd"
                val stopIds = current.segments.flatMap { listOfNotNull(it.fromStopId, it.toStopId) }.toSet()
                val matched = crowdStatuses.filter { it.stopId in stopIds }
                val text = if (matched.isEmpty()) {
                    "No station congestion alerts are recorded along this route [SIMULATED]."
                } else {
                    "Station crowd levels along your journey [SIMULATED]:\n" + matched.joinToString("\n") {
                        "• ${it.stopName}: ${it.crowdLevel.label} (+${it.estimatedWaitExtraMinutes}m platform buffer)"
                    }
                }
                text to false
            }

            containsAny(q, listOf("save", "bookmark", "favorite", "keep")) -> {
                lastIntentTopic = "save"
                "Yes! You can save this ${current.origin.name} → ${current.destination.name} journey right now so it stays available offline in your Saved tab. Tap the button below to save it." to true
            }

            containsAny(q, listOf("depart", "arrive", "time", "schedule", "when", "long", "duration")) -> {
                lastIntentTopic = "timing"
                val text = "Departure: ${TimeFormatters.formatClock(current.departureSeconds)}\n" +
                    "Arrival: ${TimeFormatters.formatClock(current.arrivalSeconds)}\n" +
                    "Total Duration: ${TimeFormatters.formatDuration(current.totalDurationMinutes)} (${current.scheduleProvenance.badgeText})\n\n" +
                    current.segments.joinToString("\n") { seg ->
                        "• ${TimeFormatters.formatClock(seg.departureSeconds)} ${seg.routeShortName}: ${seg.fromName} → ${seg.toName} (${seg.durationMinutes}m)"
                    }
                text to false
            }

            containsAny(q, listOf("more", "explain", "details", "next", "what about")) && lastIntentTopic != null -> {
                val followUpText = when (lastIntentTopic) {
                    "transfers" -> "Tip: Keep a smart card or NCMC card ready at interchanges to reduce gate queue time by 2–4 minutes."
                    "fare" -> "Fares shown are estimated from standard DMRC, DTC, Indian Railways, and NCRTC distance slabs (ESTIMATED)."
                    "delay" -> "You can test delay scenarios anytime from the Alert Simulator on the Home or Explore tab to see live schedule recalculations."
                    else -> "Your active journey uses ${current.modesUsed.joinToString(", ") { it.shortLabel }} for ${TimeFormatters.formatDuration(current.totalDurationMinutes)} and ₹${current.totalFareInr}."
                }
                followUpText to false
            }

            else -> {
                val summary = "Here's a quick overview of your active ${current.badgeType.label} route (${current.origin.name} → ${current.destination.name}):\n" +
                    "• Duration: ${TimeFormatters.formatDuration(current.totalDurationMinutes)} (${TimeFormatters.formatClock(current.departureSeconds)} – ${TimeFormatters.formatClock(current.arrivalSeconds)})\n" +
                    "• Estimated Fare: ₹${current.totalFareInr} · Transfers: ${current.transferCount} · Walk: ${TimeFormatters.formatDistance(current.totalWalkingMeters)}\n" +
                    "• Modes: ${current.modesUsed.joinToString(" → ") { it.shortLabel }}\n\n" +
                    "Ask me which route is cheapest, where to change stations, how much walking is involved, or what happens if a bus/train is delayed."
                summary to false
            }
        }

        return ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = response.first,
            timestampLabel = currentTimeLabel(),
            canSaveActiveRoute = response.second,
            suggestedFollowUps = defaultSuggestions(hasRoute = true)
        )
    }

    private fun handleNoActiveRouteQuery(
        q: String,
        alerts: List<TransitAlert>,
        crowdStatuses: List<StationCrowdStatus>
    ): ChatMessage {
        val text = when {
            containsAny(q, listOf("delay", "alert", "status")) -> {
                "Active Delhi NCR transit alerts [SIMULATED]:\n" + alerts.joinToString("\n") {
                    "• ${it.title}: ${it.status.label}" + if (it.delayMinutes > 0) " (+${it.delayMinutes}m)" else ""
                }
            }
            containsAny(q, listOf("crowd", "busy", "station")) -> {
                "Major interchange crowd status [SIMULATED]:\n" + crowdStatuses.take(4).joinToString("\n") {
                    "• ${it.stopName}: ${it.crowdLevel.label}"
                }
            }
            else -> {
                "Search for a journey on the Home screen first (for example, Mathura Junction → Connaught Place, New Delhi) and I'll compare fastest vs cheapest options, transfer stations, walking legs, and delay backups for your exact itinerary!"
            }
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = text,
            timestampLabel = currentTimeLabel(),
            suggestedFollowUps = listOf("Any active delays?", "Which stations are crowded?", "How does routing work?")
        )
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun defaultSuggestions(hasRoute: Boolean): List<String> = if (hasRoute) {
        listOf(
            "Which route is the cheapest?",
            "How many transfers are required?",
            "What happens if my bus is delayed?",
            "How much walking is involved?",
            "Can I save this journey?"
        )
    } else {
        listOf("Any active delays?", "Which stations are crowded?")
    }

    private fun currentTimeLabel(): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
}
