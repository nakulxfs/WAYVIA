from typing import Dict, List

from app.models.schemas import JourneyRouteOut, StationCrowdOut, TransitAlertOut


class AwarenessService:
    def __init__(self) -> None:
        self.alerts: List[TransitAlertOut] = [
            TransitAlertOut(
                id="alert_dtc_405",
                title="DTC 405 · Ring Road Traffic",
                summary="Sample simulation: +8 min delay near India Gate.",
                mode="BUS",
                affected_route_id="DTC_405",
                status="MINOR_DELAY",
                delay_minutes=8,
                provenance="SIMULATED",
            ),
            TransitAlertOut(
                id="alert_ir_mtj",
                title="12279 Mathura-NDLS Intercity",
                summary="Sample simulation: +6 min approach clearance near Nizamuddin.",
                mode="RAILWAY",
                affected_route_id="IR_MTJ_EXP",
                status="MINOR_DELAY",
                delay_minutes=6,
                provenance="SIMULATED",
            ),
        ]
        self.crowds: List[StationCrowdOut] = [
            StationCrowdOut(stop_id="METRO_RAJIV_CHOWK", stop_name="Rajiv Chowk Metro Station", mode="METRO", crowd_level="HIGH", estimated_wait_extra_minutes=4, provenance="SIMULATED"),
            StationCrowdOut(stop_id="RAIL_NEW_DELHI", stop_name="New Delhi Railway Station", mode="RAILWAY", crowd_level="HIGH", estimated_wait_extra_minutes=5, provenance="SIMULATED"),
            StationCrowdOut(stop_id="RRTS_ANAND_VIHAR", stop_name="Anand Vihar RRTS Station", mode="RRTS", crowd_level="LOW", estimated_wait_extra_minutes=0, provenance="SIMULATED"),
        ]

    def delay_map(self) -> Dict[str, int]:
        return {a.affected_route_id: a.delay_minutes for a in self.alerts if a.affected_route_id}


def answer_journey_question(message: str, journey: JourneyRouteOut | None) -> str:
    q = message.lower()
    if journey is None:
        return "Search a journey first via POST /api/journey/search and pass journey_id to ask itinerary-specific questions."
    if any(k in q for k in ["cheap", "fare", "cost", "price"]):
        return f"This {journey.badge_type} route has an estimated fare of ₹{journey.total_fare_inr} ({journey.fare_provenance})."
    if any(k in q for k in ["transfer", "change", "interchange"]):
        return f"This journey requires {journey.transfer_count} transfer(s) across {len([s for s in journey.segments if s.mode != 'WALK'])} transit leg(s)."
    if any(k in q for k in ["walk", "walking"]):
        return f"Total walking distance is {journey.total_walking_meters} meters."
    return (
        f"Journey {journey.origin_name} → {journey.dest_name}: "
        f"{journey.total_duration_minutes} min, ₹{journey.total_fare_inr}, "
        f"{journey.transfer_count} transfer(s), {journey.total_walking_meters}m walk."
    )
