import argparse
import csv
import json
import logging
import time
from collections import Counter
from datetime import datetime, timezone

from app.engine import demand_summary, market_forecasts, segment
from app.models import MarketPrice
from app.store import Store
from app.training import drift, promote, train

logger = logging.getLogger(__name__)


def snapshot(store):
    def build():
        profiles = store.profiles()
        counts = Counter(segment(p) for p in profiles)
        # Suppress small cohorts in shared analytics; individual recommendations still work.
        groups = [{"name": name, "farmers": count} for name, count in counts.items() if count >= 5]
        return {"segments": groups, "suppressedSmallSegments": sum(count < 5 for count in counts.values()),
                "demand": demand_summary(store.records(), store.searches(), store.prices()),
                "forecasts": market_forecasts(store.prices()),
                "experiments": store.experiment_report(), "drift": drift(profiles),
                "generatedAt": datetime.now(timezone.utc).isoformat()}
    result = store.publish_snapshot(build)
    store.job("nightly_snapshot", {"segments": len(result["segments"]), "demandRows": len(result["demand"])})
    return result


def run_worker(store):
    while True:
        try:
            current = datetime.now(timezone.utc)
            previous = store.job("nightly_snapshot")
            if not previous or previous["completedAt"][:10] != current.date().isoformat():
                snapshot(store)
            trained = store.job("monthly_training")
            if not trained or trained["completedAt"][:7] != current.strftime("%Y-%m"):
                report = store.with_cohort_lock(train)
                store.job("monthly_training", {k: report[k] for k in ("status", "profiles")})
        except Exception:
            logger.exception("Market intelligence job failed; will retry")
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
    elif args.command == "promote":
        result = store.with_cohort_lock(lambda profiles: promote(args.reviewed_by, profiles))
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
