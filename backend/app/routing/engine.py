import heapq
import math
import uuid
from typing import Dict, List, Optional, Tuple

from app.data_ingestion.gtfs_loader import GtfsStore
from app.models.schemas import JourneyRouteOut, JourneySearchRequest, JourneySegmentOut

WALK_SPEED_MPS = 1.25


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    r = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2
    return int(round(2 * r * math.asin(math.sqrt(a))))


def boarding_base_fare(mode: str, route_id: str) -> int:
    if mode == "METRO":
        return 20 if "AIRPORT" in route_id.upper() else 10
    if mode == "BUS":
        return 5
    if mode == "RAILWAY":
        return 10 if ("MEMU" in route_id.upper() or "EMU" in route_id.upper()) else 35
    if mode == "RRTS":
        return 20
    return 0


def incremental_fare(mode: str, route_id: str, dist_m: int) -> int:
    km = dist_m / 1000.0
    if mode == "METRO":
        return max(5, min(60, int(round(km * (3.5 if "AIRPORT" in route_id.upper() else 2.2)))))
    if mode == "BUS":
        return max(3, min(10, int(round(km * 0.9))))
    if mode == "RAILWAY":
        rate = 0.28 if ("MEMU" in route_id.upper() or "EMU" in route_id.upper()) else 0.72
        return max(5, min(65, int(round(km * rate))))
    if mode == "RRTS":
        return max(15, min(40, int(round(km * 2.4))))
    return 0


