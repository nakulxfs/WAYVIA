import csv
import io
import zipfile
from pathlib import Path
from typing import Dict, List, Optional

import httpx

ASSETS_GTFS_DIR = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "assets" / "gtfs_delhi_ncr"


def parse_gtfs_time(raw: str) -> int:
    parts = raw.strip().split(":")
    h = int(parts[0])
    m = int(parts[1])
    s = int(parts[2]) if len(parts) > 2 else 0
    return h * 3600 + m * 60 + s


def infer_mode(route_type: int, agency_id: str, route_id: str) -> str:
    upper = f"{agency_id} {route_id}".upper()
    if "NCRTC" in upper or "RRTS" in upper:
        return "RRTS"
    if route_type == 1 or "DMRC" in upper or "METRO" in upper:
        return "METRO"
    if route_type == 2 or "IR" in upper or "RAIL" in upper:
        return "RAILWAY"
    return "BUS"


def infer_stop_mode(stop_id: str, stop_name: str) -> str:
    upper = f"{stop_id} {stop_name}".upper()
    if "RRTS" in upper:
        return "RRTS"
    if "METRO" in upper:
        return "METRO"
    if "RAIL" in upper or "JUNCTION" in upper:
        return "RAILWAY"
    return "BUS"


class GtfsStore:
    def __init__(self) -> None:
        self.stops: Dict[str, Dict] = {}
        self.routes: Dict[str, Dict] = {}
        self.trips: Dict[str, Dict] = {}
        self.stop_times: Dict[str, List[Dict]] = {}
        self.frequencies: Dict[str, List[Dict]] = {}
        self.source_label: str = "Delhi NCR Seeded GTFS"
        self.load_from_directory(ASSETS_GTFS_DIR)

    def _read_csv_text(self, text: str) -> List[Dict[str, str]]:
        if not text.strip():
            return []
        return list(csv.DictReader(io.StringIO(text)))

    def load_tables(self, tables: Dict[str, str], source_label: str) -> None:
        stops = {}
        for row in self._read_csv_text(tables.get("stops.txt", "")):
            sid = row.get("stop_id", "").strip()
            if not sid:
                continue
            stops[sid] = {
                "stop_id": sid,
                "stop_code": row.get("stop_code", sid),
                "name": row.get("stop_name", sid),
                "description": row.get("stop_desc", ""),
                "lat": float(row["stop_lat"]),
                "lon": float(row["stop_lon"]),
                "mode": infer_stop_mode(sid, row.get("stop_name", "")),
            }

        routes = {}
        for row in self._read_csv_text(tables.get("routes.txt", "")):
            rid = row.get("route_id", "").strip()
            if not rid:
                continue
            agency = row.get("agency_id", "")
            rtype = int(row.get("route_type", "3") or 3)
            routes[rid] = {
                "route_id": rid,
                "agency_id": agency,
                "short_name": row.get("route_short_name", rid),
                "long_name": row.get("route_long_name", rid),
                "mode": infer_mode(rtype, agency, rid),
                "color_hex": row.get("route_color", "38E8FF"),
            }

        trips = {}
        for row in self._read_csv_text(tables.get("trips.txt", "")):
            tid = row.get("trip_id", "").strip()
            if tid:
                trips[tid] = {
                    "trip_id": tid,
                    "route_id": row.get("route_id", ""),
                    "service_id": row.get("service_id", "DAILY_ALL"),
                    "headsign": row.get("trip_headsign", ""),
                }

        stop_times: Dict[str, List[Dict]] = {}
        for row in self._read_csv_text(tables.get("stop_times.txt", "")):
            tid = row.get("trip_id", "").strip()
            if not tid:
                continue
            stop_times.setdefault(tid, []).append(
                {
                    "trip_id": tid,
                    "stop_id": row["stop_id"].strip(),
                    "arrival_seconds": parse_gtfs_time(row["arrival_time"]),
                    "departure_seconds": parse_gtfs_time(row["departure_time"]),
                    "stop_sequence": int(row["stop_sequence"]),
                }
            )
        for tid in stop_times:
            stop_times[tid].sort(key=lambda x: x["stop_sequence"])

        frequencies: Dict[str, List[Dict]] = {}
        for row in self._read_csv_text(tables.get("frequencies.txt", "")):
            tid = row.get("trip_id", "").strip()
            if tid:
                frequencies.setdefault(tid, []).append(
                    {
                        "start_seconds": parse_gtfs_time(row["start_time"]),
                        "end_seconds": parse_gtfs_time(row["end_time"]),
                        "headway_seconds": int(row["headway_secs"]),
                    }
                )

        if stops and routes and trips:
            self.stops = stops
            self.routes = routes
            self.trips = trips
            self.stop_times = stop_times
            self.frequencies = frequencies
            self.source_label = source_label

    def load_from_directory(self, directory: Path) -> None:
        tables = {}
        for fname in ["agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt", "calendar_dates.txt", "frequencies.txt"]:
            path = directory / fname
            if path.exists():
                tables[fname] = path.read_text(encoding="utf-8")
        self.load_tables(tables, "Delhi NCR Seeded GTFS")

    async def refresh_from_remote_zip(self, url: Optional[str] = None) -> str:
        target = url or "https://otd.delhi.gov.in/data/static/GTFS.zip"
        try:
            async with httpx.AsyncClient(timeout=6.0) as client:
                resp = await client.get(target)
                if resp.status_code == 200 and "zip" in resp.headers.get("content-type", ""):
                    with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
                        tables = {
                            name.split("/")[-1]: zf.read(name).decode("utf-8", errors="ignore")
                            for name in zf.namelist()
                            if name.endswith(".txt")
                        }
                    self.load_tables(tables, f"Remote GTFS ({target})")
                    return f"Updated from {target} ({len(self.stops)} stops)"
        except Exception:
            pass
        self.load_from_directory(ASSETS_GTFS_DIR)
        return f"Fallback to seeded Delhi NCR GTFS ({len(self.stops)} stops)"
