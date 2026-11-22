package com.example.domain.routing

import com.example.domain.models.DataProvenance
import com.example.domain.models.GeoPoint
import com.example.domain.models.GtfsDataset
import com.example.domain.models.JourneyRoute
import com.example.domain.models.JourneySegment
import com.example.domain.models.PlaceLocation
import com.example.domain.models.RouteBadgeType
import com.example.domain.models.RoutingOptions
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitStop
import com.example.domain.models.UpcomingDeparture
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ScheduledHopEdge(
    val tripInstanceId: String,
    val templateTripId: String,
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val headsign: String,
    val mode: TransitMode,
    val colorHex: String,
    val fromStopId: String,
    val toStopId: String,
    val departureSeconds: Int,
    val arrivalSeconds: Int,
    val distanceMeters: Int,
    val incrementalFareInr: Int
)

data class WalkTransferEdge(
    val fromStopId: String,
    val toStopId: String,
    val distanceMeters: Int,
    val walkSeconds: Int
)

class TransitGraph(val dataset: GtfsDataset) {
    val stops: Map<String, TransitStop> = dataset.stops
    val transitEdgesByStop: Map<String, List<ScheduledHopEdge>>
    val walkTransfersByStop: Map<String, List<WalkTransferEdge>>

    init {
        val builtTransit = mutableMapOf<String, MutableList<ScheduledHopEdge>>()
        for ((tripId, trip) in dataset.trips) {
            val route = dataset.routes[trip.routeId] ?: continue
            val stopTimes = dataset.stopTimesByTrip[tripId].orEmpty()
            if (stopTimes.size < 2) continue

            val baseStart = stopTimes.first().departureSeconds
            val freqs = dataset.frequencies[tripId].orEmpty()
            val offsets = if (freqs.isEmpty()) {
                listOf(0 to tripId)
            } else {
                buildList {
                    freqs.forEachIndexed { freqIdx, freq ->
                        var dep = freq.startSeconds
                        var instanceNum = 0
                        while (dep <= freq.endSeconds) {
                            add((dep - baseStart) to "${tripId}_F${freqIdx}_$instanceNum")
                            dep += freq.headwaySeconds
                            instanceNum++
                        }
                    }
                }
            }

            for ((offsetSecs, instanceId) in offsets) {
                for (i in 0 until stopTimes.lastIndex) {
                    val fromSt = stopTimes[i]
                    val toSt = stopTimes[i + 1]
                    val fromStop = stops[fromSt.stopId] ?: continue
                    val toStop = stops[toSt.stopId] ?: continue
                    val depSec = fromSt.departureSeconds + offsetSecs
                    val arrSec = toSt.arrivalSeconds + offsetSecs
                    if (arrSec <= depSec) continue

                    val distMeters = haversineMeters(fromStop.lat, fromStop.lon, toStop.lat, toStop.lon)
                    val hopFare = estimateIncrementalHopFare(route.mode, route.routeId, distMeters)
                    builtTransit.getOrPut(fromStop.stopId) { mutableListOf() }.add(
                        ScheduledHopEdge(
                            tripInstanceId = instanceId,
                            templateTripId = tripId,
                            routeId = route.routeId,
                            routeShortName = route.shortName,
                            routeLongName = route.longName,
                            headsign = trip.headsign,
                            mode = route.mode,
                            colorHex = route.colorHex,
                            fromStopId = fromStop.stopId,
                            toStopId = toStop.stopId,
                            departureSeconds = depSec,
                            arrivalSeconds = arrSec,
                            distanceMeters = distMeters,
                            incrementalFareInr = hopFare
                        )
                    )
                }
            }
        }
        transitEdgesByStop = builtTransit.mapValues { (_, list) ->
            list.sortedBy { it.departureSeconds }
        }

        val builtWalks = mutableMapOf<String, MutableList<WalkTransferEdge>>()
        val stopList = stops.values.toList()
        for (i in stopList.indices) {
            for (j in i + 1 until stopList.size) {
                val a = stopList[i]
                val b = stopList[j]
                val dist = haversineMeters(a.lat, a.lon, b.lat, b.lon)
                if (dist <= 2200) {
                    val walkSecs = ((dist / WALK_SPEED_MPS) + 60).roundToInt()
                    builtWalks.getOrPut(a.stopId) { mutableListOf() }.add(
                        WalkTransferEdge(a.stopId, b.stopId, dist, walkSecs)
                    )
                    builtWalks.getOrPut(b.stopId) { mutableListOf() }.add(
                        WalkTransferEdge(b.stopId, a.stopId, dist, walkSecs)
                    )
                }
            }
        }
        walkTransfersByStop = builtWalks
    }