class BackendRoutingEngine:
    def __init__(self, store: GtfsStore) -> None:
        self.store = store
        self.transit_edges: Dict[str, List[Dict]] = {}
        self.walk_edges: Dict[str, List[Dict]] = {}
        self.journey_cache: Dict[str, JourneyRouteOut] = {}
        self.rebuild_graph()

    def rebuild_graph(self) -> None:
        edges: Dict[str, List[Dict]] = {}
        for tid, trip in self.store.trips.items():
            route = self.store.routes.get(trip["route_id"])
            st_list = self.store.stop_times.get(tid, [])
            if not route or len(st_list) < 2:
                continue
            base_start = st_list[0]["departure_seconds"]
            freqs = self.store.frequencies.get(tid, [])
            offsets = [(0, tid)] if not freqs else []
            for fi, f in enumerate(freqs):
                dep = f["start_seconds"]
                idx = 0
                while dep <= f["end_seconds"]:
                    offsets.append((dep - base_start, f"{tid}_F{fi}_{idx}"))
                    dep += f["headway_seconds"]
                    idx += 1

            for offset, inst_id in offsets:
                for i in range(len(st_list) - 1):
                    a, b = st_list[i], st_list[i + 1]
                    sa, sb = self.store.stops.get(a["stop_id"]), self.store.stops.get(b["stop_id"])
                    if not sa or not sb:
                        continue
                    dep_s = a["departure_seconds"] + offset
                    arr_s = b["arrival_seconds"] + offset
                    if arr_s <= dep_s:
                        continue
                    dist = haversine_meters(sa["lat"], sa["lon"], sb["lat"], sb["lon"])
                    edges.setdefault(sa["stop_id"], []).append(
                        {
                            "trip_instance": inst_id,
                            "route_id": route["route_id"],
                            "route_short_name": route["short_name"],
                            "headsign": trip["headsign"],
                            "mode": route["mode"],
                            "from_stop_id": sa["stop_id"],
                            "to_stop_id": sb["stop_id"],
                            "departure_seconds": dep_s,
                            "arrival_seconds": arr_s,
                            "distance_meters": dist,
                            "hop_fare": incremental_fare(route["mode"], route["route_id"], dist),
                        }
                    )
        for sid in edges:
            edges[sid].sort(key=lambda e: e["departure_seconds"])
        self.transit_edges = edges

        walks: Dict[str, List[Dict]] = {}
        stops_list = list(self.store.stops.values())
        for i in range(len(stops_list)):
            for j in range(i + 1, len(stops_list)):
                sa, sb = stops_list[i], stops_list[j]
                dist = haversine_meters(sa["lat"], sa["lon"], sb["lat"], sb["lon"])
                if dist <= 2200:
                    wsec = int(round(dist / WALK_SPEED_MPS + 60))
                    walks.setdefault(sa["stop_id"], []).append({"to_stop_id": sb["stop_id"], "distance_meters": dist, "walk_seconds": wsec})
                    walks.setdefault(sb["stop_id"], []).append({"to_stop_id": sa["stop_id"], "distance_meters": dist, "walk_seconds": wsec})
        self.walk_edges = walks

    def _score(self, objective: str, elapsed_s: int, fare: int, transfers: int, walk_m: int) -> float:
        mins = elapsed_s / 60.0
        wkm = walk_m / 1000.0
        if objective == "FASTEST":
            return float(elapsed_s + transfers * 180)
        if objective == "CHEAPEST":
            return fare * 100.0 + mins * 0.45 + transfers * 12.0 + wkm * 4.0
        return mins * 1.0 + fare * 0.9 + transfers * 9.5 + wkm * 7.0

    def _nearby(self, lat: float, lon: float, exact_id: Optional[str], radius: int) -> List[Tuple[Dict, int]]:
        scored = [(s, haversine_meters(lat, lon, s["lat"], s["lon"])) for s in self.store.stops.values()]
        scored.sort(key=lambda x: x[1])
        res = [x for x in scored if x[1] <= max(radius, 1800)][:6]
        if exact_id and exact_id in self.store.stops and all(s["stop_id"] != exact_id for s, _ in res):
            res.insert(0, (self.store.stops[exact_id], 0))
        return res or scored[:2]

    def search_single(self, req: JourneySearchRequest, objective: str, delay_by_route: Dict[str, int]) -> Optional[JourneyRouteOut]:
        access = self._nearby(req.origin_lat, req.origin_lon, req.origin_stop_id, req.max_walk_meters)
        egress = self._nearby(req.dest_lat, req.dest_lon, req.dest_stop_id, req.max_walk_meters)
        egress_map = {s["stop_id"]: (s, d) for s, d in egress}
        allowed = set(req.allowed_modes)

        pq = []
        best: Dict[str, float] = {}
        parent: Dict[str, Dict] = {}

        for stop, dist in access:
            wsec = 60 if dist <= 60 else int(round(dist / WALK_SPEED_MPS))
            arr = req.departure_seconds + wsec
            key = f"{stop['stop_id']}|NONE|0"
            sc = self._score(objective, wsec, 0, 0, dist)
            best[key] = sc
            parent[key] = {"prev": "ORIGIN", "edge": None, "walk_to": stop["stop_id"], "walk_m": dist, "walk_s": wsec, "arr": arr, "wait": 0, "fare": 0}
            heapq.heappush(pq, (sc, stop["stop_id"], None, arr, 0, 0, dist, key))

        goal_key, goal_score, goal_egress = None, float("inf"), None
        max_arr = req.departure_seconds + 7 * 3600
        expanded = 0

        while pq and expanded < 12000:
            sc, sid, active_inst, arr, fare, transfers, walk_m, key = heapq.heappop(pq)
            if sc > best.get(key, float("inf")) + 1e-6 or sc >= goal_score:
                continue
            expanded += 1

            if sid in egress_map and active_inst is not None:
                es, ed = egress_map[sid]
                ew = 60 if ed <= 60 else int(round(ed / WALK_SPEED_MPS))
                gsc = self._score(objective, (arr + ew) - req.departure_seconds, fare, transfers, walk_m + ed)
                if gsc < goal_score:
                    goal_score, goal_key, goal_egress = gsc, key, (es, ed)

            seen_rn = set()
            for edge in self.transit_edges.get(sid, []):
                if edge["mode"] not in allowed:
                    continue
                dsec = delay_by_route.get(edge["route_id"], 0) * 60
                edep, earr = edge["departure_seconds"] + dsec, edge["arrival_seconds"] + dsec
                same_trip = active_inst == edge["trip_instance"]
                if edep < (arr if same_trip else arr + 90) or earr > max_arr:
                    continue
                rk = (edge["route_id"], edge["to_stop_id"])
                if not same_trip and rk in seen_rn:
                    continue
                seen_rn.add(rk)
                ntrans = transfers + (1 if (active_inst is not None and not same_trip) else 0)
                if ntrans > req.max_transfers:
                    continue
                add_fare = edge["hop_fare"] + (0 if same_trip else boarding_base_fare(edge["mode"], edge["route_id"]))
                nfare = fare + add_fare
                nsc = self._score(objective, earr - req.departure_seconds, nfare, ntrans, walk_m)
                nkey = f"{edge['to_stop_id']}|{edge['route_id']}|{ntrans}"
                if nsc < best.get(nkey, float("inf")):
                    best[nkey] = nsc
                    parent[nkey] = {"prev": key, "edge": {**edge, "departure_seconds": edep, "arrival_seconds": earr}, "walk_to": None, "walk_m": 0, "walk_s": 0, "arr": earr, "wait": max(0, edep - arr), "fare": add_fare}
                    heapq.heappush(pq, (nsc, edge["to_stop_id"], edge["trip_instance"], earr, nfare, ntrans, walk_m, nkey))

            if active_inst is not None and transfers < req.max_transfers:
                for w in self.walk_edges.get(sid, []):
                    if w["distance_meters"] > req.max_walk_meters:
                        continue
                    narr = arr + w["walk_seconds"]
                    nwalk = walk_m + w["distance_meters"]
                    nsc = self._score(objective, narr - req.departure_seconds, fare, transfers, nwalk)
                    nkey = f"{w['to_stop_id']}|WALK|{transfers}"
                    if nsc < best.get(nkey, float("inf")):
                        best[nkey] = nsc
                        parent[nkey] = {"prev": key, "edge": None, "walk_to": w["to_stop_id"], "walk_m": w["distance_meters"], "walk_s": w["walk_seconds"], "arr": narr, "wait": 0, "fare": 0}
                        heapq.heappush(pq, (nsc, w["to_stop_id"], None, narr, fare, transfers, nwalk, nkey))

        if not goal_key or not goal_egress:
            return None

        steps = []
        cur = goal_key
        while cur and cur != "ORIGIN":
            st = parent.get(cur)
            if not st:
                break
            steps.append(st)
            cur = st["prev"]
        steps.reverse()

        segments: List[JourneySegmentOut] = []
        total_walk = 0
        for st in steps:
            if st["edge"] is None:
                target = self.store.stops[st["walk_to"]]
                total_walk += st["walk_m"]
                segments.append(
                    JourneySegmentOut(
                        mode="WALK",
                        route_short_name="Walk",
                        from_name=req.origin_name if not segments else segments[-1].to_name,
                        to_stop_id=target["stop_id"],
                        to_name=target["name"],
                        departure_seconds=st["arr"] - st["walk_s"],
                        arrival_seconds=st["arr"],
                        duration_minutes=max(1, st["walk_s"] // 60),
                        wait_seconds=0,
                        distance_meters=st["walk_m"],
                        fare_inr=0,
                        instruction=f"Walk {st['walk_m']}m to {target['name']}",
                    )
                )
            else:
                e = st["edge"]
                fs, ts = self.store.stops[e["from_stop_id"]], self.store.stops[e["to_stop_id"]]
                if segments and segments[-1].route_id == e["route_id"] and segments[-1].to_stop_id == fs["stop_id"]:
                    prev = segments[-1]
                    segments[-1] = prev.model_copy(
                        update={
                            "to_stop_id": ts["stop_id"],
                            "to_name": ts["name"],
                            "arrival_seconds": e["arrival_seconds"],
                            "duration_minutes": max(1, (e["arrival_seconds"] - prev.departure_seconds) // 60),
                            "distance_meters": prev.distance_meters + e["distance_meters"],
                            "fare_inr": prev.fare_inr + st["fare"],
                            "instruction": f"Ride {e['route_short_name']} from {prev.from_name} to {ts['name']}",
                        }
                    )
                else:
                    segments.append(
                        JourneySegmentOut(
                            mode=e["mode"],
                            route_id=e["route_id"],
                            route_short_name=e["route_short_name"],
                            from_stop_id=fs["stop_id"],
                            from_name=fs["name"],
                            to_stop_id=ts["stop_id"],
                            to_name=ts["name"],
                            departure_seconds=e["departure_seconds"],
                            arrival_seconds=e["arrival_seconds"],
                            duration_minutes=max(1, (e["arrival_seconds"] - e["departure_seconds"]) // 60),
                            wait_seconds=st["wait"],
                            distance_meters=e["distance_meters"],
                            fare_inr=st["fare"],
                            instruction=f"Board {e['route_short_name']} from {fs['name']} to {ts['name']}",
                        )
                    )

        es, ed = goal_egress
        ew = 60 if ed <= 60 else int(round(ed / WALK_SPEED_MPS))
        last_arr = segments[-1].arrival_seconds if segments else req.departure_seconds
        total_walk += ed
        segments.append(
            JourneySegmentOut(
                mode="WALK",
                route_short_name="Walk",
                from_stop_id=es["stop_id"],
                from_name=es["name"],
                to_name=req.dest_name,
                departure_seconds=last_arr,
                arrival_seconds=last_arr + ew,
                duration_minutes=max(1, ew // 60),
                wait_seconds=0,
                distance_meters=ed,
                fare_inr=0,
                instruction=f"Walk {ed}m to {req.dest_name}",
            )
        )

        transit_legs = [s for s in segments if s.mode != "WALK"]
        jid = str(uuid.uuid4())
        route_out = JourneyRouteOut(
            journey_id=jid,
            badge_type=objective,
            origin_name=req.origin_name,
            dest_name=req.dest_name,
            departure_seconds=segments[0].departure_seconds,
            arrival_seconds=segments[-1].arrival_seconds,
            total_duration_minutes=max(1, (segments[-1].arrival_seconds - segments[0].departure_seconds) // 60),
            total_fare_inr=sum(s.fare_inr for s in transit_legs),
            transfer_count=max(0, len(transit_legs) - 1),
            total_walking_meters=total_walk,
            segments=segments,
        )
        self.journey_cache[jid] = route_out
        return route_out

    def search_multimodal(self, req: JourneySearchRequest, delay_by_route: Dict[str, int]) -> List[JourneyRouteOut]:
        results = []
        seen_sigs = set()
        for obj in ["FASTEST", "CHEAPEST", "BALANCED"]:
            r = self.search_single(req, obj, delay_by_route)
            if not r:
                continue
            sig = "|".join(f"{s.route_id}:{s.from_stop_id}->{s.to_stop_id}" for s in r.segments if s.mode != "WALK")
            if sig not in seen_sigs:
                seen_sigs.add(sig)
                results.append(r)
        return results
