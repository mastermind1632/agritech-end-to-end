"""Public model readiness without farmer identifiers or private training features."""
import json
import os
import pickle
from datetime import datetime, timezone
from pathlib import Path

import joblib

from app.engine import cohort_fingerprint, feature_rows
from app.training import (CATEGORIES, MIN_CLUSTERS, MIN_CLUSTER_PROFILES, MIN_SILHOUETTE,
                          _cohort_unchanged, minimum_profiles)

WORKER_STALE_SECONDS = 900


def _artifact(name):
    path = Path(os.getenv("MODEL_DIR", "/data/models")) / f"{name}.joblib"
    try:
        payload = joblib.load(path)
    except FileNotFoundError:
        return None, "missing"
    except (OSError, EOFError, ValueError, pickle.UnpicklingError, ImportError, AttributeError):
        return None, "unreadable"
    if not isinstance(payload, dict) or not isinstance(payload.get("report"), dict) or not isinstance(payload.get("assignments"), dict):
        return None, "unreadable"
    return payload, None


def _report(payload):
    allowed = ("algorithm", "version", "qualityGate", "qualityFailures", "selectedClusters", "silhouette", "reviewStatus")
    return {key: payload["report"][key] for key in allowed if key in payload["report"]}


def _timestamp(value):
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed.astimezone(timezone.utc)
    except (TypeError, ValueError):
        return None


def model_readiness(profiles, profile=None, learned=None, training_job=None, worker_job=None):
    profiles = [p for p in profiles if p["analyticsConsent"]]
    training = (training_job or {}).get("data", {})
    worker = (worker_job or {}).get("data", {})
    last_worker = _timestamp((worker_job or {}).get("completedAt"))
    worker_status = "unknown"
    if last_worker:
        if worker.get("trainingStatus") == "failed" or worker.get("snapshotStatus") == "failed":
            worker_status = "failed"
        elif (datetime.now(timezone.utc) - last_worker).total_seconds() > WORKER_STALE_SECONDS:
            worker_status = "stale"
        else:
            worker_status = "healthy"
    minimum = minimum_profiles()
    result = {"code": "training_pending", "message": "", "nextAction": "",
              "consentedProfiles": len(profiles), "minimumProfiles": minimum,
              "missingProfiles": max(0, minimum - len(profiles)),
              "trainingStatus": training.get("status", "not_started"),
              "lastTrainingAt": (training_job or {}).get("completedAt"),
              "workerStatus": worker_status, "lastWorkerAt": (worker_job or {}).get("completedAt"),
              "reviewRequired": True, "automaticActivation": False,
              "qualityRequirements": {"minimumClusters": MIN_CLUSTERS,
                                      "minimumClusterProfiles": MIN_CLUSTER_PROFILES,
                                      "minimumSilhouette": MIN_SILHOUETTE}}

    def explain(code, message, action):
        result.update(code=code, message=message, nextAction=action)
        return result

    if profile is not None and not profile["analyticsConsent"]:
        return explain("analytics_consent_required", "Your analytics consent is off, so your farm uses profile-based grouping.",
                       "Buyer matching still works. Enable analytics consent only if you want to join shared model training.")
    if learned:
        result["activeModel"] = {key: learned[key] for key in ("version", "reviewStatus") if key in learned}
        result["activeModel"]["algorithm"] = learned.get("method")
        return explain("active", "Your farm belongs to a reviewed machine-learning segment.",
                       "Keep your production profile current. Buyer matches still check crop, dates, distance and capacity.")
    if len(profiles) < minimum:
        return explain("insufficient_profiles",
                       f"{len(profiles)} consenting production {'profile is' if len(profiles) == 1 else 'profiles are'} available; the current training policy requires {minimum}.",
                       "Use profile-based buyer matches while more real farms choose to join model training.")
    if profile is not None:
        current = next((p for p in profiles if p["ownerId"] == profile["ownerId"]), None)
        if current is None or cohort_fingerprint([current]) != cohort_fingerprint([profile]):
            return explain("profile_changed", "Your saved production profile changed while its segment was checked.",
                           "Refresh the page to use your current profile.")

    active, active_error = _artifact("active")
    candidate, candidate_error = _artifact("candidate")
    if active:
        result["activeModel"] = _report(active)
        if (_cohort_unchanged(active, profiles) and active["report"].get("qualityGate")
                and active["report"].get("reviewStatus", "").startswith("Reviewed by ")):
            if profile is None:
                return explain("active", "A reviewed model is available for the current training cohort.",
                               "New supported profiles can use the saved model; unsupported profiles retain rule-based matching.")
            label = active["assignments"].get(profile["ownerId"])
            if label is not None and label < 0:
                return explain("noise_profile", "The geographic model did not assign your farm to a supported group.",
                               "Keep using profile-based matches; a reviewer can reassess grouping after retraining.")
            if active["report"].get("algorithm") == "hdbscan" or not active.get("predictionLimits"):
                return explain("unsupported_profile", "The reviewed model cannot assign a new profile without retraining.",
                               "Keep using profile-based matches while a new candidate is trained and reviewed.")
            categories = active["transform"].named_transformers_["categorical"].categories_
            if any(profile[key] not in known for key, known in zip(CATEGORIES, categories)):
                return explain("unknown_category", "Your crop or province is not represented in the reviewed model.",
                               "Use profile-based matches until representative profiles are included in a reviewed model.")
            return explain("outlier_profile", "Your farm is outside the reviewed model's supported production ranges.",
                           "Check your profile values. Profile-based matches remain available while the model is reviewed.")

    if candidate and _cohort_unchanged(candidate, profiles):
        result["candidate"] = _report(candidate)
        if not candidate["report"].get("qualityGate"):
            return explain("quality_checks_failed", "A model was trained, but its farm groups did not pass validation.",
                           "A platform reviewer must inspect the candidate or collect more representative consenting profiles.")
        return explain("review_pending", "A candidate model passed numerical checks and is waiting for human review.",
                       "An agronomist or extension officer must review and approve it before ML segments are shown.")
    if active or candidate:
        return explain("cohort_changed", "The original training profiles changed, so the previous model is not being used.",
                       "The background worker must train a fresh candidate, followed by human review.")
    if active_error == "unreadable" or candidate_error == "unreadable":
        return explain("artifact_unavailable", "A saved model could not be read; profile-based matching is being used.",
                       "A platform operator must inspect model storage and rebuild the candidate before review.")
    distinct = len({json.dumps(row, sort_keys=True) for row in feature_rows(profiles)})
    if distinct < MIN_CLUSTERS:
        return explain("insufficient_variation", "There are enough consenting profiles, but their production features do not form distinct groups.",
                       "Collect representative production profiles rather than duplicating farms or forcing extra clusters.")
    if worker.get("trainingStatus") == "failed" or training.get("status") == "training_failed":
        return explain("training_failed", "The latest model-training attempt failed; this is separate from having too few farms.",
                       "A platform operator must check the worker and its dependencies, then rerun training.")
    if worker_status == "stale":
        return explain("worker_stale", "The background worker has not reported recently and no reviewed model is ready.",
                       "A platform operator must check and restart the analytics worker if needed.")
    return explain("training_pending", "The profiles are ready for training, but no reviewed model has been activated.",
                   "The analytics worker must train a candidate and an agronomist must review it.")
