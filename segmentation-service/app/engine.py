import hashlib
import math
from collections import Counter, defaultdict
from datetime import date, timedelta

RULE_VERSION = "rules-v1"
EXPORT_CROPS = {"citrus", "apple", "grape", "avocado"}


def distance_km(a, b):
    lat1, lat2 = map(math.radians, [a["latitude"], b["latitude"]])
    dlat = lat2 - lat1
    dlon = math.radians(b["longitude"] - a["longitude"])
    x = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371.0088 * 2 * math.asin(math.sqrt(min(1, x)))


def segment(profile):
    size = "Small" if profile["hectares"] < 5 else "Medium" if profile["hectares"] < 50 else "Large"
    return f"{size} {profile['crop']} growers, {profile['province']}, harvest month {profile['harvestMonth']}"


def services(profile, expense_trend=None):
    result = []
    if profile["hectares"] < 5 and not profile["businessRegistered"]:
        result.append({"service": "business_companion", "reason": "Small farm without recorded business registration"})
    if (profile["hectares"] >= 50 and profile["consistentProduction"]
            and profile["businessRegistered"] and profile["crop"] in EXPORT_CROPS):
        result.append({"service": "export_readiness", "reason": "Explore export prerequisites; this is not export certification"})
    if (profile["expenseConsent"] and expense_trend and expense_trend["previous"] > 0
            and expense_trend["current"] > expense_trend["previous"] * 1.2):
        result.append({"service": "expense_insights", "reason": "Recorded expenses rose over 20% between the last two complete months; not proof of input-price inflation"})
    return result


def experiment_arm(owner, enabled=False):
    return "exploration" if enabled and int(hashlib.sha256(owner.encode()).hexdigest()[:8], 16) % 2 else "control"


def matches(profile, records, owner, today=None, arm="control", exposure=None, limit=10):
    today = today or date.today()
    # Capacity is seasonal, so recommendations use the next harvest month rather than inventing ready stock.
    year = today.year + (profile["harvestMonth"] < today.month)
    harvest_start = date(year, profile["harvestMonth"], 1)
    following = date(year + (profile["harvestMonth"] == 12), profile["harvestMonth"] % 12 + 1, 1)
    harvest_end = following - timedelta(days=1)
    candidates = []
    for record in records:
        if record["ownerId"] == owner or record["kind"] != "demand" or record["crop"] != profile["crop"]:
            continue
        start, end = date.fromisoformat(record["availableFrom"]), date.fromisoformat(record["availableUntil"])
        km = distance_km(profile, record)
        if end < today or start > harvest_end or end < max(today, harvest_start):
            continue
        if km > profile["deliveryRadiusKm"] or record["quantityKg"] > profile["capacityKg"]:
            continue
        score = 0.6 * (1 - km / profile["deliveryRadiusKm"]) + 0.4 * record["quantityKg"] / profile["capacityKg"]
        candidates.append({**record, "distanceKm": round(km, 1), "score": round(score, 4),
                           "reasons": ["Same crop", "Within your delivery radius", "Harvest month overlaps buyer dates",
                                       "Requested volume is within your stated seasonal capacity"],
                           "notice": "Buyer request, not a confirmed order. Confirm stock, delivery and price directly."})
    candidates.sort(key=lambda r: (-r["score"], r["id"]))
    selected = candidates[:limit]
    # Reserve one position for a valid under-exposed request, not an irrelevant request.
    if arm == "exploration" and len(candidates) > limit:
        least_seen = min(candidates, key=lambda r: ((exposure or {}).get(r["id"], 0), r["id"]))
        if least_seen["id"] not in {r["id"] for r in selected}:
            selected[-1] = least_seen
    return selected


