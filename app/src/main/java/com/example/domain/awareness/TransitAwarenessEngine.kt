package com.example.domain.awareness

import com.example.domain.models.CrowdLevel
import com.example.domain.models.DataProvenance
import com.example.domain.models.ServiceStatus
import com.example.domain.models.StationCrowdStatus
import com.example.domain.models.TransitAlert
import com.example.domain.models.TransitMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Transit delay & crowd awareness module.
 * Explicitly labels all simulated/sample feeds as DataProvenance.SIMULATED
 * so demonstration data is never misrepresented as LIVE telemetry.
 */
class TransitAwarenessEngine {

    private val _alerts = MutableStateFlow(initialSimulatedAlerts())
    val alerts: StateFlow<List<TransitAlert>> = _alerts.asStateFlow()

    private val _crowdStatuses = MutableStateFlow(initialSimulatedCrowds())
    val crowdStatuses: StateFlow<List<StationCrowdStatus>> = _crowdStatuses.asStateFlow()

    private fun initialSimulatedAlerts(): List<TransitAlert> {
        val now = System.currentTimeMillis()
        return listOf(
            TransitAlert(
                id = "alert_dtc_405",
                title = "DTC 405 · Ring Road Traffic",
                summary = "Sample simulation: +8 min delay near ITO and India Gate due to peak hour congestion.",
                mode = TransitMode.BUS,
                affectedRouteId = "DTC_405",
                affectedStopId = "BUS_INDIA_GATE",
                status = ServiceStatus.MINOR_DELAY,
                delayMinutes = 8,
                provenance = DataProvenance.SIMULATED,
                updatedAtEpochMs = now
            ),
            TransitAlert(
                id = "alert_dmrc_yellow",
                title = "DMRC Yellow Line · Normal Service",
                summary = "Trains operating at scheduled 6–8 minute headway between Gurugram and Kashmere Gate.",
                mode = TransitMode.METRO,
                affectedRouteId = "DMRC_YELLOW",
                affectedStopId = "METRO_RAJIV_CHOWK",
                status = ServiceStatus.NORMAL,
                delayMinutes = 0,
                provenance = DataProvenance.SIMULATED,
                updatedAtEpochMs = now
            ),
            TransitAlert(
                id = "alert_ir_mtj",
                title = "12279 Mathura–NDLS Intercity",
                summary = "Sample simulation: +6 min approach clearance near Hazrat Nizamuddin outer.",
                mode = TransitMode.RAILWAY,
                affectedRouteId = "IR_MTJ_EXP",
                affectedStopId = "RAIL_NIZAMUDDIN",
                status = ServiceStatus.MINOR_DELAY,
                delayMinutes = 6,
                provenance = DataProvenance.SIMULATED,
                updatedAtEpochMs = now
            ),
            TransitAlert(
                id = "alert_rrts_namo",
                title = "Namo Bharat RRTS · On Time",
                summary = "High-speed Rapid Rail corridor operating smoothly between New Ashok Nagar and Meerut South.",
                mode = TransitMode.RRTS,
                affectedRouteId = "RRTS_NAMO",
                affectedStopId = "RRTS_ANAND_VIHAR",
                status = ServiceStatus.NORMAL,
                delayMinutes = 0,
                provenance = DataProvenance.SIMULATED,
                updatedAtEpochMs = now
            )
        )
    }

    private fun initialSimulatedCrowds(): List<StationCrowdStatus> {
        val now = System.currentTimeMillis()
        return listOf(
            StationCrowdStatus("METRO_RAJIV_CHOWK", "Rajiv Chowk Metro Station", TransitMode.METRO, CrowdLevel.HIGH, 4, DataProvenance.SIMULATED, now),
            StationCrowdStatus("METRO_NEW_DELHI", "New Delhi Metro Station", TransitMode.METRO, CrowdLevel.MODERATE, 2, DataProvenance.SIMULATED, now),
            StationCrowdStatus("METRO_HAUZ_KHAS", "Hauz Khas Metro Station", TransitMode.METRO, CrowdLevel.MODERATE, 2, DataProvenance.SIMULATED, now),
            StationCrowdStatus("METRO_KASHMERE_GATE", "Kashmere Gate Metro Station", TransitMode.METRO, CrowdLevel.HIGH, 4, DataProvenance.SIMULATED, now),
            StationCrowdStatus("RAIL_NEW_DELHI", "New Delhi Railway Station", TransitMode.RAILWAY, CrowdLevel.HIGH, 5, DataProvenance.SIMULATED, now),
            StationCrowdStatus("RAIL_NIZAMUDDIN", "Hazrat Nizamuddin Railway Station", TransitMode.RAILWAY, CrowdLevel.MODERATE, 3, DataProvenance.SIMULATED, now),
            StationCrowdStatus("BUS_CONNAUGHT_PLACE", "Connaught Place Outer Circle Bus Stop", TransitMode.BUS, CrowdLevel.MODERATE, 2, DataProvenance.SIMULATED, now),
            StationCrowdStatus("RRTS_ANAND_VIHAR", "Anand Vihar RRTS Station", TransitMode.RRTS, CrowdLevel.LOW, 0, DataProvenance.SIMULATED, now)
        )
    }

