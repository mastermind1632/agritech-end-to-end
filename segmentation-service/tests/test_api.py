import unittest
from datetime import date
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient

from app import main
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
