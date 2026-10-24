import os
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import Mock, patch
from uuid import uuid4

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.outcomes import create_router


class OutcomeApiTest(unittest.TestCase):
    def setUp(self):
        self.store = Mock()
        self.body = {"eventId": str(uuid4()), "orderId": str(uuid4()), "farmerId": str(uuid4()),
                     "buyerId": str(uuid4()), "recordId": str(uuid4()), "crop": "tomato", "province": "Limpopo",
                     "quantityKg": "125.500", "pricePerKg": "12.50",
                     "occurredAt": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()}
        self.store.record_order_outcome.return_value = {"recorded": True, "duplicate": False,
            "eventId": self.body["eventId"], "orderId": self.body["orderId"], "attributed": False}
        app = FastAPI()
        app.include_router(create_router(self.store))
        self.client = TestClient(app)
        self.secret = "isolated-outcome-api-test-secret"
        self.env = patch.dict(os.environ, {"SEGMENTATION_EVENT_TOKEN": self.secret})
        self.env.start()

    def tearDown(self):
        self.env.stop()

    def post(self, body=None, headers=None):
        return self.client.post("/internal/order-outcomes", json=self.body if body is None else body,
                                headers={"X-Segmentation-Event-Token": self.secret} if headers is None else headers)

    def test_ingestion_disabled_without_a_configured_producer_secret(self):
        with patch.dict(os.environ, {"SEGMENTATION_EVENT_TOKEN": ""}):
            response = self.post()
        self.assertEqual(response.status_code, 503)
        self.store.record_order_outcome.assert_not_called()

    def test_bearer_tokens_and_wrong_producer_tokens_cannot_self_report_orders(self):
        for headers in ({}, {"Authorization": "Bearer ordinary-farmer-jwt"},
                        {"X-Segmentation-Event-Token": "wrong-secret"}):
            with self.subTest(headers=headers):
                response = self.post(headers=headers)
                self.assertEqual(response.status_code, 401)
                self.assertNotIn("wrong-secret", response.text)
                self.assertNotIn(self.secret, response.text)
        self.store.record_order_outcome.assert_not_called()

    def test_trusted_completed_event_and_idempotent_retry(self):
        response = self.post()
        self.assertEqual(response.status_code, 201)
        self.assertTrue(response.json()["recorded"])
        payload = self.store.record_order_outcome.call_args.args[0]
        self.assertEqual(payload["status"], "completed")
        self.assertEqual(payload["occurredAt"][-1], "Z")
        self.assertNotIn("SEGMENTATION_EVENT_TOKEN", payload)
        self.store.record_order_outcome.return_value["duplicate"] = True
        self.assertEqual(self.post().status_code, 200)

    def test_rejects_unowned_or_nonconsented_outcomes(self):
        self.store.record_order_outcome.side_effect = ValueError("Both participants must enable analytics consent")
        response = self.post()
        self.assertEqual(response.status_code, 409)
        self.assertIn("consent", response.json()["detail"])

    def test_rejects_invalid_future_or_incomplete_completed_events(self):
        changes = ({"eventId": "not-a-uuid"}, {"quantityKg": 0}, {"pricePerKg": -1},
                   {"quantityKg": "NaN"}, {"pricePerKg": "Infinity"},
                   {"status": "cancelled"}, {"crop": "unknown"}, {"province": "unknown"},
                   {"occurredAt": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()},
                   {"occurredAt": "2026-01-01T12:00:00"}, {"farmerId": self.body["buyerId"]},
                   {"privateUserField": "extra"})
        for change in changes:
            with self.subTest(change=change):
                self.assertEqual(self.post({**self.body, **change}).status_code, 422)
        self.store.record_order_outcome.assert_not_called()
