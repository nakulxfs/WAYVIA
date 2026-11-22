from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query

from app.data_ingestion.gtfs_loader import GtfsStore
from app.database import db
from app.models.schemas import (
    DepartureOut,
    JourneyChatRequest,
    JourneyChatResponse,
    JourneyRouteOut,
    JourneySearchRequest,
    LocationResult,
    RouteOut,
    SavedRouteCreate,
    SavedRouteOut,
    StationCrowdOut,
    StopOut,
    TransitAlertOut,
)
from app.routing.engine import BackendRoutingEngine, haversine_meters
from app.services.awareness import AwarenessService, answer_journey_question

router = APIRouter(prefix="/api", tags=["CommuteBuddy Transit API"])

gtfs_store = GtfsStore()
routing_engine = BackendRoutingEngine(gtfs_store)
awareness_service = AwarenessService()


def _format_clock(secs: int) -> str:
    secs = secs % 86400
    h, m = secs // 3600, (secs % 3600) // 60
    return f"{h:02d}:{m:02d}"


@router.get("/locations/search", response_model=List[LocationResult])
def search_locations(q: str = Query(..., min_length=1)) -> List[LocationResult]:
    lower = q.strip().lower()
    results = []
    for stop in gtfs_store.stops.values():
        if lower in stop["name"].lower() or lower in stop["description"].lower():
            results.append(
                LocationResult(
                    id=f"stop_{stop['stop_id']}",
                    name=stop["name"],
                    subtitle=f"{stop['mode']} · {stop['description']}",
                    lat=stop["lat"],
                    lon=stop["lon"],
                    stop_id=stop["stop_id"],
                )
            )
    return results[:12]


@router.get("/transit/stops/nearby", response_model=List[StopOut])
def nearby_stops(
    lat: float = Query(28.6315),
    lon: float = Query(77.2167),
    mode: Optional[str] = None,
    limit: int = Query(15, ge=1, le=50),
) -> List[StopOut]:
    items = []
    for stop in gtfs_store.stops.values():
        if mode and stop["mode"] != mode.upper():
            continue
        dist = haversine_meters(lat, lon, stop["lat"], stop["lon"])
        items.append(StopOut(**stop, distance_meters=dist))
    items.sort(key=lambda s: s.distance_meters or 0)
    return items[:limit]


@router.get("/transit/routes", response_model=List[RouteOut])
def list_routes() -> List[RouteOut]:
    return [RouteOut(**r) for r in gtfs_store.routes.values()]


@router.get("/transit/departures", response_model=List[DepartureOut])
def stop_departures(
    stop_id: str = Query(...),
    after_seconds: int = Query(30600),
    limit: int = Query(8, ge=1, le=20),
) -> List[DepartureOut]:
    delays = awareness_service.delay_map()
    edges = [e for e in routing_engine.transit_edges.get(stop_id, []) if e["departure_seconds"] >= after_seconds]
    out = []
    for e in edges[:limit]:
        delay = delays.get(e["route_id"], 0)
        dep = e["departure_seconds"] + delay * 60
        out.append(
            DepartureOut(
                route_short_name=e["route_short_name"],
                headsign=e["headsign"],
                mode=e["mode"],
                departure_seconds=dep,
                departure_clock=_format_clock(dep),
                delay_minutes=delay,
                provenance="SIMULATED" if delay > 0 else "SCHEDULED",
            )
        )
    return out


@router.post("/journey/search", response_model=List[JourneyRouteOut])
def search_journey(req: JourneySearchRequest) -> List[JourneyRouteOut]:
    return routing_engine.search_multimodal(req, awareness_service.delay_map())


@router.get("/journey/{journey_id}", response_model=JourneyRouteOut)
def get_journey(journey_id: str) -> JourneyRouteOut:
    journey = routing_engine.journey_cache.get(journey_id)
    if not journey:
        raise HTTPException(status_code=404, detail="Journey not found")
    return journey


@router.post("/journey/chat", response_model=JourneyChatResponse)
def journey_chat(req: JourneyChatRequest) -> JourneyChatResponse:
    journey = routing_engine.journey_cache.get(req.journey_id) if req.journey_id else None
    return JourneyChatResponse(reply=answer_journey_question(req.message, journey))


@router.get("/transit/alerts", response_model=List[TransitAlertOut])
def get_alerts() -> List[TransitAlertOut]:
    return awareness_service.alerts


@router.get("/transit/crowd", response_model=List[StationCrowdOut])
def get_crowd() -> List[StationCrowdOut]:
    return awareness_service.crowds


@router.post("/transit/refresh")
async def refresh_transit(gtfs_zip_url: Optional[str] = None) -> dict:
    summary = await gtfs_store.refresh_from_remote_zip(gtfs_zip_url)
    routing_engine.rebuild_graph()
    return {"status": "ok", "summary": summary, "stops": len(gtfs_store.stops), "routes": len(gtfs_store.routes)}


@router.post("/routes/saved", response_model=SavedRouteOut)
def create_saved_route(payload: SavedRouteCreate) -> SavedRouteOut:
    record = db.insert_saved_route(payload.model_dump())
    return SavedRouteOut(**record)


@router.get("/routes/saved", response_model=List[SavedRouteOut])
def get_saved_routes() -> List[SavedRouteOut]:
    return [SavedRouteOut(**r) for r in db.list_saved_routes()]


@router.delete("/routes/saved/{route_id}")
def remove_saved_route(route_id: str) -> dict:
    deleted = db.delete_saved_route(route_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Saved route not found")
    return {"deleted": True, "route_id": route_id}