    companion object {
        const val WALK_SPEED_MPS = 1.25

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return (2 * r * asin(sqrt(a))).roundToInt()
        }

        fun estimateIncrementalHopFare(mode: TransitMode, routeId: String, distMeters: Int): Int {
            val km = distMeters / 1000.0
            return when (mode) {
                TransitMode.METRO -> {
                    if (routeId.contains("AIRPORT", ignoreCase = true)) {
                        (km * 3.5).roundToInt().coerceIn(20, 60)
                    } else {
                        (km * 2.2).roundToInt().coerceIn(5, 15)
                    }
                }
                TransitMode.BUS -> (km * 0.9).roundToInt().coerceIn(3, 10)
                TransitMode.RAILWAY -> {
                    if (routeId.contains("MEMU", ignoreCase = true) || routeId.contains("EMU", ignoreCase = true)) {
                        (km * 0.28).roundToInt().coerceIn(5, 15)
                    } else {
                        (km * 0.72).roundToInt().coerceIn(15, 65)
                    }
                }
                TransitMode.RRTS -> (km * 2.4).roundToInt().coerceIn(15, 40)
                TransitMode.WALK -> 0
            }
        }

        fun boardingBaseFare(mode: TransitMode, routeId: String): Int = when (mode) {
            TransitMode.METRO -> if (routeId.contains("AIRPORT", ignoreCase = true)) 20 else 10
            TransitMode.BUS -> 5
            TransitMode.RAILWAY -> if (routeId.contains("MEMU", ignoreCase = true) || routeId.contains("EMU", ignoreCase = true)) 10 else 35
            TransitMode.RRTS -> 20
            TransitMode.WALK -> 0
        }
    }
}

class MultimodalRoutingEngine(private val graph: TransitGraph) {

    private enum class OptimizationObjective {
        FASTEST, CHEAPEST, BALANCED
    }

    private data class SearchStep(
        val prevStateKey: String?,
        val transitEdge: ScheduledHopEdge?,
        val walkToStopId: String?,
        val walkMeters: Int,
        val walkSeconds: Int,
        val arrivalSeconds: Int,
        val waitSeconds: Int,
        val addedFareInr: Int
    )

    private data class QueueNode(
        val stopId: String,
        val activeTripInstanceId: String?,
        val activeRouteId: String?,
        val arrivalSeconds: Int,
        val totalFareInr: Int,
        val transfers: Int,
        val totalWalkMeters: Int,
        val score: Double,
        val stateKey: String
    ) : Comparable<QueueNode> {
        override fun compareTo(other: QueueNode): Int = score.compareTo(other.score)
    }

