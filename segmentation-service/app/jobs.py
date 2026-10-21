import argparse
import csv
import json
import logging
import time
from collections import Counter
from datetime import datetime, timezone

from app.engine import RULE_VERSION, cohort_fingerprint, demand_summary, market_forecasts, segment
from app.models import MarketPrice
from app.store import Store
from app.training import active_segments, artifact_versions, drift, promote, train

logger = logging.getLogger(__name__)


def _utc(value):
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def snapshot(store):
    def build():
        profiles = [p for p in store.profiles() if p["analyticsConsent"]]
        learned = active_segments(profiles)
        counts = Counter()
        descriptions = {}
        for profile in profiles:
            group = learned.get(profile["ownerId"]) or {"name": segment(profile), "method": "rules",
                "version": RULE_VERSION, "reviewStatus": "Rule-based grouping; no reviewed assignment is available"}
            key = (group["method"], group["version"], group.get("segmentId", group["name"]))
            counts[key] += 1
            descriptions[key] = group
        # Suppress small cohorts in shared analytics; individual recommendations still work.
        groups = [{**{key: value for key, value in descriptions[group].items() if key != "assignment"}, "farmers": count}
                  for group, count in sorted(counts.items()) if count >= 5]
        return {"segments": groups, "suppressedSmallSegments": sum(count < 5 for count in counts.values()),
                "segmentation": {"learnedProfiles": len(learned), "ruleProfiles": len(profiles) - len(learned)},
                "demand": demand_summary(store.records(), store.searches(), store.prices(), order_outcomes=store.order_outcomes()),
                "forecasts": market_forecasts(store.prices()),
                "experiments": store.experiment_report(), "drift": drift(profiles),
                "generatedAt": datetime.now(timezone.utc).isoformat()}
    result = store.publish_snapshot(build)
    store.job("nightly_snapshot", {"segments": len(result["segments"]), "demandRows": len(result["demand"])})
    return result


def training_status(report, current):
    fields = ("status", "profiles", "minimum", "version", "algorithm", "qualityGate", "reviewStatus", "fingerprint")
    return {**{key: report[key] for key in fields if key in report}, "trainingPeriod": _utc(current).strftime("%Y-%m")}


def _training_cycle(store, current):
    def build(profiles):
        previous = store.job("monthly_training")
        last = previous["data"] if previous else {}
        versions = artifact_versions()
        monitor = store.job("model_monitor")
        monitored = monitor["data"] if monitor else {}
        reasons = []
        period = last.get("trainingPeriod") or (_utc(previous["completedAt"]).strftime("%Y-%m") if previous else None)
        if period != current.strftime("%Y-%m"):
            reasons.append("monthly_schedule")
        if last.get("fingerprint") != cohort_fingerprint(profiles):
            reasons.append("cohort_changed")
        if last.get("status") == "candidate" and versions["candidate"] is None:
            reasons.append("candidate_missing")
        if monitored.get("active") and versions["active"] is None:
            reasons.append("active_missing")
        result = {"status": "up_to_date"}
        if reasons:
            algorithm = last.get("algorithm", "kmeans")
            report = train(profiles, algorithm=algorithm)
            result = {**training_status(report, current), "trigger": reasons}
            store.job("monthly_training", result)
            versions = artifact_versions()
        if not monitor or versions != monitored:
            store.job("model_monitor", versions)
        return result
    return store.with_cohort_lock(build)


def run_cycle(store, current=None):
    current = _utc(current or datetime.now(timezone.utc))
    result = {}
    try:
        previous = store.job("nightly_snapshot")
        if (not previous or _utc(previous["completedAt"]).date() != current.date()
                or store.latest_snapshot() is None):
            snapshot(store)
            result["snapshot"] = "completed"
        else:
            result["snapshot"] = "up_to_date"
    except Exception:
        logger.exception("Market intelligence snapshot failed; will retry")
        result["snapshot"] = "failed"
    try:
        result["training"] = _training_cycle(store, current)
    except Exception:
        logger.exception("Market intelligence training failed; will retry")
        result["training"] = {"status": "failed"}
    return result


def run_worker(store):
    while True:
        run_cycle(store)
        time.sleep(300)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["snapshot", "train", "promote", "import-prices", "experiments", "worker"])
    parser.add_argument("--algorithm", choices=["kmeans", "kprototypes", "hdbscan"], default="kmeans")
    parser.add_argument("--reviewed-by", default="")
    parser.add_argument("--csv")
    args = parser.parse_args()
    store = Store()
    for attempt in range(60):
        try:
            store.initialize()
            break
        except Exception:
            if attempt == 59:
                raise
            time.sleep(2)
    if args.command == "worker":
        run_worker(store)
        return
    if args.command == "snapshot":
        result = snapshot(store)
    elif args.command == "train":
        result = store.with_cohort_lock(lambda profiles: train(profiles, args.algorithm))
        store.job("monthly_training", training_status(result, datetime.now(timezone.utc)))
        store.job("model_monitor", artifact_versions())
    elif args.command == "promote":
        result = store.with_cohort_lock(lambda profiles: promote(args.reviewed_by, profiles))
        previous = store.job("monthly_training")
        status = training_status(result, datetime.now(timezone.utc))
        if previous and previous["data"].get("version") == result["version"]:
            status["trainingPeriod"] = previous["data"].get("trainingPeriod") or _utc(previous["completedAt"]).strftime("%Y-%m")
        store.job("monthly_training", status)
        store.job("model_monitor", artifact_versions())
    elif args.command == "experiments":
        result = store.experiment_report()
    else:
        if not args.csv:
            parser.error("--csv is required")
        with open(args.csv, newline="", encoding="utf-8-sig") as file:
            prices = [MarketPrice.model_validate(row).model_dump(mode="json") for row in csv.DictReader(file)]
        store.import_prices(prices)
        result = {"imported": len(prices)}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
