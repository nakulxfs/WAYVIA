from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import router
from app.database.db import init_db

app = FastAPI(
    title="CommuteBuddy Delhi NCR Transit API",
    version="1.0.0",
    description="Multimodal public transit routing, GTFS ingestion, awareness simulation, and saved routes API.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup_event() -> None:
    init_db()


@app.get("/health")
def health() -> dict:
    return {"status": "healthy", "scope": "Delhi NCR"}


app.include_router(router)