    fun findMultimodalRoutes(
        origin: PlaceLocation,
        destination: PlaceLocation,
        options: RoutingOptions,
        activeAlerts: List<TransitAlert> = emptyList()
    ): List<JourneyRoute> {
        val delayByRoute = activeAlerts.mapNotNull { alert ->
            alert.affectedRouteId?.let { it to alert.delayMinutes }
        }.toMap()
        val alertByRoute = activeAlerts.mapNotNull { alert ->
            alert.affectedRouteId?.let { it to alert }
        }.toMap()

        val fastest = runSearch(origin, destination, options, OptimizationObjective.FASTEST, delayByRoute, alertByRoute)
        val cheapest = runSearch(origin, destination, options, OptimizationObjective.CHEAPEST, delayByRoute, alertByRoute)
        val balanced = runSearch(origin, destination, options, OptimizationObjective.BALANCED, delayByRoute, alertByRoute)

        val candidates = mutableListOf<JourneyRoute>()
        fastest?.let { candidates.add(it.copy(badgeType = RouteBadgeType.FASTEST)) }
        cheapest?.let { route ->
            if (candidates.none { sameItinerarySignature(it, route) }) {
                candidates.add(route.copy(badgeType = RouteBadgeType.CHEAPEST))
            }
        }
        balanced?.let { route ->
            if (candidates.none { sameItinerarySignature(it, route) }) {
                candidates.add(route.copy(badgeType = RouteBadgeType.BALANCED))
            }
        }

        // If cheapest or balanced matched fastest, try finding a genuine alternative avoiding primary mode/route
        if (candidates.size < 3 && fastest != null) {
            val primaryRoutes = fastest.segments.mapNotNull { it.routeId }.toSet()
            val altCheapest = runSearch(
                origin, destination, options, OptimizationObjective.CHEAPEST,
                delayByRoute, alertByRoute, penalizedRouteIds = primaryRoutes
            )
            if (altCheapest != null && candidates.none { sameItinerarySignature(it, altCheapest) }) {
                candidates.add(altCheapest.copy(badgeType = RouteBadgeType.CHEAPEST))
            }
        }
        if (candidates.size < 3 && candidates.isNotEmpty()) {
            val usedRoutes = candidates.flatMap { r -> r.segments.mapNotNull { it.routeId } }.toSet()
            val altBalanced = runSearch(
                origin, destination, options, OptimizationObjective.BALANCED,
                delayByRoute, alertByRoute, penalizedRouteIds = usedRoutes
            )
            if (altBalanced != null && candidates.none { sameItinerarySignature(it, altBalanced) }) {
                candidates.add(altBalanced.copy(badgeType = RouteBadgeType.BALANCED))
            }
        }

        // Re-assign badges truthfully based on actual computed values
        if (candidates.isEmpty()) return emptyList()
        val minDuration = candidates.minOf { it.totalDurationMinutes }
        val minFare = candidates.minOf { it.totalFareInr }
        val assigned = mutableListOf<JourneyRoute>()
        val usedBadges = mutableSetOf<RouteBadgeType>()

        val fastestPick = candidates.minBy { it.totalDurationMinutes }
        assigned.add(fastestPick.copy(badgeType = RouteBadgeType.FASTEST))
        usedBadges.add(RouteBadgeType.FASTEST)

        val cheapestPick = candidates.filter { it.id != fastestPick.id }.minByOrNull { it.totalFareInr }
        if (cheapestPick != null) {
            val badge = if (cheapestPick.totalFareInr <= minFare || RouteBadgeType.CHEAPEST !in usedBadges) {
                RouteBadgeType.CHEAPEST
            } else {
                RouteBadgeType.BALANCED
            }
            assigned.add(cheapestPick.copy(badgeType = badge))
            usedBadges.add(badge)
        }

        val remaining = candidates.filter { c -> assigned.none { it.id == c.id } }
        for (extra in remaining) {
            val badge = when {
                RouteBadgeType.BALANCED !in usedBadges -> RouteBadgeType.BALANCED
                RouteBadgeType.CHEAPEST !in usedBadges -> RouteBadgeType.CHEAPEST
                else -> RouteBadgeType.BALANCED
            }
            assigned.add(extra.copy(badgeType = badge))
            usedBadges.add(badge)
        }

        return assigned.sortedBy {
            when (it.badgeType) {
                RouteBadgeType.FASTEST -> 0
                RouteBadgeType.CHEAPEST -> 1
                RouteBadgeType.BALANCED -> 2
            }
        }
    }

    private fun sameItinerarySignature(a: JourneyRoute, b: JourneyRoute): Boolean {
        val sigA = a.segments.filter { it.mode != TransitMode.WALK }.joinToString("|") {
            "${it.routeId}:${it.fromStopId}->${it.toStopId}"
        }
        val sigB = b.segments.filter { it.mode != TransitMode.WALK }.joinToString("|") {
            "${it.routeId}:${it.fromStopId}->${it.toStopId}"
        }
        return sigA == sigB
    }

