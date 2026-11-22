package com.example

import com.example.domain.awareness.TransitAwarenessEngine
import com.example.domain.chat.TransitChatEngine
import com.example.domain.gtfs.GtfsParser
import com.example.domain.models.DataProvenance
import com.example.domain.models.MapTileProvider
import com.example.domain.models.PlaceLocation
import com.example.domain.models.RouteBadgeType
import com.example.domain.models.RoutingOptions
import com.example.domain.models.TransitMode
import com.example.domain.routing.MultimodalRoutingEngine
import com.example.domain.routing.TransitGraph
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultimodalTransitEngineTest {

    private fun loadBundledGtfsDir(): File {
        val candidates = listOf(
            File("src/main/assets/gtfs_delhi_ncr"),
            File("app/src/main/assets/gtfs_delhi_ncr")
        )
        return candidates.first { it.exists() && it.isDirectory }
    }

    @Test
    fun `parses Delhi NCR GTFS assets and computes Mathura to Connaught Place multimodal routes`() {
        val dataset = GtfsParser().parseFromDirectory(
            directory = loadBundledGtfsDir(),
            sourceLabel = "Delhi NCR Seeded GTFS"
        )

        assertTrue("Expected GTFS stops to load (${dataset.stops.size})", dataset.stops.size >= 30)
        assertTrue("Expected GTFS routes to load (${dataset.routes.size})", dataset.routes.size >= 10)
        assertTrue("Expected GTFS trips to load (${dataset.trips.size})", dataset.trips.size >= 20)

        val graph = TransitGraph(dataset)
        val engine = MultimodalRoutingEngine(graph)
        val awareness = TransitAwarenessEngine()

        val mathura = PlaceLocation("mtj", "Mathura Junction", "Rail", 27.4765, 77.6744, "RAIL_MATHURA")
        val cp = PlaceLocation("cp", "Connaught Place, New Delhi", "Hub", 28.6315, 77.2167, "METRO_RAJIV_CHOWK")

        val routes = engine.findMultimodalRoutes(
            origin = mathura,
            destination = cp,
            options = RoutingOptions(departureSeconds = 8 * 3600 + 30 * 60),
            activeAlerts = awareness.alerts.value
        )

        assertTrue("Expected multiple multimodal route alternatives, found ${routes.size}", routes.size >= 2)
        val fastest = routes.first { it.badgeType == RouteBadgeType.FASTEST }
        val cheapest = routes.minBy { it.totalFareInr }

        assertTrue("Fastest route should include railway", TransitMode.RAILWAY in fastest.modesUsed)
        assertTrue("Fastest duration (${fastest.totalDurationMinutes}) must be <= cheapest (${cheapest.totalDurationMinutes})", fastest.totalDurationMinutes <= cheapest.totalDurationMinutes)
        assertTrue("Cheapest fare (${cheapest.totalFareInr}) must be <= fastest (${fastest.totalFareInr})", cheapest.totalFareInr <= fastest.totalFareInr)
        assertTrue("All routes must validate monotonic segment departure/arrival", routes.all { route ->
            route.segments.zipWithNext().all { (a, b) -> b.departureSeconds >= a.arrivalSeconds }
        })
    }

    @Test
    fun `validates operating hours and returns no routes during closed overnight window`() {
        val dataset = GtfsParser().parseFromDirectory(loadBundledGtfsDir(), "Delhi NCR Seeded GTFS")
        val engine = MultimodalRoutingEngine(TransitGraph(dataset))

        val hauzKhas = PlaceLocation("hk", "Hauz Khas", "Metro", 28.5433, 77.2066, "METRO_HAUZ_KHAS")
        val meerut = PlaceLocation("mrt", "Meerut South RRTS", "RRTS", 28.9150, 77.6410, "RRTS_MEERUT_SOUTH")

        val daytimeRoutes = engine.findMultimodalRoutes(hauzKhas, meerut, RoutingOptions(departureSeconds = 9 * 3600))
        assertTrue("Expected multimodal Metro+RRTS route during daytime", daytimeRoutes.isNotEmpty())
        assertTrue("Route to Meerut South should use RRTS", daytimeRoutes.any { TransitMode.RRTS in it.modesUsed })

        val closedWindowRoutes = engine.findMultimodalRoutes(hauzKhas, meerut, RoutingOptions(departureSeconds = 23 * 3600 + 50 * 60))
        assertTrue("Expected no feasible routes past closing hours", closedWindowRoutes.isEmpty())
    }

    @Test
    fun `itinerary aware chat assistant answers fare, transfer, delay, and save questions accurately`() {
        val dataset = GtfsParser().parseFromDirectory(loadBundledGtfsDir(), "Delhi NCR Seeded GTFS")
        val engine = MultimodalRoutingEngine(TransitGraph(dataset))
        val awareness = TransitAwarenessEngine()
        val chat = TransitChatEngine(engine)

        val mathura = PlaceLocation("mtj", "Mathura Junction", "Rail", 27.4765, 77.6744, "RAIL_MATHURA")
        val cp = PlaceLocation("cp", "Connaught Place, New Delhi", "Hub", 28.6315, 77.2167, "METRO_RAJIV_CHOWK")
        val routes = engine.findMultimodalRoutes(mathura, cp, RoutingOptions(departureSeconds = 9 * 3600), awareness.alerts.value)
        val active = routes.first()

        val cheapReply = chat.respond("Which route is the cheapest?", active, routes, awareness.alerts.value, awareness.crowdStatuses.value)
        assertTrue("Expected fare in rupees", cheapReply.text.contains("₹"))

        val transferReply = chat.respond("How many transfers are required?", active, routes, awareness.alerts.value, awareness.crowdStatuses.value)
        assertTrue("Expected transfer info", transferReply.text.contains("transfer", ignoreCase = true))

        val delayReply = chat.respond("What happens if my train is delayed?", active, routes, awareness.alerts.value, awareness.crowdStatuses.value)
        assertTrue("Expected provenance tag in delay reply", delayReply.text.contains("SIMULATED") || delayReply.text.contains("SCHEDULED"))

        val saveReply = chat.respond("Can I save this journey?", active, routes, awareness.alerts.value, awareness.crowdStatuses.value)
        assertTrue("Chat should enable save action button", saveReply.canSaveActiveRoute)
    }

    @Test
    fun `awareness simulator strictly preserves SIMULATED provenance and never claims LIVE`() {
        val awareness = TransitAwarenessEngine()
        awareness.simulateBusDelay(15)
        awareness.simulateTrainDelay(20)
        awareness.cycleMetroCrowd()

        assertTrue(awareness.alerts.value.all { it.provenance == DataProvenance.SIMULATED })
        assertTrue(awareness.crowdStatuses.value.all { it.provenance == DataProvenance.SIMULATED })
        assertFalse(awareness.alerts.value.any { it.provenance == DataProvenance.LIVE })
        assertTrue(MapTileProvider.entries.all { it.attribution.contains("OpenStreetMap") })
        assertTrue(MapTileProvider.entries.all { provider ->
            provider.tileUrls.none { it.contains("cartocdn", ignoreCase = true) } &&
                (provider.styleUri == null || !provider.styleUri.contains("cartocdn", ignoreCase = true)) &&
                (provider.styleUri != null || provider.tileUrls.isNotEmpty())
        })
        assertTrue(MapTileProvider.fromId("carto_dark") == MapTileProvider.OPENFREEMAP_POSITRON)
        assertTrue(MapTileProvider.fromId("carto_voyager") == MapTileProvider.OPENFREEMAP_POSITRON)
    }
}
