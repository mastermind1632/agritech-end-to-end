import asyncio
import os
import threading
import time
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import UUID

import psycopg
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse

from app.engine import RULE_VERSION, demand_summary, experiment_arm, matches, segment, services
from app.models import CROPS, PROVINCES, FarmProfile, Feedback, MarketRecord
from app.outcomes import create_router
from app.readiness import model_readiness
from app.store import Store
from app.training import active_segment

store = Store()
rate_lock = threading.Lock()
rates = {}


@asynccontextmanager
async def lifespan(app):
    for attempt in range(60):
        try:
            await asyncio.to_thread(store.initialize)
            break
        except psycopg.Error:
            if attempt == 59:
                raise
            await asyncio.sleep(2)
    yield


app = FastAPI(title="AgriTech Market Intelligence", version="1.0.0", lifespan=lifespan)
app.include_router(create_router(store))


def identity(authorization: str = Header(default="")):
    import json
    if not authorization.startswith("Bearer ") or len(authorization) > 4096:
        raise HTTPException(401, "Sign in to access market intelligence")
    request = Request(os.getenv("AI_BACKEND_URL", "http://localhost:8080") + "/api/ai/identity",
                      headers={"Authorization": authorization})
    try:
        with urlopen(request, timeout=10) as response:
            owner = json.load(response)["farmerId"]
    except HTTPError as exc:
        raise HTTPException(exc.code if exc.code in (401, 403) else 503, "Identity verification unavailable") from exc
    except (URLError, TimeoutError, ConnectionError, KeyError, ValueError) as exc:
        raise HTTPException(503, "Identity verification unavailable") from exc
    with rate_lock:
        now = time.monotonic()
        for key in list(rates):
            if rates[key][0] < now - 60:
                del rates[key]
        start, count = rates.get(owner, (now, 0))
        if count >= 120:
            raise HTTPException(429, "Please wait a minute before requesting more market data")
        rates[owner] = (start, count + 1)
    return owner


def required_profile(owner):
    profile = store.profile(owner)
    if profile is None:
        raise HTTPException(409, "Complete your farm production profile first")
    return profile


def public_record(record):
    return {k: v for k, v in record.items() if k not in ("latitude", "longitude", "ownerId")}


@app.exception_handler(psycopg.Error)
async def database_error(request, exception):
    return JSONResponse(status_code=503, content={"detail": "Market data is temporarily unavailable"})


@app.get("/health")
def health():
    with store.connect() as conn:
        conn.execute("SELECT 1")
    return {"status": "UP"}


@app.get("/api/options")
def options(owner=Depends(identity)):
    return {"crops": CROPS, "provinces": PROVINCES}


@app.get("/api/profile")
def profile(owner=Depends(identity)):
    return {"profile": store.profile(owner)}


@app.put("/api/profile")
def save_profile(body: FarmProfile, owner=Depends(identity)):
    return {"profile": store.save_profile(owner, body.model_dump(mode="json"))}


@app.delete("/api/profile")
def delete_profile(owner=Depends(identity)):
    store.delete_profile(owner)
    return {"deleted": True}


@app.get("/api/records")
def records(crop: str | None = None, province: str | None = None, kind: str = "offer",
            mine: bool = False, owner=Depends(identity)):
    if kind not in ("offer", "demand") or (crop is not None and crop not in CROPS) or (province is not None and province not in PROVINCES):
        raise HTTPException(422, "Choose a supported crop, province and listing type")
    found = store.records(owner if mine else None, crop, province, kind)
    if crop and province and not mine:
        store.log_search(owner, {"crop": crop, "province": province, "kind": kind, "resultCount": len(found)})
    return {"records": [r if mine else public_record(r) for r in found[:100]],
            "resultCount": len(found), "limited": len(found) > 100}


@app.post("/api/records", status_code=201)
def add_record(body: MarketRecord, owner=Depends(identity)):
    if body.availableUntil < date.today():
        raise HTTPException(422, "The listing has already expired")
    try:
        return store.save_record(owner, body.model_dump(mode="json"))
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc


@app.delete("/api/records/{record_id}")
def remove_record(record_id: UUID, owner=Depends(identity)):
    if not store.delete_record(owner, record_id):
        raise HTTPException(404, "Your listing was not found")
    return {"deleted": True}


@app.get("/api/recommendations")
def recommendations(owner=Depends(identity)):
    p = required_profile(owner)
    profiles = store.profiles()
    learned = active_segment(p, profiles)
    readiness = model_readiness(profiles, p, learned, store.job("monthly_training"), store.job("worker_status"))
    cohort = learned or {"name": segment(p), "method": "rules", "version": RULE_VERSION,
                         "reviewStatus": readiness["message"]}
    arm = experiment_arm(owner, os.getenv("ENABLE_AB_TEST", "false").lower() == "true" and p["analyticsConsent"])
    found = matches(p, store.records(kind="demand", crop=p["crop"]), owner, arm=arm, exposure=store.exposure())
    impression = store.impression(owner, arm, cohort["version"], found) if p["analyticsConsent"] else None
    trend = store.expense_trend(owner) if p["expenseConsent"] else None
    return {"segment": cohort, "recommendations": [public_record(r) for r in found],
            "segmentationStatus": readiness,
            "productionContext": {key: p[key] for key in ("crop", "province", "district", "hectares", "capacityKg",
                "plantingMonth", "harvestMonth", "deliveryRadiusKm", "businessRegistered", "consistentProduction")},
            "services": services(p, trend), "impressionId": impression, "experimentArm": arm,
            "notice": "Independent matches; combined requests may exceed your seasonal capacity. No orders have been placed."}


@app.post("/api/feedback")
def feedback(body: Feedback, owner=Depends(identity)):
    try:
        UUID(body.impressionId)
        UUID(body.recordId)
    except ValueError as exc:
        raise HTTPException(422, "Invalid recommendation identifiers") from exc
    if not required_profile(owner)["analyticsConsent"]:
        raise HTTPException(409, "Recommendation tracking is disabled")
    if not store.feedback(owner, body.model_dump()):
        raise HTTPException(404, "Recommendation impression not found for your account")
    return {"recorded": True}


@app.get("/api/demand")
def demand(owner=Depends(identity)):
    published = store.latest_snapshot() or {}
    try:
        generated = datetime.fromisoformat(published["generatedAt"].replace("Z", "+00:00"))
        current = timedelta(0) <= datetime.now(timezone.utc) - generated <= timedelta(days=2)
        current = current and generated.astimezone(timezone(timedelta(hours=2))).date().replace(day=1) == date.today().replace(day=1)
    except (KeyError, ValueError, TypeError):
        current = False
    return {"rows": demand_summary(store.records(), store.searches(), store.prices(), order_outcomes=store.order_outcomes()),
            "forecasts": published.get("forecasts", []) if current else [],
            "forecastStatus": "published" if current else "pending_analytics",
            "pipeline": store.job("nightly_snapshot"),
            "notice": "Requests and failed searches are demand signals. Completed orders are recorded trusted marketplace events. Historical prices do not prove profit."}