    private fun runSearch(
        origin: PlaceLocation,
        destination: PlaceLocation,
        options: RoutingOptions,
        objective: OptimizationObjective,
        delayByRoute: Map<String, Int>,
        alertByRoute: Map<String, TransitAlert>,
        penalizedRouteIds: Set<String> = emptySet()
    ): JourneyRoute? {
        val accessStops = findNearbyStops(origin.lat, origin.lon, origin.stopId, options.maxWalkMeters.coerceAtLeast(1800))
        val egressStops = findNearbyStops(destination.lat, destination.lon, destination.stopId, options.maxWalkMeters.coerceAtLeast(1800))
        if (accessStops.isEmpty() || egressStops.isEmpty()) return null

        val egressMap = egressStops.associateBy { it.first.stopId }
        val pq = PriorityQueue<QueueNode>()
        val bestScoreByState = mutableMapOf<String, Double>()
        val parentStep = mutableMapOf<String, SearchStep>()

        val originNodeKey = "ORIGIN"
        for ((stop, walkDist) in accessStops) {
            val walkSecs = if (walkDist <= 60) 60 else (walkDist / TransitGraph.WALK_SPEED_MPS).roundToInt()
            val arrAtStop = options.departureSeconds + walkSecs
            val stateKey = "${stop.stopId}|NONE|0"
            val initScore = computeScore(
                objective = objective,
                elapsedSeconds = walkSecs,
                fareInr = 0,
                transfers = 0,
                walkMeters = walkDist
            )
            if (initScore < (bestScoreByState[stateKey] ?: Double.POSITIVE_INFINITY)) {
                bestScoreByState[stateKey] = initScore
                parentStep[stateKey] = SearchStep(
                    prevStateKey = originNodeKey,
                    transitEdge = null,
                    walkToStopId = stop.stopId,
                    walkMeters = walkDist,
                    walkSeconds = walkSecs,
                    arrivalSeconds = arrAtStop,
                    waitSeconds = 0,
                    addedFareInr = 0
                )
                pq.add(
                    QueueNode(
                        stopId = stop.stopId,
                        activeTripInstanceId = null,
                        activeRouteId = null,
                        arrivalSeconds = arrAtStop,
                        totalFareInr = 0,
                        transfers = 0,
                        totalWalkMeters = walkDist,
                        score = initScore,
                        stateKey = stateKey
                    )
                )
            }
        }

        var bestGoalStateKey: String? = null
        var bestGoalScore = Double.POSITIVE_INFINITY
        var bestGoalEgressStop: Pair<TransitStop, Int>? = null
        var bestGoalArrival = Int.MAX_VALUE
        var bestGoalFare = Int.MAX_VALUE

        val maxArrivalAllowed = options.departureSeconds + 7 * 3600
        var expanded = 0

        while (pq.isNotEmpty() && expanded < 14000) {
            val curr = pq.poll() ?: break
            if (curr.score > (bestScoreByState[curr.stateKey] ?: Double.POSITIVE_INFINITY) + 1e-6) continue
            if (curr.score >= bestGoalScore) break
            expanded++

            // Check if current stop can walk directly to destination
            val egressEntry = egressMap[curr.stopId]
            if (egressEntry != null && curr.activeTripInstanceId != null) {
                val (_, egressDist) = egressEntry
                val egressWalkSecs = if (egressDist <= 60) 60 else (egressDist / TransitGraph.WALK_SPEED_MPS).roundToInt()
                val finalArrival = curr.arrivalSeconds + egressWalkSecs
                val goalScore = computeScore(
                    objective = objective,
                    elapsedSeconds = finalArrival - options.departureSeconds,
                    fareInr = curr.totalFareInr,
                    transfers = curr.transfers,
                    walkMeters = curr.totalWalkMeters + egressDist
                )
                if (goalScore < bestGoalScore) {
                    bestGoalScore = goalScore
                    bestGoalStateKey = curr.stateKey
                    bestGoalEgressStop = egressEntry
                    bestGoalArrival = finalArrival
                    bestGoalFare = curr.totalFareInr
                }
            }

            // 1. Explore scheduled transit edges from curr.stopId
            val outgoing = graph.transitEdgesByStop[curr.stopId].orEmpty()
            // Group by route+toStop so we only inspect the earliest valid departure per route for speed
            val seenRouteNext = mutableSetOf<String>()

            for (edge in outgoing) {
                if (edge.mode !in options.allowedModes) continue
                val delaySecs = (delayByRoute[edge.routeId] ?: 0) * 60
                val effectiveDep = edge.departureSeconds + delaySecs
                val effectiveArr = edge.arrivalSeconds + delaySecs

                val continuingSameTrip = curr.activeTripInstanceId == edge.tripInstanceId
                val minBoardTime = if (continuingSameTrip) curr.arrivalSeconds else curr.arrivalSeconds + 90
                if (effectiveDep < minBoardTime) continue
                if (effectiveArr > maxArrivalAllowed) continue

                val routeToKey = "${edge.routeId}|${edge.toStopId}"
                if (!continuingSameTrip && !seenRouteNext.add(routeToKey)) continue

                val isBoardingNewTrip = !continuingSameTrip
                val newTransfers = if (curr.activeTripInstanceId != null && isBoardingNewTrip) curr.transfers + 1 else curr.transfers
                if (newTransfers > options.maxTransfers) continue

                val addedFare = if (isBoardingNewTrip) {
                    TransitGraph.boardingBaseFare(edge.mode, edge.routeId) + edge.incrementalFareInr
                } else {
                    edge.incrementalFareInr
                }
                val nextFare = curr.totalFareInr + addedFare
                val penalty = if (edge.routeId in penalizedRouteIds) {
                    when (objective) {
                        OptimizationObjective.FASTEST -> 1800.0
                        OptimizationObjective.CHEAPEST -> 60.0
                        OptimizationObjective.BALANCED -> 38.0
                    }
                } else 0.0

                val nextScore = computeScore(
                    objective = objective,
                    elapsedSeconds = effectiveArr - options.departureSeconds,
                    fareInr = nextFare,
                    transfers = newTransfers,
                    walkMeters = curr.totalWalkMeters
                ) + penalty

                val nextStateKey = "${edge.toStopId}|${edge.routeId}|$newTransfers"
                if (nextScore < (bestScoreByState[nextStateKey] ?: Double.POSITIVE_INFINITY)) {
                    bestScoreByState[nextStateKey] = nextScore
                    parentStep[nextStateKey] = SearchStep(
                        prevStateKey = curr.stateKey,
                        transitEdge = edge.copy(
                            departureSeconds = effectiveDep,
                            arrivalSeconds = effectiveArr
                        ),
                        walkToStopId = null,
                        walkMeters = 0,
                        walkSeconds = 0,
                        arrivalSeconds = effectiveArr,
                        waitSeconds = (effectiveDep - curr.arrivalSeconds).coerceAtLeast(0),
                        addedFareInr = addedFare
                    )
                    pq.add(
                        QueueNode(
                            stopId = edge.toStopId,
                            activeTripInstanceId = edge.tripInstanceId,
                            activeRouteId = edge.routeId,
                            arrivalSeconds = effectiveArr,
                            totalFareInr = nextFare,
                            transfers = newTransfers,
                            totalWalkMeters = curr.totalWalkMeters,
                            score = nextScore,
                            stateKey = nextStateKey
                        )
                    )
                }
            }

            // 2. Explore walking transfers to nearby stops (only if we arrived via transit, avoid double walks)
            if (curr.activeTripInstanceId != null && curr.transfers < options.maxTransfers) {
                val walks = graph.walkTransfersByStop[curr.stopId].orEmpty()
                for (walk in walks) {
                    if (walk.distanceMeters > options.maxWalkMeters) continue
                    val nextArrival = curr.arrivalSeconds + walk.walkSeconds
                    if (nextArrival > maxArrivalAllowed) continue
                    val nextWalkTotal = curr.totalWalkMeters + walk.distanceMeters
                    val nextStateKey = "${walk.toStopId}|WALK|${curr.transfers}"
                    val nextScore = computeScore(
                        objective = objective,
                        elapsedSeconds = nextArrival - options.departureSeconds,
                        fareInr = curr.totalFareInr,
                        transfers = curr.transfers,
                        walkMeters = nextWalkTotal
                    )
                    if (nextScore < (bestScoreByState[nextStateKey] ?: Double.POSITIVE_INFINITY)) {
                        bestScoreByState[nextStateKey] = nextScore
                        parentStep[nextStateKey] = SearchStep(
                            prevStateKey = curr.stateKey,
                            transitEdge = null,
                            walkToStopId = walk.toStopId,
                            walkMeters = walk.distanceMeters,
                            walkSeconds = walk.walkSeconds,
                            arrivalSeconds = nextArrival,
                            waitSeconds = 0,
                            addedFareInr = 0
                        )
                        pq.add(
                            QueueNode(
                                stopId = walk.toStopId,
                                activeTripInstanceId = null,
                                activeRouteId = null,
                                arrivalSeconds = nextArrival,
                                totalFareInr = curr.totalFareInr,
                                transfers = curr.transfers,
                                totalWalkMeters = nextWalkTotal,
                                score = nextScore,
                                stateKey = nextStateKey
                            )
                        )
                    }
                }
            }
        }

        val goalKey = bestGoalStateKey ?: return null
        val egressStopPair = bestGoalEgressStop ?: return null

        // Reconstruct path steps
        val steps = mutableListOf<SearchStep>()
        var cursor: String? = goalKey
        var guard = 0
        while (cursor != null && cursor != originNodeKey && guard < 100) {
            val step = parentStep[cursor] ?: break
            steps.add(step)
            cursor = step.prevStateKey
            guard++
        }
        steps.reverse()
        if (steps.isEmpty()) return null

        return buildJourneyRoute(
            origin = origin,
            destination = destination,
            options = options,
            steps = steps,
            egressStop = egressStopPair.first,
            egressWalkMeters = egressStopPair.second,
            alertByRoute = alertByRoute,
            badgeType = when (objective) {
                OptimizationObjective.FASTEST -> RouteBadgeType.FASTEST
                OptimizationObjective.CHEAPEST -> RouteBadgeType.CHEAPEST
                OptimizationObjective.BALANCED -> RouteBadgeType.BALANCED
            }
        )
    }