def demand_summary(records, searches, prices, today=None):
    today = today or date.today()
    groups = defaultdict(lambda: {"buyerRequests": 0, "requestedKg": 0, "offers": 0,
                                   "failedSearches": 0, "priceTotal": 0, "priceVolume": 0, "sources": set()})
    for r in records:
        if date.fromisoformat(r["availableUntil"]) < today:
            continue
        row = groups[(r["crop"], r["province"], int(r["availableFrom"][5:7]))]
        if r["kind"] == "demand":
            row["buyerRequests"] += 1
            row["requestedKg"] += r["quantityKg"]
        else:
            row["offers"] += 1
    for s in searches:
        if s["resultCount"] == 0 and s.get("kind", "offer") == "offer":
            groups[(s["crop"], s["province"], int(s["occurredOn"][5:7]))]["failedSearches"] += 1
    for p in prices:
        row = groups[(p["crop"], p["province"], int(p["observedOn"][5:7]))]
        row["priceTotal"] += p["pricePerKg"] * p["volumeKg"]
        row["priceVolume"] += p["volumeKg"]
        row["sources"].add(p["sourceUrl"])
    return [{"crop": crop, "province": province, "month": month,
             **{k: v for k, v in row.items() if k not in ("priceTotal", "priceVolume", "sources")},
             "observedPricePerKg": round(row["priceTotal"] / row["priceVolume"], 2) if row["priceVolume"] else None,
             "priceSources": sorted(row["sources"]), "priceBasis": "Volume-weighted historical market observations, not a current quote",
             "profitability": "Unknown: production, delivery and selling costs are not supplied"}
            for (crop, province, month), row in sorted(groups.items())]


def feature_rows(profiles):
    rows = []
    for p in profiles:
        rows.append({"crop": p["crop"], "province": p["province"], "hectares": p["hectares"],
                     "capacityKg": p["capacityKg"],
                     **{f"{key}_{axis}": fn(2 * math.pi * (p[key] - 1) / 12)
                        for key in ("plantingMonth", "harvestMonth")
                        for axis, fn in (("sin", math.sin), ("cos", math.cos))}})
    return rows


def cohort_fingerprint(profiles):
    import json
    fields = ("ownerId", "crop", "province", "hectares", "capacityKg", "plantingMonth", "harvestMonth", "latitude", "longitude")
    rows = sorted(({k: p[k] for k in fields} for p in profiles if p["analyticsConsent"]), key=lambda p: p["ownerId"])
    return hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()


def forecast_months(observations, horizon=3):
    import pandas as pd
    from statsmodels.tsa.holtwinters import ExponentialSmoothing
    if not observations:
        return {"method": "insufficient_history", "forecast": []}
    series = pd.Series({pd.Timestamp(row["month"]): row["quantityKg"] for row in observations}).sort_index()
    # Missing months are unknown, not zero demand. Two complete annual cycles are needed for seasonality.
    complete = series.reindex(pd.date_range(series.index.min(), series.index.max(), freq="MS"))
    if len(complete) < 24 or complete.isna().any():
        return {"method": "seasonal_averages", "forecast": [],
                "averages": {str(m): float(v) for m, v in series.groupby(series.index.month).mean().items()},
                "notice": "Forecast withheld until 24 consecutive observed months are available"}
    fit = ExponentialSmoothing(complete, trend="add", seasonal="add", seasonal_periods=12).fit()
    return {"method": "holt_winters", "forecast": [
        {"month": t.date().isoformat(), "quantityKg": max(0, round(float(v), 2))}
        for t, v in fit.forecast(horizon).items()], "notice": "Experimental forecast, not guaranteed buyer demand"}


def market_forecasts(prices):
    groups = defaultdict(lambda: defaultdict(float))
    for row in prices:
        groups[(row["crop"], row["province"], row["market"])][row["observedOn"][:7] + "-01"] += row["volumeKg"]
    return [{"crop": crop, "province": province, "market": market,
             **forecast_months([{"month": month, "quantityKg": qty} for month, qty in sorted(months.items())]),
             "basis": "Imported observed market volume, not total regional demand; collection coverage must be reviewed"}
            for (crop, province, market), months in sorted(groups.items())]
