from typing import List, Optional
from pydantic import BaseModel, Field


class LocationResult(BaseModel):
    id: str
    name: str
    subtitle: str
    lat: float
    lon: float
    stop_id: Optional[str] = None
    source: str = "GTFS"


class StopOut(BaseModel):
    stop_id: str
    stop_code: str
    name: str
    description: str
    lat: float
    lon: float
    mode: str
    distance_meters: Optional[int] = None


class RouteOut(BaseModel):
    route_id: str
    agency_id: str
    short_name: str
    long_name: str
    mode: str
    color_hex: str


class DepartureOut(BaseModel):
    route_short_name: str
    headsign: str
    mode: str
    departure_seconds: int
    departure_clock: str
    delay_minutes: int = 0
    provenance: str = "SCHEDULED"


class JourneySearchRequest(BaseModel):
    origin_lat: float
    origin_lon: float
    origin_name: str = "Origin"
    origin_stop_id: Optional[str] = None
    dest_lat: float
    dest_lon: float
    dest_name: str = "Destination"
    dest_stop_id: Optional[str] = None
    departure_seconds: int = Field(default=30600, ge=0, le=100000)
    allowed_modes: List[str] = Field(default_factory=lambda: ["METRO", "BUS", "RAILWAY", "RRTS"])
    max_walk_meters: int = Field(default=1200, ge=200, le=3000)
    max_transfers: int = Field(default=4, ge=0, le=6)


class JourneySegmentOut(BaseModel):
    mode: str
    route_id: Optional[str] = None
    route_short_name: str
    from_stop_id: Optional[str] = None
    from_name: str
    to_stop_id: Optional[str] = None
    to_name: str
    departure_seconds: int
    arrival_seconds: int
    duration_minutes: int
    wait_seconds: int
    distance_meters: int
    fare_inr: int
    instruction: str


class JourneyRouteOut(BaseModel):
    journey_id: str
    badge_type: str
    origin_name: str
    dest_name: str
    departure_seconds: int
    arrival_seconds: int
    total_duration_minutes: int
    total_fare_inr: int
    transfer_count: int
    total_walking_meters: int
    schedule_provenance: str = "SCHEDULED"
    fare_provenance: str = "ESTIMATED"
    segments: List[JourneySegmentOut]


class JourneyChatRequest(BaseModel):
    journey_id: Optional[str] = None
    message: str


class JourneyChatResponse(BaseModel):
    reply: str
    provenance: str = "SCHEDULED"


class TransitAlertOut(BaseModel):
    id: str
    title: str
    summary: str
    mode: str
    affected_route_id: Optional[str] = None
    status: str
    delay_minutes: int
    provenance: str = "SIMULATED"


class StationCrowdOut(BaseModel):
    stop_id: str
    stop_name: str
    mode: str
    crowd_level: str
    estimated_wait_extra_minutes: int
    provenance: str = "SIMULATED"


class SavedRouteCreate(BaseModel):
    custom_name: str
    origin_name: str
    origin_lat: float
    origin_lon: float
    dest_name: str
    dest_lat: float
    dest_lon: float
    duration_minutes: int
    fare_inr: int
    transfer_count: int
    modes_summary: str


class SavedRouteOut(SavedRouteCreate):
    route_id: str