    fun simulateBusDelay(minutes: Int = 15) {
        val now = System.currentTimeMillis()
        val status = when {
            minutes == 0 -> ServiceStatus.NORMAL
            minutes <= 10 -> ServiceStatus.MINOR_DELAY
            minutes <= 20 -> ServiceStatus.HEAVY_DELAY
            else -> ServiceStatus.DISRUPTED
        }
        _alerts.update { list ->
            list.map { alert ->
                if (alert.mode == TransitMode.BUS) {
                    alert.copy(
                        status = status,
                        delayMinutes = minutes,
                        summary = if (minutes == 0) {
                            "Sample simulation reset: DTC buses running on published schedule."
                        } else {
                            "Sample simulation: +$minutes min delay on DTC corridor due to arterial congestion."
                        },
                        provenance = DataProvenance.SIMULATED,
                        updatedAtEpochMs = now
                    )
                } else alert
            }
        }
    }

    fun simulateTrainDelay(minutes: Int = 18) {
        val now = System.currentTimeMillis()
        val status = when {
            minutes == 0 -> ServiceStatus.NORMAL
            minutes <= 10 -> ServiceStatus.MINOR_DELAY
            minutes <= 25 -> ServiceStatus.HEAVY_DELAY
            else -> ServiceStatus.DISRUPTED
        }
        _alerts.update { list ->
            list.map { alert ->
                if (alert.mode == TransitMode.RAILWAY) {
                    alert.copy(
                        status = status,
                        delayMinutes = minutes,
                        summary = if (minutes == 0) {
                            "Sample simulation reset: NCR rail services running on timetable."
                        } else {
                            "Sample simulation: +$minutes min train delay reported on Mathura–New Delhi line."
                        },
                        provenance = DataProvenance.SIMULATED,
                        updatedAtEpochMs = now
                    )
                } else alert
            }
        }
    }

    fun cycleMetroCrowd() {
        val now = System.currentTimeMillis()
        _crowdStatuses.update { list ->
            list.map { item ->
                if (item.mode == TransitMode.METRO) {
                    val nextLevel = when (item.crowdLevel) {
                        CrowdLevel.LOW -> CrowdLevel.MODERATE
                        CrowdLevel.MODERATE -> CrowdLevel.HIGH
                        CrowdLevel.HIGH -> CrowdLevel.LOW
                    }
                    val extraWait = when (nextLevel) {
                        CrowdLevel.LOW -> 0
                        CrowdLevel.MODERATE -> 2
                        CrowdLevel.HIGH -> 5
                    }
                    item.copy(
                        crowdLevel = nextLevel,
                        estimatedWaitExtraMinutes = extraWait,
                        provenance = DataProvenance.SIMULATED,
                        updatedAtEpochMs = now
                    )
                } else item
            }
        }
    }

    fun simulateStationCongestion(stopId: String, level: CrowdLevel) {
        val now = System.currentTimeMillis()
        _crowdStatuses.update { list ->
            list.map { item ->
                if (item.stopId == stopId) {
                    item.copy(
                        crowdLevel = level,
                        estimatedWaitExtraMinutes = when (level) {
                            CrowdLevel.LOW -> 0
                            CrowdLevel.MODERATE -> 2
                            CrowdLevel.HIGH -> 6
                        },
                        provenance = DataProvenance.SIMULATED,
                        updatedAtEpochMs = now
                    )
                } else item
            }
        }
    }

    fun resetSimulation() {
        _alerts.value = initialSimulatedAlerts()
        _crowdStatuses.value = initialSimulatedCrowds()
    }

    fun crowdForStop(stopId: String?): StationCrowdStatus? {
        if (stopId == null) return null
        return _crowdStatuses.value.find { it.stopId == stopId }
    }
}
