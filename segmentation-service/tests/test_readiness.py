import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import MagicMock, patch

from app.diagnostics import diagnose
from app.readiness import model_readiness
from app.training import active_segment, promote, train
from test_engine import profile


class ReadinessTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.env = patch.dict(os.environ, {"MODEL_DIR": self.directory.name, "SEGMENTATION_MIN_PROFILES": "30"})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.profiles = [profile(f"private-farmer-{i}", crop="tomato" if i < 15 else "maize",
                                 hectares=2 if i < 15 else 100, capacityKg=1000 if i < 15 else 100000)
                         for i in range(30)]

    def test_small_realistic_cohort_reports_policy_without_exposing_profiles(self):
        result = model_readiness(self.profiles[:2], self.profiles[0], training_job={
            "completedAt": "2026-10-25T10:00:00Z", "data": {"status": "cold_start", "fingerprint": "private-fingerprint"}})
        self.assertEqual(result["code"], "insufficient_profiles")
        self.assertEqual(result["consentedProfiles"], 2)
        self.assertEqual(result["minimumProfiles"], 30)
        self.assertEqual(result["missingProfiles"], 28)
        self.assertFalse(result["automaticActivation"])
        serialized = json.dumps(result)
        for private in ("private-farmer", "private-fingerprint", "ownerId", "latitude", "longitude", "baseline", "assignments"):
            self.assertNotIn(private, serialized)

    def test_consent_is_distinct_from_global_training_readiness(self):
        result = model_readiness(self.profiles, {**self.profiles[0], "analyticsConsent": False})
        self.assertEqual(result["code"], "analytics_consent_required")
        self.assertIn("Buyer matching still works", result["nextAction"])

    def test_candidate_review_and_active_model_are_distinct(self):
        train(self.profiles, track=False)
        pending = model_readiness(self.profiles, self.profiles[0])
        self.assertEqual(pending["code"], "review_pending")
        self.assertTrue(pending["candidate"]["qualityGate"])
        self.assertNotIn("fingerprint", json.dumps(pending))
        promote("Named reviewer", self.profiles)
        learned = active_segment(self.profiles[0], self.profiles)
        active = model_readiness(self.profiles, self.profiles[0], learned)
        self.assertEqual(active["code"], "active")
        self.assertEqual(active["activeModel"]["algorithm"], "kmeans")
        changed = [{**self.profiles[0], "hectares": 3}, *self.profiles[1:]]
        self.assertEqual(model_readiness(changed, changed[0])["code"], "cohort_changed")

    def test_quality_failure_is_not_reported_as_missing_training(self):
        profiles = [profile(f"farmer-{i}", crop="tomato" if i < 26 else "maize",
                            hectares=2 if i < 26 else 100, capacityKg=1000 if i < 26 else 100000)
                    for i in range(30)]
        train(profiles, track=False)
        result = model_readiness(profiles, profiles[0])
        self.assertEqual(result["code"], "quality_checks_failed")
        self.assertIn("cluster_too_small", result["candidate"]["qualityFailures"])

    def test_invalid_model_storage_is_not_reported_as_pending_review(self):
        (Path(self.directory.name) / "active.joblib").write_bytes(b"\x80broken")
        self.assertEqual(model_readiness(self.profiles, self.profiles[0])["code"], "artifact_unavailable")

    def test_new_unknown_category_and_outlier_have_specific_fallback_reasons(self):
        train(self.profiles, track=False)
        promote("Reviewer", self.profiles)
        for changes, expected in (({"crop": "cabbage"}, "unknown_category"),
                                  ({"capacityKg": 1000000000, "hectares": 1000000}, "outlier_profile")):
            with self.subTest(expected=expected):
                new = {**self.profiles[0], "ownerId": "new-farmer", **changes}
                self.assertEqual(model_readiness([*self.profiles, new], new)["code"], expected)

    def test_daily_or_monthly_job_age_is_not_worker_liveness(self):
        old = {"completedAt": "2020-01-01T00:00:00Z", "data": {"status": "candidate"}}
        self.assertEqual(model_readiness(self.profiles, training_job=old)["workerStatus"], "unknown")
        now = datetime.now(timezone.utc)
        worker = {"completedAt": now.isoformat(), "data": {"trainingStatus": "up_to_date", "snapshotStatus": "up_to_date"}}
        result = model_readiness(self.profiles, training_job=old, worker_job=worker)
        self.assertEqual(result["workerStatus"], "healthy")
        worker["completedAt"] = (now - timedelta(minutes=30)).isoformat()
        self.assertEqual(model_readiness(self.profiles, worker_job=worker)["code"], "worker_stale")

    def test_worker_training_and_snapshot_failures_are_reported_accurately(self):
        worker = {"completedAt": datetime.now(timezone.utc).isoformat(),
                  "data": {"trainingStatus": "failed", "snapshotStatus": "completed"}}
        result = model_readiness(self.profiles, worker_job=worker)
        self.assertEqual(result["workerStatus"], "failed")
        self.assertEqual(result["code"], "training_failed")
        worker["data"] = {"trainingStatus": "up_to_date", "snapshotStatus": "failed"}
        result = model_readiness(self.profiles, worker_job=worker)
        self.assertEqual(result["workerStatus"], "failed")
        self.assertEqual(result["code"], "training_pending")

    def test_standalone_diagnostics_detect_missing_sklearn_without_leaking_exception_details(self):
        def imports(name):
            if name == "sklearn.cluster":
                raise ImportError("private-system-path and account information")
            return object()
        with patch("app.diagnostics.import_module", side_effect=imports), \
                patch("app.diagnostics.version", return_value="test-version"):
            result = diagnose()
        self.assertEqual(result["status"], "dependency_error")
        self.assertFalse(result["dependencies"]["scikit-learn"]["available"])
        self.assertNotIn("private-system-path", json.dumps(result))

    def test_standalone_diagnostics_exclude_reviewer_and_farmer_identifiers(self):
        train(self.profiles, track=False)
        promote("Sensitive Reviewer Name", self.profiles)
        store = MagicMock()
        store.profiles.return_value = self.profiles
        store.job.return_value = None
        with patch("app.diagnostics.import_module", return_value=object()), \
                patch("app.diagnostics.version", return_value="test-version"), \
                patch("app.store.Store", return_value=store):
            result = diagnose()
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["segmentationStatus"]["code"], "active")
        for private in ("Sensitive Reviewer Name", "private-farmer", "fingerprint", "assignments"):
            self.assertNotIn(private, json.dumps(result))
