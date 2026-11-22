import sqlite3
import uuid
from pathlib import Path
from typing import Dict, List

DB_PATH = Path(__file__).resolve().parent.parent.parent / "commutebuddy.sqlite3"


def get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with get_conn() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS saved_routes (
                route_id TEXT PRIMARY KEY,
                custom_name TEXT NOT NULL,
                origin_name TEXT NOT NULL,
                origin_lat REAL NOT NULL,
                origin_lon REAL NOT NULL,
                dest_name TEXT NOT NULL,
                dest_lat REAL NOT NULL,
                dest_lon REAL NOT NULL,
                duration_minutes INTEGER NOT NULL,
                fare_inr INTEGER NOT NULL,
                transfer_count INTEGER NOT NULL,
                modes_summary TEXT NOT NULL
            )
            """
        )


def insert_saved_route(payload: Dict) -> Dict:
    route_id = str(uuid.uuid4())
    with get_conn() as conn:
        conn.execute(
            """
            INSERT INTO saved_routes (
                route_id, custom_name, origin_name, origin_lat, origin_lon,
                dest_name, dest_lat, dest_lon, duration_minutes, fare_inr,
                transfer_count, modes_summary
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                route_id,
                payload["custom_name"],
                payload["origin_name"],
                payload["origin_lat"],
                payload["origin_lon"],
                payload["dest_name"],
                payload["dest_lat"],
                payload["dest_lon"],
                payload["duration_minutes"],
                payload["fare_inr"],
                payload["transfer_count"],
                payload["modes_summary"],
            ),
        )
    return {"route_id": route_id, **payload}


def list_saved_routes() -> List[Dict]:
    with get_conn() as conn:
        rows = conn.execute("SELECT * FROM saved_routes ORDER BY rowid DESC").fetchall()
        return [dict(r) for r in rows]


def delete_saved_route(route_id: str) -> bool:
    with get_conn() as conn:
        cur = conn.execute("DELETE FROM saved_routes WHERE route_id = ?", (route_id,))
        return cur.rowcount > 0
