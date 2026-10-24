import os
import tempfile
import unittest
from datetime import date, datetime, timedelta, timezone
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient

from app import main
from app.training import promote, train
from test_engine import profile, record


class FakeStore:
    def __init__(self):
        self.p = profile()
        self.r = record(str(uuid4()), availableFrom=date.today().isoformat(), availableUntil=date.today().isoformat())
        self.feedback_owner = None

    def profile(self, owner):
        return self.p

    def profiles(self):
        return []

    def job(self, name):
        return None

    def save_profile(self, owner, body):
        self.p = {**body, "ownerId": owner}
        return self.p

    def records(self, *args, **kwargs):
        return [self.r]

    def exposure(self):
        return {}

    def impression(self, *args):
        return str(uuid4())

    def delete_record(self, owner, key):
        return owner == self.r["ownerId"] and str(key) == self.r["id"]

    def feedback(self, owner, body):
        self.feedback_owner = owner
        return False


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.store = FakeStore()
        self.patch = patch.object(main, "store", self.store)
        self.patch.start()
        main.app.dependency_overrides[main.identity] = lambda: "farmer-a"
        self.client = TestClient(main.app)

    def tearDown(self):
        main.app.dependency_overrides.clear()
        self.patch.stop()

    def test_profile_cannot_spoof_owner(self):
        body = {k: v for k, v in profile().items() if k != "ownerId"}
        self.assertEqual(self.client.put("/api/profile", json={**body, "ownerId": "another"}).status_code, 422)
        self.assertEqual(self.client.put("/api/profile", json=body).json()["profile"]["ownerId"], "farmer-a")

    def test_public_browse_redacts_exact_coordinates_and_owner(self):
        r = self.client.get("/api/records").json()["records"][0]
        self.assertNotIn("latitude", r)
        self.assertNotIn("longitude", r)
        self.assertNotIn("ownerId", r)

    def test_foreign_listing_delete_is_not_allowed(self):
        self.assertEqual(self.client.delete("/api/records/" + self.store.r["id"]).status_code, 404)

    def test_invalid_feedback_and_foreign_impression(self):
        self.assertEqual(self.client.post("/api/feedback", json={"impressionId": "x" * 36, "recordId": "x" * 36, "action": "click"}).status_code, 422)
        self.assertEqual(self.client.post("/api/feedback", json={"impressionId": str(uuid4()), "recordId": str(uuid4()), "action": "click"}).status_code, 404)
        self.assertEqual(self.store.feedback_owner, "farmer-a")

    def test_missing_profile_gets_actionable_status(self):
        self.store.p = None
        self.assertEqual(self.client.get("/api/recommendations").status_code, 409)

    def test_authentication_required(self):
        main.app.dependency_overrides.clear()
        self.assertEqual(self.client.get("/api/profile").status_code, 401)

    def test_feedback_tracking_requires_consent(self):
        self.store.p["analyticsConsent"] = False
        self.assertEqual(self.client.post("/api/feedback", json={"impressionId": str(uuid4()), "recordId": str(uuid4()), "action": "click"}).status_code, 409)

    def test_recommendations_explain_small_cohort_and_include_only_own_allowed_context(self):
        self.store.profiles = lambda: [self.store.p, profile("second-farmer")]
        self.store.job = lambda name: {"completedAt": "2026-10-25T10:00:00Z", "data": {
            "status": "cold_start", "profiles": 2, "fingerprint": "private-fingerprint"}} if name == "monthly_training" else None
        payload = self.client.get("/api/recommendations").json()
        self.assertEqual(payload["segment"]["method"], "rules")
        status = payload["segmentationStatus"]
        self.assertEqual(status["code"], "insufficient_profiles")
        self.assertEqual(status["consentedProfiles"], 2)
        self.assertEqual(status["minimumProfiles"], 30)
        self.assertEqual(status["trainingStatus"], "cold_start")
        self.assertEqual(status["workerStatus"], "unknown")
        self.assertNotIn("private-fingerprint", str(status))
        self.assertEqual(set(payload["productionContext"]), {"crop", "province", "district", "hectares", "capacityKg",
            "plantingMonth", "harvestMonth", "deliveryRadiusKm", "businessRegistered", "consistentProduction"})
        self.assertEqual(payload["productionContext"]["crop"], self.store.p["crop"])

    def test_recommendations_distinguish_opted_out_profile_from_model_failure(self):
        self.store.p["analyticsConsent"] = False
        payload = self.client.get("/api/recommendations").json()
        self.assertEqual(payload["segmentationStatus"]["code"], "analytics_consent_required")
        self.assertEqual(payload["segment"]["method"], "rules")

    def test_forecasts_are_served_from_published_analytics_without_request_time_fitting(self):
        forecast = {"crop": "tomato", "forecast": [{"month": date.today().replace(day=1).isoformat(), "quantityKg": 100}]}
        self.store.latest_snapshot = lambda: {"generatedAt": datetime.now(timezone.utc).isoformat(), "forecasts": [forecast]}
        self.store.searches = self.store.prices = self.store.order_outcomes = lambda: []
        self.store.job = lambda name: None
        with patch("app.forecasting._holt_winters", side_effect=AssertionError("Do not fit models in HTTP requests")):
            response = self.client.get("/api/demand")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["forecasts"], [forecast])
        self.assertEqual(response.json()["forecastStatus"], "published")
        self.store.latest_snapshot = lambda: {"generatedAt": (datetime.now(timezone.utc) - timedelta(days=3)).isoformat(), "forecasts": [forecast]}
        self.assertEqual(self.client.get("/api/demand").json()["forecasts"], [])
        self.store.latest_snapshot = lambda: None
        self.assertEqual(self.client.get("/api/demand").json()["forecastStatus"], "pending_analytics")

    def test_identity_connection_reset_returns_an_actionable_service_status(self):
        main.app.dependency_overrides.clear()
        with patch("app.main.urlopen", side_effect=ConnectionResetError("closed")):
            response = self.client.get("/api/profile", headers={"Authorization": "Bearer synthetic"})
        self.assertEqual(response.status_code, 503)

    def test_recommendations_use_reviewed_kmeans_then_fall_back_when_cohort_changes(self):
        profiles = [profile("farmer-a" if i == 0 else str(i),
                            crop=("tomato", "maize", "citrus")[i % 3],
                            hectares=(2, 30, 150)[i % 3],
                            capacityKg=(1000, 20000, 100000)[i % 3],
                            harvestMonth=date.today().month) for i in range(45)]
        self.store.p = profiles[0]
        self.store.profiles = lambda: profiles
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"MODEL_DIR": directory}):
            self.assertEqual(self.client.get("/api/recommendations").json()["segment"]["method"], "rules")
            train(profiles, track=False)
            promote("Test reviewer", profiles)
            response = self.client.get("/api/recommendations")
            self.assertEqual(response.status_code, 200)
            payload = response.json()
            self.assertEqual(payload["segment"]["method"], "kmeans")
            self.assertEqual(payload["segmentationStatus"]["code"], "active")
            self.assertEqual([r["id"] for r in payload["recommendations"]], [self.store.r["id"]])
            self.assertNotIn("ownerId", payload["recommendations"][0])
            profiles.append(profile("new-farmer", harvestMonth=date.today().month))
            self.assertEqual(self.client.get("/api/recommendations").json()["segment"]["method"], "kmeans")
            profiles[1] = {**profiles[1], "hectares": profiles[1]["hectares"] + 1}
            self.assertEqual(self.client.get("/api/recommendations").json()["segment"]["method"], "rules")
