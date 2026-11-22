package com.example.domain.gtfs

import android.content.Context
import com.example.domain.models.DataProvenance
import com.example.domain.models.GtfsCalendar
import com.example.domain.models.GtfsCalendarDate
import com.example.domain.models.GtfsDataset
import com.example.domain.models.GtfsFrequency
import com.example.domain.models.GtfsStopTime
import com.example.domain.models.GtfsTrip
import com.example.domain.models.TransitAgency
import com.example.domain.models.TransitMode
import com.example.domain.models.TransitRouteInfo
import com.example.domain.models.TransitStop
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class GtfsParser {

    fun parseFromAssets(
        context: Context,
        assetDir: String = "gtfs_delhi_ncr",
        sourceLabel: String = "Delhi NCR Seeded GTFS (DMRC + DTC + IR + NCRTC)"
    ): GtfsDataset {
        fun readAsset(filename: String): String? = runCatching {
            context.assets.open("$assetDir/$filename").bufferedReader().use { it.readText() }
        }.getOrNull()

        return parseTables(
            agencyCsv = readAsset("agency.txt").orEmpty(),
            stopsCsv = readAsset("stops.txt").orEmpty(),
            routesCsv = readAsset("routes.txt").orEmpty(),
            tripsCsv = readAsset("trips.txt").orEmpty(),
            stopTimesCsv = readAsset("stop_times.txt").orEmpty(),
            calendarCsv = readAsset("calendar.txt").orEmpty(),
            calendarDatesCsv = readAsset("calendar_dates.txt").orEmpty(),
            frequenciesCsv = readAsset("frequencies.txt").orEmpty(),
            sourceLabel = sourceLabel,
            provenance = DataProvenance.SCHEDULED
        )
    }

    fun parseFromDirectory(
        directory: File,
        sourceLabel: String,
        provenance: DataProvenance = DataProvenance.SCHEDULED
    ): GtfsDataset {
        fun readFile(filename: String): String {
            val file = File(directory, filename)
            return if (file.exists()) file.readText() else ""
        }
        return parseTables(
            agencyCsv = readFile("agency.txt"),
            stopsCsv = readFile("stops.txt"),
            routesCsv = readFile("routes.txt"),
            tripsCsv = readFile("trips.txt"),
            stopTimesCsv = readFile("stop_times.txt"),
            calendarCsv = readFile("calendar.txt"),
            calendarDatesCsv = readFile("calendar_dates.txt"),
            frequenciesCsv = readFile("frequencies.txt"),
            sourceLabel = sourceLabel,
            provenance = provenance
        )
    }

    fun parseFromZipStream(
        inputStream: InputStream,
        sourceLabel: String,
        provenance: DataProvenance = DataProvenance.SCHEDULED
    ): GtfsDataset {
        val tableContents = mutableMapOf<String, String>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val baseName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && baseName.endsWith(".txt")) {
                    val reader = BufferedReader(InputStreamReader(zis))
                    tableContents[baseName] = reader.readText()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return parseTables(
            agencyCsv = tableContents["agency.txt"].orEmpty(),
            stopsCsv = tableContents["stops.txt"].orEmpty(),
            routesCsv = tableContents["routes.txt"].orEmpty(),
            tripsCsv = tableContents["trips.txt"].orEmpty(),
            stopTimesCsv = tableContents["stop_times.txt"].orEmpty(),
            calendarCsv = tableContents["calendar.txt"].orEmpty(),
            calendarDatesCsv = tableContents["calendar_dates.txt"].orEmpty(),
            frequenciesCsv = tableContents["frequencies.txt"].orEmpty(),
            sourceLabel = sourceLabel,
            provenance = provenance
        )
    }

    fun parseTables(
        agencyCsv: String,
        stopsCsv: String,
        routesCsv: String,
        tripsCsv: String,
        stopTimesCsv: String,
        calendarCsv: String,
        calendarDatesCsv: String,
        frequenciesCsv: String = "",
        sourceLabel: String = "GTFS Dataset",
        provenance: DataProvenance = DataProvenance.SCHEDULED
    ): GtfsDataset {
        val agencies = parseCsv(agencyCsv).mapNotNull { row ->
            val id = row["agency_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to TransitAgency(
                agencyId = id,
                name = row["agency_name"].orEmpty().ifBlank { id },
                url = row["agency_url"].orEmpty(),
                timezone = row["agency_timezone"].orEmpty().ifBlank { "Asia/Kolkata" }
            )
        }.toMap()

        val routes = parseCsv(routesCsv).mapNotNull { row ->
            val id = row["route_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val agencyId = row["agency_id"].orEmpty()
            val routeType = row["route_type"]?.toIntOrNull() ?: 3
            val mode = inferMode(routeType, agencyId, id)
            id to TransitRouteInfo(
                routeId = id,
                agencyId = agencyId,
                shortName = row["route_short_name"].orEmpty().ifBlank { id },
                longName = row["route_long_name"].orEmpty().ifBlank { row["route_short_name"].orEmpty() },
                mode = mode,
                colorHex = row["route_color"].orEmpty().ifBlank { defaultColorForMode(mode) }
            )
        }.toMap()

        val stops = parseCsv(stopsCsv).mapNotNull { row ->
            val id = row["stop_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = row["stop_lat"]?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = row["stop_lon"]?.toDoubleOrNull() ?: return@mapNotNull null
            val mode = inferStopMode(id, row["stop_name"].orEmpty())
            id to TransitStop(
                stopId = id,
                stopCode = row["stop_code"].orEmpty().ifBlank { id },
                name = row["stop_name"].orEmpty().ifBlank { id },
                description = row["stop_desc"].orEmpty(),
                lat = lat,
                lon = lon,
                zoneId = row["zone_id"].orEmpty(),
                primaryMode = mode
            )
        }.toMap()

        val trips = parseCsv(tripsCsv).mapNotNull { row ->
            val tripId = row["trip_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val routeId = row["route_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            tripId to GtfsTrip(
                tripId = tripId,
                routeId = routeId,
                serviceId = row["service_id"].orEmpty().ifBlank { "DAILY_ALL" },
                headsign = row["trip_headsign"].orEmpty(),
                directionId = row["direction_id"]?.toIntOrNull() ?: 0
            )
        }.toMap()

        val stopTimesByTrip = parseCsv(stopTimesCsv).mapNotNull { row ->
            val tripId = row["trip_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val stopId = row["stop_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val arr = parseGtfsTimeToSeconds(row["arrival_time"]) ?: return@mapNotNull null
            val dep = parseGtfsTimeToSeconds(row["departure_time"]) ?: arr
            val seq = row["stop_sequence"]?.toIntOrNull() ?: 0
            GtfsStopTime(
                tripId = tripId,
                arrivalSeconds = arr,
                departureSeconds = dep,
                stopId = stopId,
                stopSequence = seq
            )
        }.groupBy { it.tripId }
            .mapValues { (_, list) -> list.sortedBy { it.stopSequence } }

        val calendars = parseCsv(calendarCsv).mapNotNull { row ->
            val serviceId = row["service_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val days = booleanArrayOf(
                row["monday"] == "1",
                row["tuesday"] == "1",
                row["wednesday"] == "1",
                row["thursday"] == "1",
                row["friday"] == "1",
                row["saturday"] == "1",
                row["sunday"] == "1"
            )
            serviceId to GtfsCalendar(
                serviceId = serviceId,
                daysActive = days,
                startDate = row["start_date"].orEmpty(),
                endDate = row["end_date"].orEmpty()
            )
        }.toMap()

        val calendarDates = parseCsv(calendarDatesCsv).mapNotNull { row ->
            val serviceId = row["service_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val date = row["date"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val exType = row["exception_type"]?.toIntOrNull() ?: return@mapNotNull null
            GtfsCalendarDate(serviceId, date, exType)
        }

        val frequencies = parseCsv(frequenciesCsv).mapNotNull { row ->
            val tripId = row["trip_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val start = parseGtfsTimeToSeconds(row["start_time"]) ?: return@mapNotNull null
            val end = parseGtfsTimeToSeconds(row["end_time"]) ?: return@mapNotNull null
            val headway = row["headway_secs"]?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            GtfsFrequency(tripId, start, end, headway)
        }.groupBy { it.tripId }

        return GtfsDataset(
            agencies = agencies,
            stops = stops,
            routes = routes,
            trips = trips,
            stopTimesByTrip = stopTimesByTrip,
            calendars = calendars,
            calendarDates = calendarDates,
            frequencies = frequencies,
            sourceLabel = sourceLabel,
            provenance = provenance,
            loadedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun inferMode(routeType: Int, agencyId: String, routeId: String): TransitMode {
        val upperAgency = agencyId.uppercase()
        val upperRoute = routeId.uppercase()
        return when {
            upperAgency.contains("NCRTC") || upperRoute.contains("RRTS") -> TransitMode.RRTS
            routeType == 1 || upperAgency.contains("DMRC") || upperRoute.contains("METRO") -> TransitMode.METRO
            routeType == 2 || upperAgency.contains("IR") || upperRoute.contains("RAIL") -> TransitMode.RAILWAY
            else -> TransitMode.BUS
        }
    }

    private fun inferStopMode(stopId: String, stopName: String): TransitMode {
        val combined = "$stopId $stopName".uppercase()
        return when {
            combined.contains("RRTS") -> TransitMode.RRTS
            combined.contains("METRO") -> TransitMode.METRO
            combined.contains("RAIL") || combined.contains("JUNCTION") || combined.contains("RAILWAY") -> TransitMode.RAILWAY
            else -> TransitMode.BUS
        }
    }

    private fun defaultColorForMode(mode: TransitMode): String = when (mode) {
        TransitMode.METRO -> "111827"
        TransitMode.BUS -> "059669"
        TransitMode.RAILWAY -> "6D28D9"
        TransitMode.RRTS -> "D97706"
        TransitMode.WALK -> "64748B"
    }

    fun parseGtfsTimeToSeconds(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }

    private fun parseCsv(csvText: String): List<Map<String, String>> {
        val lines = csvText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size < 2) return emptyList()
        val headers = splitCsvLine(lines.first()).map { it.trim().removePrefix("\uFEFF") }
        return lines.drop(1).map { line ->
            val values = splitCsvLine(line)
            headers.mapIndexed { index, header ->
                header to values.getOrElse(index) { "" }.trim()
            }.toMap()
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
