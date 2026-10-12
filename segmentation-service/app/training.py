import json
import os
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.cluster import HDBSCAN, KMeans
from sklearn.compose import ColumnTransformer
from sklearn.metrics import silhouette_score
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.engine import cohort_fingerprint, feature_rows, segment

NUMERIC = ["hectares", "capacityKg", "plantingMonth_sin", "plantingMonth_cos", "harvestMonth_sin", "harvestMonth_cos"]
CATEGORIES = ["crop", "province"]
MIN_PROFILES = 30


def artifact_dir():
    root = Path(os.getenv("MODEL_DIR", "/data/models"))
    root.mkdir(parents=True, exist_ok=True)
    return root


def train(profiles, algorithm="kmeans", k=3, track=True):
    profiles = [p for p in profiles if p["analyticsConsent"]]
    if len(profiles) < MIN_PROFILES:
        return {"status": "cold_start", "profiles": len(profiles), "minimum": MIN_PROFILES}
    frame = pd.DataFrame(feature_rows(profiles))
    transform = ColumnTransformer([("numeric", StandardScaler(), NUMERIC),
                                   ("categorical", OneHotEncoder(handle_unknown="ignore", sparse_output=False), CATEGORIES)])
    features = transform.fit_transform(frame)
    if algorithm == "kmeans":
        model = KMeans(n_clusters=k, random_state=42, n_init=10)
        labels = model.fit_predict(features)
    elif algorithm == "kprototypes":
        from kmodes.kprototypes import KPrototypes
        mixed = np.column_stack((StandardScaler().fit_transform(frame[NUMERIC]), frame[CATEGORIES].to_numpy()))
        model = KPrototypes(n_clusters=k, init="Cao", n_init=5, random_state=42)
        labels = model.fit_predict(mixed, categorical=[len(NUMERIC), len(NUMERIC) + 1])
    elif algorithm == "hdbscan":
        model = HDBSCAN(min_cluster_size=5, metric="haversine")
        labels = model.fit_predict(np.radians([[p["latitude"], p["longitude"]] for p in profiles]))
    else:
        raise ValueError("Choose kmeans, kprototypes or hdbscan")
    counts = Counter(int(x) for x in labels if x >= 0)
    mask = labels >= 0
    evaluation = np.radians([[p["latitude"], p["longitude"]] for p in profiles]) if algorithm == "hdbscan" else features
    metric = "haversine" if algorithm == "hdbscan" else "euclidean"
    score = float(silhouette_score(evaluation[mask], labels[mask], metric=metric)) if 1 < len(counts) < int(mask.sum()) else None
    quality = len(counts) >= 2 and min(counts.values()) >= 5 and score is not None and score >= 0.1
    names = {}
    for label in counts:
        members = [p for p, assignment in zip(profiles, labels) if assignment == label]
        typical = dict(members[0])
        typical["crop"] = Counter(p["crop"] for p in members).most_common(1)[0][0]
        typical["province"] = Counter(p["province"] for p in members).most_common(1)[0][0]
        typical["hectares"] = float(np.median([p["hectares"] for p in members]))
        typical["harvestMonth"] = Counter(p["harvestMonth"] for p in members).most_common(1)[0][0]
        names[str(label)] = segment(typical)
    version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + algorithm
    report = {"status": "candidate", "version": version, "algorithm": algorithm, "profiles": len(profiles),
              "silhouette": score, "clusterSizes": {str(c): n for c, n in counts.items()},
              "noiseProfiles": int((labels < 0).sum()), "qualityGate": quality,
              "reviewStatus": "Agronomist/extension-officer review required", "segmentNames": names,
              "fingerprint": cohort_fingerprint(profiles),
              "baseline": {key: {"mean": float(frame[key].mean()), "std": float(frame[key].std())} for key in NUMERIC},
              "cropDistribution": dict(Counter(p["crop"] for p in profiles))}
    root = artifact_dir()
    payload = {"report": report, "model": model, "transform": transform,
               "assignments": {p["ownerId"]: int(label) for p, label in zip(profiles, labels)}}
    temporary = root / "candidate.tmp"
    joblib.dump(payload, temporary)
    temporary.replace(root / "candidate.joblib")
    (root / "candidate.json").write_text(json.dumps(report, indent=2))
    if track:
        import mlflow
        mlflow.set_tracking_uri(os.getenv("MLFLOW_TRACKING_URI", "sqlite:////data/mlflow.db"))
        if mlflow.get_experiment_by_name("agritech-market-segmentation") is None:
            artifacts = (root.parent / "mlruns").resolve()
            artifacts.mkdir(parents=True, exist_ok=True)
            mlflow.create_experiment("agritech-market-segmentation", artifact_location=artifacts.as_uri())
        mlflow.set_experiment("agritech-market-segmentation")
        with mlflow.start_run(run_name=version):
            mlflow.log_params({"algorithm": algorithm, "profiles": len(profiles), "clusters": len(counts), "version": version})
            if score is not None:
                mlflow.log_metric("silhouette", score)
            mlflow.log_metric("noise_profiles", report["noiseProfiles"])
            mlflow.set_tag("review_status", "pending")
            # Do not upload farmer identifiers or precise locations to an external tracker.
            mlflow.log_dict({k: v for k, v in report.items() if k != "fingerprint"}, "evaluation.json")
    return report


def promote(reviewed_by, profiles):
    if not reviewed_by.strip():
        raise ValueError("Record the human reviewer")
    root = artifact_dir()
    payload = joblib.load(root / "candidate.joblib")
    report = payload["report"]
    if not report["qualityGate"] or report["fingerprint"] != cohort_fingerprint(profiles):
        raise ValueError("Candidate failed quality checks or the consented cohort changed; retrain")
    report["reviewStatus"] = "Reviewed by " + reviewed_by
    path = root / "active.tmp"
    joblib.dump(payload, path)
    path.replace(root / "active.joblib")
    (root / "active.json").write_text(json.dumps(report, indent=2))
    return report


def active_segment(profile, profiles):
    path = artifact_dir() / "active.joblib"
    if not profile["analyticsConsent"] or not path.exists():
        return None
    try:
        payload = joblib.load(path)
    except FileNotFoundError:
        # Revocation can remove the artifact between the existence check and the read.
        return None
    report = payload["report"]
    if report["fingerprint"] != cohort_fingerprint(profiles):
        return None
    label = payload["assignments"].get(profile["ownerId"], -1)
    if label < 0:
        return None
    return {"name": report["segmentNames"][str(label)], "method": report["algorithm"], "version": report["version"],
            "reviewStatus": report["reviewStatus"]}


def invalidate_artifacts():
    root = artifact_dir()
    for name in ("active.joblib", "active.json", "candidate.joblib", "candidate.json"):
        (root / name).unlink(missing_ok=True)


def drift(profiles):
    path = artifact_dir() / "active.json"
    if not path.exists() or len(profiles) < MIN_PROFILES:
        return {"status": "insufficient_baseline"}
    report = json.loads(path.read_text())
    frame = pd.DataFrame(feature_rows(profiles))
    shifts = {key: abs(float(frame[key].mean()) - stats["mean"]) / max(stats["std"], 0.000001)
              for key, stats in report["baseline"].items()}
    old = report["cropDistribution"]
    new = Counter(p["crop"] for p in profiles)
    crop_shift = sum(abs(new[c] / len(profiles) - old.get(c, 0) / report["profiles"]) for c in set(old) | set(new)) / 2
    return {"status": "review_required" if max(shifts.values()) > 0.5 or crop_shift > 0.2 else "stable",
            "standardizedMeanShifts": shifts, "cropDistributionShift": crop_shift}