    private fun buildJourneyRoute(
        origin: PlaceLocation,
        destination: PlaceLocation,
        options: RoutingOptions,
        steps: List<SearchStep>,
        egressStop: TransitStop,
        egressWalkMeters: Int,
        alertByRoute: Map<String, TransitAlert>,
        badgeType: RouteBadgeType
    ): JourneyRoute {
        val segments = mutableListOf<JourneySegment>()
        var totalWalkMeters = 0

        // Group consecutive transit hops on the same tripInstanceId into a single clean JourneySegment
        var idx = 0
        while (idx < steps.size) {
            val step = steps[idx]
            if (step.transitEdge == null) {
                val targetStop = graph.stops[step.walkToStopId]
                if (targetStop != null) {
                    val isFirst = idx == 0
                    val fromName = if (isFirst) origin.name else segments.lastOrNull()?.toName ?: origin.name
                    val fromLat = if (isFirst) origin.lat else segments.lastOrNull()?.toLat ?: origin.lat
                    val fromLon = if (isFirst) origin.lon else segments.lastOrNull()?.toLon ?: origin.lon
                    val depSec = step.arrivalSeconds - step.walkSeconds
                    totalWalkMeters += step.walkMeters
                    if (step.walkMeters >= 30 || isFirst) {
                        segments.add(
                            JourneySegment(
                                id = UUID.randomUUID().toString(),
                                mode = TransitMode.WALK,
                                routeId = null,
                                routeShortName = "Walk",
                                routeLongName = "Pedestrian transfer",
                                headsign = "Walk to ${targetStop.name}",
                                colorHex = "94A3B8",
                                fromStopId = if (isFirst) origin.stopId else segments.lastOrNull()?.toStopId,
                                fromName = fromName,
                                fromLat = fromLat,
                                fromLon = fromLon,
                                toStopId = targetStop.stopId,
                                toName = targetStop.name,
                                toLat = targetStop.lat,
                                toLon = targetStop.lon,
                                departureSeconds = depSec,
                                arrivalSeconds = step.arrivalSeconds,
                                waitSeconds = 0,
                                distanceMeters = step.walkMeters,
                                fareInr = 0,
                                stopCount = 0,
                                intermediateStops = emptyList(),
                                pathCoordinates = listOf(GeoPoint(fromLat, fromLon), GeoPoint(targetStop.lat, targetStop.lon)),
                                instruction = if (isFirst) {
                                    "Walk ${step.walkMeters}m to ${targetStop.name}"
                                } else {
                                    "Interchange walk (${step.walkMeters}m) to ${targetStop.name}"
                                }
                            )
                        )
                    }
                }
                idx++
            } else {
                val firstEdge = step.transitEdge
                val waitSecs = step.waitSeconds
                var segmentFare = step.addedFareInr
                var segmentDist = firstEdge.distanceMeters
                val coords = mutableListOf<GeoPoint>()
                val intermediateNames = mutableListOf<String>()
                val startStop = graph.stops[firstEdge.fromStopId]!!
                coords.add(GeoPoint(startStop.lat, startStop.lon))

                var lastEdge = firstEdge
                var hopCount = 1
                idx++
                while (idx < steps.size && steps[idx].transitEdge?.tripInstanceId == firstEdge.tripInstanceId) {
                    val nextEdge = steps[idx].transitEdge!!
                    val midStop = graph.stops[nextEdge.fromStopId]
                    if (midStop != null) {
                        intermediateNames.add(midStop.name)
                        coords.add(GeoPoint(midStop.lat, midStop.lon))
                    }
                    segmentFare += steps[idx].addedFareInr
                    segmentDist += nextEdge.distanceMeters
                    lastEdge = nextEdge
                    hopCount++
                    idx++
                }
                val endStop = graph.stops[lastEdge.toStopId]!!
                coords.add(GeoPoint(endStop.lat, endStop.lon))

                val routeAlert = alertByRoute[firstEdge.routeId]
                val delayMin = routeAlert?.delayMinutes ?: 0
                segments.add(
                    JourneySegment(
                        id = UUID.randomUUID().toString(),
                        mode = firstEdge.mode,
                        routeId = firstEdge.routeId,
                        routeShortName = firstEdge.routeShortName,
                        routeLongName = firstEdge.routeLongName,
                        headsign = firstEdge.headsign,
                        colorHex = firstEdge.colorHex,
                        fromStopId = startStop.stopId,
                        fromName = startStop.name,
                        fromLat = startStop.lat,
                        fromLon = startStop.lon,
                        toStopId = endStop.stopId,
                        toName = endStop.name,
                        toLat = endStop.lat,
                        toLon = endStop.lon,
                        departureSeconds = firstEdge.departureSeconds,
                        arrivalSeconds = lastEdge.arrivalSeconds,
                        waitSeconds = waitSecs,
                        distanceMeters = segmentDist,
                        fareInr = segmentFare,
                        stopCount = hopCount,
                        intermediateStops = intermediateNames,
                        pathCoordinates = coords,
                        delayMinutes = delayMin,
                        delayProvenance = routeAlert?.provenance ?: graph.dataset.provenance,
                        instruction = "Board ${firstEdge.routeShortName} towards ${firstEdge.headsign} from ${startStop.name} to ${endStop.name}"
                    )
                )
            }
        }

        // Append final egress walk to destination
        val egressWalkSecs = if (egressWalkMeters <= 60) 60 else (egressWalkMeters / TransitGraph.WALK_SPEED_MPS).roundToInt()
        val lastArrival = segments.lastOrNull()?.arrivalSeconds ?: options.departureSeconds
        totalWalkMeters += egressWalkMeters
        if (egressWalkMeters >= 30 || egressStop.name != destination.name) {
            segments.add(
                JourneySegment(
                    id = UUID.randomUUID().toString(),
                    mode = TransitMode.WALK,
                    routeId = null,
                    routeShortName = "Walk",
                    routeLongName = "Final walk to destination",
                    headsign = destination.name,
                    colorHex = "94A3B8",
                    fromStopId = egressStop.stopId,
                    fromName = egressStop.name,
                    fromLat = egressStop.lat,
                    fromLon = egressStop.lon,
                    toStopId = destination.stopId,
                    toName = destination.name,
                    toLat = destination.lat,
                    toLon = destination.lon,
                    departureSeconds = lastArrival,
                    arrivalSeconds = lastArrival + egressWalkSecs,
                    waitSeconds = 0,
                    distanceMeters = egressWalkMeters,
                    fareInr = 0,
                    stopCount = 0,
                    intermediateStops = emptyList(),
                    pathCoordinates = listOf(
                        GeoPoint(egressStop.lat, egressStop.lon),
                        GeoPoint(destination.lat, destination.lon)
                    ),
                    instruction = "Walk ${egressWalkMeters}m to ${destination.name}"
                )
            )
        }

        val transitSegments = segments.filter { it.mode != TransitMode.WALK }
        val totalFare = transitSegments.sumOf { it.fareInr }
        val transfers = (transitSegments.size - 1).coerceAtLeast(0)
        val actualDep = segments.firstOrNull()?.departureSeconds ?: options.departureSeconds
        val actualArr = segments.lastOrNull()?.arrivalSeconds ?: actualDep
        val totalMinutes = ((actualArr - actualDep) / 60).coerceAtLeast(1)
        val totalDelay = transitSegments.sumOf { it.delayMinutes }
        val delayNotice = transitSegments.firstOrNull { it.delayMinutes > 0 }?.let { seg ->
            "${seg.routeShortName}: +${seg.delayMinutes}m delay"
        }

        return JourneyRoute(
            id = UUID.randomUUID().toString(),
            badgeType = badgeType,
            origin = origin,
            destination = destination,
            departureSeconds = actualDep,
            arrivalSeconds = actualArr,
            totalDurationMinutes = totalMinutes,
            totalFareInr = totalFare,
            transferCount = transfers,
            totalWalkingMeters = totalWalkMeters,
            segments = segments,
            scheduleProvenance = graph.dataset.provenance,
            fareProvenance = DataProvenance.ESTIMATED,
            activeDelayMinutes = totalDelay,
            delayNotice = delayNotice,
            delayProvenance = DataProvenance.SIMULATED,
            modesUsed = segments.map { it.mode }.distinct()
        )
    }

