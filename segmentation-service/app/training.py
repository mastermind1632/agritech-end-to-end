import json
import os
import pickle
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.cluster import HDBSCAN, KMeans
from sklearn.compose import ColumnTransformer
from sklearn.metrics import pairwise_distances, silhouette_score
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.engine import cohort_fingerprint, feature_rows, segment

NUMERIC = ["hectares", "capacityKg", "plantingMonth_sin", "plantingMonth_cos", "harvestMonth_sin", "harvestMonth_cos"]
CATEGORIES = ["crop", "province"]
MIN_PROFILES = 30
MIN_CLUSTERS = 2
MIN_CLUSTER_PROFILES = 5
MIN_SILHOUETTE = 0.1
MAX_AUTO_CLUSTERS = 6
TRAINING_POLICY_VERSION = "auto-k-v1"
MAX_SILHOUETTE_PROFILES = 2000
PREDICTION_RADIUS_MULTIPLIER = 1.5


def minimum_profiles():
    minimum = int(os.getenv("SEGMENTATION_MIN_PROFILES", str(MIN_PROFILES)))
    if minimum < MIN_CLUSTERS * MIN_CLUSTER_PROFILES:
        raise ValueError("SEGMENTATION_MIN_PROFILES must allow at least two groups of five farms")
    return minimum


def quality_failures(labels, score):
    counts = Counter(int(label) for label in labels if label >= 0)
    failures = []
    if len(counts) < MIN_CLUSTERS:
        failures.append("too_few_clusters")
    if counts and min(counts.values()) < MIN_CLUSTER_PROFILES:
        failures.append("cluster_too_small")
    if score is None:
        failures.append("silhouette_unavailable")
    elif score < MIN_SILHOUETTE:
        failures.append("clusters_not_well_separated")
    return failures


def cluster_silhouette(features, labels, metric="euclidean", gamma=None):
    """Use a bounded, reproducible sample and the dissimilarity fitted by the algorithm."""
    mask = labels >= 0
    features, labels = features[mask], labels[mask]
    if len(labels) > MAX_SILHOUETTE_PROFILES:
        sample = np.random.default_rng(42).choice(len(labels), MAX_SILHOUETTE_PROFILES, replace=False)
        features, labels = features[sample], labels[sample]
    if not 1 < len(np.unique(labels)) < len(labels):
        return None
    if metric == "kprototypes":
        numeric = features[:, :len(NUMERIC)].astype(float)
        categorical = features[:, len(NUMERIC):]
        # KPrototypes minimizes squared numerical distance plus gamma-weighted category mismatches.
        distances = pairwise_distances(numeric, metric="sqeuclidean")
        for column in range(categorical.shape[1]):
            values = categorical[:, column]
            distances += gamma * (values[:, None] != values[None, :])
        np.fill_diagonal(distances, 0)
        return float(silhouette_score(distances, labels, metric="precomputed"))
    return float(silhouette_score(features, labels, metric=metric))


def artifact_dir():
    root = Path(os.getenv("MODEL_DIR", "/data/models"))
    root.mkdir(parents=True, exist_ok=True)
    return root


def _assigned_distances(model, features, labels, algorithm):
    if algorithm == "kmeans":
        return np.linalg.norm(features - model.cluster_centers_[labels], axis=1)
    centroids = model.cluster_centroids_[labels]
    numeric = features[:, :len(NUMERIC)].astype(float)
    return (np.square(numeric - centroids[:, :len(NUMERIC)].astype(float)).sum(axis=1)
            + model.gamma * (features[:, len(NUMERIC):] != centroids[:, len(NUMERIC):]).sum(axis=1))


def _cohort_unchanged(payload, profiles):
    # Additions can use inference; removing or editing a training member requires retraining.
    if not isinstance(payload, dict) or not isinstance(payload.get("assignments"), dict) or not isinstance(payload.get("report"), dict):
        return False
    members = set(payload["assignments"])
    current = [p for p in profiles if p["analyticsConsent"] and p["ownerId"] in members]
    return (len(current) == len(members)
            and payload["report"].get("fingerprint") == cohort_fingerprint(current))


def artifact_versions():
    """Inspect artifact pairs without loading a fitted model on every worker poll."""
    root = artifact_dir()
    versions = {}
    for name in ("candidate", "active"):
        try:
            report = json.loads((root / f"{name}.json").read_text())
            versions[name] = report["version"] if (root / f"{name}.joblib").is_file() else None
        except (OSError, ValueError, KeyError, TypeError):
            versions[name] = None
    return versions


def train(profiles, algorithm="kmeans", k="auto", track=True):
    profiles = [p for p in profiles if p["analyticsConsent"]]
    minimum = minimum_profiles()
    if len(profiles) < minimum:
        return {"status": "cold_start", "profiles": len(profiles), "minimum": minimum,
                "trainingPolicyVersion": TRAINING_POLICY_VERSION, "requestedClusters": k,
                "fingerprint": cohort_fingerprint(profiles), "algorithm": algorithm, "qualityGate": False,
                "qualityFailures": ["insufficient_profiles"],
                "reviewStatus": f"Rule-based grouping until at least {minimum} consented profiles are available"}
    frame = pd.DataFrame(feature_rows(profiles))
    transform = ColumnTransformer([("numeric", StandardScaler(), NUMERIC),
                                   ("categorical", OneHotEncoder(handle_unknown="ignore", sparse_output=False), CATEGORIES)])
    features = transform.fit_transform(frame)
    if algorithm == "kprototypes":
        from kmodes.kprototypes import KPrototypes
        mixed = np.column_stack((transform.named_transformers_["numeric"].transform(frame[NUMERIC]), frame[CATEGORIES].to_numpy()))
    selection = []
    if algorithm in ("kmeans", "kprototypes"):
        distinct = len(frame.drop_duplicates())
        maximum = min(MAX_AUTO_CLUSTERS, len(profiles) // MIN_CLUSTER_PROFILES, distinct)
        if distinct < MIN_CLUSTERS:
            return {"status": "insufficient_variation", "profiles": len(profiles), "minimum": minimum,
                    "trainingPolicyVersion": TRAINING_POLICY_VERSION, "requestedClusters": k,
                    "fingerprint": cohort_fingerprint(profiles), "algorithm": algorithm, "qualityGate": False,
                    "qualityFailures": ["insufficient_variation"], "silhouette": None,
                    "reviewStatus": "At least two distinct production profiles are needed before clustering"}
        choices = range(MIN_CLUSTERS, maximum + 1) if k == "auto" else [int(k)]
        best = None
        for clusters in choices:
            if clusters < MIN_CLUSTERS or clusters > distinct or clusters > len(profiles):
                raise ValueError("Choose at least two clusters, no more than the distinct production profiles")
            try:
                if algorithm == "kmeans":
                    candidate_model = KMeans(n_clusters=clusters, random_state=42, n_init=10)
                    candidate_features = features
                    candidate_labels = candidate_model.fit_predict(candidate_features)
                    candidate_score = cluster_silhouette(candidate_features, candidate_labels)
                else:
                    candidate_model = KPrototypes(n_clusters=clusters, init="Cao", n_init=5, random_state=42)
                    candidate_features = mixed
                    candidate_labels = candidate_model.fit_predict(mixed, categorical=[len(NUMERIC), len(NUMERIC) + 1])
                    candidate_score = cluster_silhouette(mixed, candidate_labels, "kprototypes", candidate_model.gamma)
            except ValueError:
                # A candidate count can be impossible to initialize even when smaller counts fit.
                selection.append({"clusters": clusters, "silhouette": None, "qualityGate": False,
                                  "qualityFailures": ["fit_failed"]})
                continue
            failures = quality_failures(candidate_labels, candidate_score)
            selection.append({"clusters": clusters, "silhouette": candidate_score,
                              "qualityGate": not failures, "qualityFailures": failures})
            rank = (not failures, candidate_score if candidate_score is not None else -1, -clusters)
            if best is None or rank > best[0]:
                best = (rank, candidate_model, candidate_labels, candidate_score)
        if best is None:
            raise ValueError("None of the tested cluster counts could be fitted; inspect the consenting training data")
        _, model, labels, score = best
    elif algorithm == "hdbscan":
        model = HDBSCAN(min_cluster_size=5, metric="haversine")
        labels = model.fit_predict(np.radians([[p["latitude"], p["longitude"]] for p in profiles]))
        score = cluster_silhouette(np.radians([[p["latitude"], p["longitude"]] for p in profiles]), labels, "haversine")
    else:
        raise ValueError("Choose kmeans, kprototypes or hdbscan")
    counts = Counter(int(x) for x in labels if x >= 0)
    evaluation = (np.radians([[p["latitude"], p["longitude"]] for p in profiles]) if algorithm == "hdbscan"
                  else mixed if algorithm == "kprototypes" else features)
    metric = {"hdbscan": "haversine", "kprototypes": "kprototypes"}.get(algorithm, "euclidean")
    failures = quality_failures(labels, score)
    quality = not failures
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
              "minimum": minimum, "trainingPolicyVersion": TRAINING_POLICY_VERSION, "requestedClusters": k,
              "silhouette": score, "clusterSizes": {str(c): n for c, n in counts.items()},
              "silhouetteMetric": "squared_euclidean_plus_gamma_matching" if algorithm == "kprototypes" else metric,
              "silhouetteSampleSize": min(int((labels >= 0).sum()), MAX_SILHOUETTE_PROFILES),
              "noiseProfiles": int((labels < 0).sum()), "qualityGate": quality,
              "qualityFailures": failures, "selectedClusters": len(counts),
              "clusterSelection": {"method": "best_valid_silhouette" if k == "auto" else "requested",
                                   "tested": selection} if selection else {"method": "density"},
              "reviewStatus": "Agronomist/extension-officer review required", "segmentNames": names,
              "fingerprint": cohort_fingerprint(profiles),
              "baseline": {key: {"mean": float(frame[key].mean()), "std": float(frame[key].std())} for key in NUMERIC},
              "cropDistribution": dict(Counter(p["crop"] for p in profiles))}
    prediction_limits = {}
    if algorithm in ("kmeans", "kprototypes"):
        distances = _assigned_distances(model, evaluation, labels, algorithm)
        minimum = 0.25 if algorithm == "kmeans" else 0.25 ** 2
        prediction_limits = {str(label): max(minimum, float(np.quantile(distances[labels == label], 0.99))
                                             * PREDICTION_RADIUS_MULTIPLIER) for label in counts}
        report["predictionPolicy"] = "Known crop/province and within the reviewed cluster's empirical distance limit"
    else:
        report["predictionPolicy"] = "Training cohort only; unseen geographic profiles use rules"
    root = artifact_dir()
    payload = {"report": report, "model": model, "transform": transform,
               "predictionLimits": prediction_limits,
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
    try:
        payload = joblib.load(root / "candidate.joblib")
    except FileNotFoundError as exc:
        raise ValueError("No trained candidate is available; train and review a candidate first") from exc
    report = payload["report"]
    if not report["qualityGate"] or not _cohort_unchanged(payload, profiles):
        raise ValueError("Candidate failed quality checks or the consented cohort changed; retrain")
    report["status"] = "active"
    report["reviewStatus"] = "Reviewed by " + reviewed_by
    path = root / "active.tmp"
    joblib.dump(payload, path)
    path.replace(root / "active.joblib")
    (root / "active.json").write_text(json.dumps(report, indent=2))
    return report


def _active_payload(profiles):
    path = Path(os.getenv("MODEL_DIR", "/data/models")) / "active.joblib"
    try:
        payload = joblib.load(path)
    except (OSError, EOFError, ValueError, pickle.UnpicklingError):
        # Missing/damaged artifacts and a concurrent revocation both fall back to rules.
        return None
    if not _cohort_unchanged(payload, profiles):
        return None
    if not payload["report"].get("qualityGate") or not payload["report"].get("reviewStatus", "").startswith("Reviewed by "):
        return None
    return payload


def _profile_segment(payload, profile):
    report = payload["report"]
    label = payload["assignments"].get(profile["ownerId"])
    assignment = "training_cohort"
    if label is None:
        if report["algorithm"] not in ("kmeans", "kprototypes") or not payload.get("predictionLimits"):
            return None
        transform = payload["transform"]
        categories = transform.named_transformers_["categorical"].categories_
        if any(profile[key] not in known for key, known in zip(CATEGORIES, categories)):
            return None
        frame = pd.DataFrame(feature_rows([profile]))
        if report["algorithm"] == "kmeans":
            features = transform.transform(frame)
            labels = payload["model"].predict(features)
        else:
            features = np.column_stack((transform.named_transformers_["numeric"].transform(frame[NUMERIC]),
                                        frame[CATEGORIES].to_numpy()))
            labels = payload["model"].predict(features, categorical=[len(NUMERIC), len(NUMERIC) + 1])
        label = int(labels[0])
        distance = float(_assigned_distances(payload["model"], features, labels, report["algorithm"])[0])
        if not np.isfinite(distance) or distance > payload["predictionLimits"].get(str(label), -1):
            return None
        assignment = "predicted"
    if label < 0:
        return None
    return {"name": report["segmentNames"][str(label)], "method": report["algorithm"], "version": report["version"],
            "reviewStatus": report["reviewStatus"], "segmentId": str(label), "assignment": assignment}


def active_segment(profile, profiles):
    if not profile["analyticsConsent"]:
        return None
    current = next((p for p in profiles if p["ownerId"] == profile["ownerId"] and p["analyticsConsent"]), None)
    if current is None or cohort_fingerprint([current]) != cohort_fingerprint([profile]):
        return None
    payload = _active_payload(profiles)
    return _profile_segment(payload, profile) if payload else None


def active_segments(profiles):
    """Resolve a consented snapshot with one artifact read and cohort validation."""
    payload = _active_payload(profiles)
    if payload is None:
        return {}
    result = {}
    for profile in profiles:
        if profile["analyticsConsent"]:
            learned = _profile_segment(payload, profile)
            if learned:
                result[profile["ownerId"]] = learned
    return result


def invalidate_artifacts():
    root = artifact_dir()
    for name in ("active.joblib", "active.json", "candidate.joblib", "candidate.json"):
        (root / name).unlink(missing_ok=True)


def drift(profiles):
    profiles = [p for p in profiles if p["analyticsConsent"]]
    path = artifact_dir() / "active.json"
    if not path.exists() or len(profiles) < minimum_profiles():
        return {"status": "insufficient_baseline"}
    try:
        report = json.loads(path.read_text())
    except (OSError, ValueError):
        return {"status": "insufficient_baseline"}
    frame = pd.DataFrame(feature_rows(profiles))
    shifts = {key: abs(float(frame[key].mean()) - stats["mean"]) / max(stats["std"], 0.000001)
              for key, stats in report["baseline"].items()}
    old = report["cropDistribution"]
    new = Counter(p["crop"] for p in profiles)
    crop_shift = sum(abs(new[c] / len(profiles) - old.get(c, 0) / report["profiles"]) for c in set(old) | set(new)) / 2
    return {"status": "review_required" if max(shifts.values()) > 0.5 or crop_shift > 0.2 else "stable",
            "standardizedMeanShifts": shifts, "cropDistributionShift": crop_shift}