    private fun computeScore(
        objective: OptimizationObjective,
        elapsedSeconds: Int,
        fareInr: Int,
        transfers: Int,
        walkMeters: Int
    ): Double {
        val elapsedMin = elapsedSeconds / 60.0
        val walkKm = walkMeters / 1000.0
        return when (objective) {
            OptimizationObjective.FASTEST -> elapsedSeconds.toDouble() + transfers * 180.0
            OptimizationObjective.CHEAPEST -> fareInr * 100.0 + elapsedMin * 0.45 + transfers * 12.0 + walkKm * 4.0
            OptimizationObjective.BALANCED -> elapsedMin * 1.0 + fareInr * 0.9 + transfers * 9.5 + walkKm * 7.0
        }
    }

    private fun findNearbyStops(
        lat: Double,
        lon: Double,
        exactStopId: String?,
        radiusMeters: Int
    ): List<Pair<TransitStop, Int>> {
        val exact = exactStopId?.let { graph.stops[it] }
        val nearby = graph.stops.values
            .map { stop -> stop to TransitGraph.haversineMeters(lat, lon, stop.lat, stop.lon) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .take(6)
            .toMutableList()

        if (exact != null && nearby.none { it.first.stopId == exact.stopId }) {
            nearby.add(0, exact to 0)
        }
        if (nearby.isEmpty()) {
            // Fall back to nearest 2 transit stops so users outside immediate station radius still get routed with clear walking distance
            return graph.stops.values
                .map { stop -> stop to TransitGraph.haversineMeters(lat, lon, stop.lat, stop.lon) }
                .sortedBy { it.second }
                .take(2)
        }
        return nearby
    }

    fun getUpcomingDepartures(
        stopId: String,
        currentSeconds: Int,
        activeAlerts: List<TransitAlert>,
        limit: Int = 8
    ): List<UpcomingDeparture> {
        val delayByRoute = activeAlerts.mapNotNull { a -> a.affectedRouteId?.let { it to a.delayMinutes } }.toMap()
        return graph.transitEdgesByStop[stopId].orEmpty()
            .filter { it.departureSeconds >= currentSeconds }
            .distinctBy { "${it.routeId}_${it.headsign}_${it.departureSeconds / 300}" }
            .take(limit)
            .map { edge ->
                val delay = delayByRoute[edge.routeId] ?: 0
                UpcomingDeparture(
                    routeShortName = edge.routeShortName,
                    headsign = edge.headsign,
                    mode = edge.mode,
                    colorHex = edge.colorHex,
                    departureSeconds = edge.departureSeconds + delay * 60,
                    minutesAway = (((edge.departureSeconds + delay * 60) - currentSeconds) / 60).coerceAtLeast(0),
                    delayMinutes = delay,
                    provenance = if (delay > 0) DataProvenance.SIMULATED else graph.dataset.provenance
                )
            }
    }

    fun findNextScheduledDeparture(
        stopId: String,
        routeId: String,
        afterSeconds: Int
    ): ScheduledHopEdge? {
        return graph.transitEdgesByStop[stopId].orEmpty()
            .firstOrNull { it.routeId == routeId && it.departureSeconds >= afterSeconds }
    }
}
