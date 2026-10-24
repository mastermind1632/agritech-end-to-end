import os
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from unittest.mock import patch
from uuid import uuid4

from app.store import Store
from test_engine import profile, record


@unittest.skipUnless(os.getenv("SEGMENTATION_TEST_DATABASE") == "true", "Requires isolated PostgreSQL test database")
class StoreTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not os.getenv("PGDATABASE", "").endswith("_test") or not os.getenv("MODEL_DIR"):
            raise RuntimeError("Use an isolated database ending in _test and an explicit test MODEL_DIR")
        cls.store = Store()
        with cls.store.connect() as conn:
            conn.execute("CREATE TABLE IF NOT EXISTS public.farmers(id varchar(36) PRIMARY KEY)")
            conn.execute("CREATE TABLE IF NOT EXISTS public.expenses(farmer_id varchar(36),amount numeric,date date)")
        cls.store.initialize()

    def setUp(self):
        self.owner = str(uuid4())
        self.other = str(uuid4())
        with self.store.connect() as conn:
            conn.execute("INSERT INTO public.farmers(id) VALUES(%s),(%s)", (self.owner, self.other))

    def tearDown(self):
        with self.store.connect() as conn:
            conn.execute("DELETE FROM public.farmers WHERE id IN (%s,%s)", (self.owner, self.other))

    def test_real_postgres_consent_feedback_ownership_and_snapshots(self):
        p = {k: v for k, v in profile().items() if k != "ownerId"}
        self.store.save_profile(self.owner, p)
        self.assertEqual(self.store.profile(self.owner)["crop"], "tomato")
        r = {k: v for k, v in record().items() if k not in ("id", "ownerId")}
        r["availableFrom"] = r["availableUntil"] = date.today().isoformat()
        saved = self.store.save_record(self.other, r)
        self.assertFalse(self.store.delete_record(self.owner, saved["id"]))
        self.store.log_search(self.owner, {"crop": "tomato", "province": "Limpopo", "resultCount": 0})
        impression = self.store.impression(self.owner, "control", "rules-v1", [saved])
        feedback = {"impressionId": impression, "recordId": saved["id"], "action": "click"}
        self.assertFalse(self.store.feedback(self.other, feedback))
        self.assertTrue(self.store.feedback(self.owner, feedback))
        self.assertTrue(self.store.feedback(self.owner, feedback))
        self.assertGreater(self.store.exposure()[saved["id"]], 0)
        self.store.publish_snapshot(lambda: {"test": True})
        self.assertEqual(self.store.latest_snapshot(), {"test": True})
        self.store.save_profile(self.owner, {**p, "analyticsConsent": False})
        self.assertIsNone(self.store.latest_snapshot())
        self.assertFalse(self.store.feedback(self.owner, feedback))
        self.assertNotIn(self.owner, [x["ownerId"] for x in self.store.profiles()])
        self.assertEqual(self.store.expense_trend(self.owner), {"current": 0, "previous": 0})
        self.store.delete_profile(self.owner)
        self.assertIsNone(self.store.profile(self.owner))

    def test_nonconsented_account_does_not_invalidate_other_farmers_model(self):
        from pathlib import Path
        from app.training import artifact_dir
        artifact = artifact_dir() / "active.json"
        artifact.write_text('{"test":true}')
        p = {k: v for k, v in profile(analyticsConsent=False).items() if k != "ownerId"}
        self.store.save_profile(self.owner, p)
        self.assertTrue(artifact.exists())
        self.store.delete_profile(self.owner)
        self.assertTrue(artifact.exists())
        artifact.unlink()

    def test_analytics_writers_wait_for_concurrent_consent_revocation(self):
        p = {k: v for k, v in profile().items() if k != "ownerId"}
        original_connect = self.store.connect
        for kind in ("search", "impression", "feedback"):
            with self.subTest(kind=kind):
                self.store.save_profile(self.owner, p)
                record = {"id": str(uuid4())}
                impression = self.store.impression(self.owner, "control", "rules-v1", [record])
                feedback = {"impressionId": impression, "recordId": record["id"], "action": "click"}
                actions = {
                    "search": lambda: self.store.log_search(self.owner, {"crop": "tomato", "province": "Limpopo", "resultCount": 0}),
                    "impression": lambda: self.store.impression(self.owner, "control", "rules-v1", [record]),
                    "feedback": lambda: self.store.feedback(self.owner, feedback),
                }
                connected = threading.Event()
                worker_pids = []

                def connect_worker():
                    conn = original_connect()
                    worker_pids.append(conn.info.backend_pid)
                    connected.set()
                    return conn

                with ThreadPoolExecutor(max_workers=1) as executor:
                    with original_connect() as revoking, original_connect() as observer:
                        # Same transaction ordering as save_profile: revoke first, then remove analytics.
                        revoking.execute("UPDATE market_intelligence.profiles SET data=jsonb_set(data,'{analyticsConsent}','false') WHERE owner_id=%s", (self.owner,))
                        revoking.execute("DELETE FROM market_intelligence.searches WHERE owner_id=%s", (self.owner,))
                        revoking.execute("DELETE FROM market_intelligence.impressions WHERE owner_id=%s", (self.owner,))
                        with patch.object(self.store, "connect", side_effect=connect_worker):
                            future = executor.submit(actions[kind])
                            self.assertTrue(connected.wait(5))
                            deadline = time.monotonic() + 5
                            waiting = False
                            while not future.done() and time.monotonic() < deadline:
                                row = observer.execute("SELECT wait_event_type FROM pg_stat_activity WHERE pid=%s", (worker_pids[0],)).fetchone()
                                # Refresh pg_stat_activity's transaction-local statistics snapshot.
                                observer.execute("SELECT pg_stat_clear_snapshot()")
                                if row and row[0] == "Lock":
                                    waiting = True
                                    break
                                time.sleep(0.01)
                            self.assertTrue(waiting, f"{kind} must wait for the consent transaction")
                    result = future.result(timeout=5)
                if kind in ("impression", "feedback"):
                    self.assertFalse(result)
                with original_connect() as conn:
                    self.assertEqual(conn.execute("SELECT count(*) FROM market_intelligence.searches WHERE owner_id=%s", (self.owner,)).fetchone()[0], 0)
                    self.assertEqual(conn.execute("SELECT count(*) FROM market_intelligence.impressions WHERE owner_id=%s", (self.owner,)).fetchone()[0], 0)

    def outcome(self, attributed=True):
        p = {k: v for k, v in profile().items() if k != "ownerId"}
        self.store.save_profile(self.owner, p)
        self.store.save_profile(self.other, p)
        listing = {k: v for k, v in record().items() if k not in ("id", "ownerId")}
        listing.update(availableFrom=(date.today() - timedelta(days=1)).isoformat(),
                       availableUntil=(date.today() + timedelta(days=1)).isoformat())
        saved = self.store.save_record(self.other, listing)
        impression = self.store.impression(self.owner, "control", "rules-v1", [saved]) if attributed else None
        return {"eventId": str(uuid4()), "orderId": str(uuid4()), "farmerId": self.owner,
                "buyerId": self.other, "recordId": saved["id"], "crop": "tomato", "province": "Limpopo",
                "quantityKg": "125.500", "pricePerKg": "12.50", "impressionId": impression,
                "occurredAt": datetime.now(timezone.utc).isoformat()}

    def test_completed_order_idempotency_and_contact_conversion_are_separate(self):
        event = self.outcome()
        self.store.feedback(self.owner, {"impressionId": event["impressionId"], "recordId": event["recordId"], "action": "click"})
        before = self.store.experiment_report()[0]
        self.assertEqual(before["engagedImpressions"], 1)
        self.assertEqual(before["trustedCompletedOrders"], 0)
        result = self.store.record_order_outcome(event)
        self.assertFalse(result["duplicate"])
        self.assertTrue(result["attributed"])
        self.assertTrue(self.store.record_order_outcome({**event, "quantityKg": "125.5"})["duplicate"])
        self.assertTrue(self.store.record_order_outcome({**event, "eventId": str(uuid4())})["duplicate"])
        self.assertEqual(len(self.store.order_outcomes()), 1)
        row = self.store.order_outcomes()[0]
        self.assertEqual(row["quantityKg"], 125.5)
        self.assertEqual(row["status"], "completed")
        self.assertNotIn("farmerId", row)
        self.assertNotIn("buyerId", row)
        self.assertNotIn("impressionId", row)
        report = self.store.experiment_report()[0]
        self.assertEqual(report["trustedCompletedOrders"], 1)
        self.assertEqual(report["convertedImpressions"], 1)
        self.assertEqual(report["recordedOrderConversionRate"], 1)
        self.assertIsNone(report["verifiedOrders"])
        with self.assertRaisesRegex(ValueError, "different completed order"):
            self.store.record_order_outcome({**event, "pricePerKg": "15"})
        with self.assertRaisesRegex(ValueError, "different completed order"):
            self.store.record_order_outcome({**event, "orderId": str(uuid4())})

    def test_completed_orders_validate_buyer_crop_region_and_impression_ownership(self):
        event = self.outcome()
        for change in ({"farmerId": self.other, "buyerId": self.owner}, {"crop": "maize"},
                       {"province": "Gauteng"}, {"recordId": str(uuid4())}, {"impressionId": str(uuid4())},
                       {"occurredAt": (datetime.now(timezone.utc) - timedelta(hours=12)).isoformat()}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.store.record_order_outcome({**event, **change})
        self.assertEqual(self.store.order_outcomes(), [])
        unattributed = {**event, "impressionId": None}
        self.assertFalse(self.store.record_order_outcome(unattributed)["attributed"])
        self.assertEqual(self.store.experiment_report()[0]["trustedCompletedOrders"], 0)
        self.assertEqual(len(self.store.order_outcomes()), 1)

    def test_either_participant_revocation_or_profile_deletion_removes_outcome_analytics(self):
        for participant in ("farmerId", "buyerId"):
            for action in ("revoke", "delete"):
                with self.subTest(participant=participant, action=action):
                    event = self.outcome()
                    self.store.record_order_outcome(event)
                    self.store.publish_snapshot(lambda: {"completedOrders": 1})
                    owner = event[participant]
                    if action == "revoke":
                        p = self.store.profile(owner)
                        self.store.save_profile(owner, {k: v for k, v in {**p, "analyticsConsent": False}.items() if k != "ownerId"})
                    else:
                        self.store.delete_profile(owner)
                    self.assertEqual(self.store.order_outcomes(), [])
                    with self.store.connect() as conn:
                        self.assertEqual(conn.execute("SELECT count(*) FROM market_intelligence.order_outcomes").fetchone()[0], 0)
                        self.assertEqual(conn.execute("SELECT count(*) FROM market_intelligence.snapshots").fetchone()[0], 0)
                    with self.assertRaisesRegex(ValueError, "consent"):
                        self.store.record_order_outcome(event)

    def test_deleting_source_demand_removes_completed_order_analytics(self):
        event = self.outcome()
        self.store.record_order_outcome(event)
        self.store.publish_snapshot(lambda: {"completedOrders": 1})
        self.assertTrue(self.store.delete_record(self.other, event["recordId"]))
        self.assertEqual(self.store.order_outcomes(), [])
        with self.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM market_intelligence.snapshots").fetchone()[0], 0)

    def test_order_availability_uses_south_african_calendar_date_at_utc_boundary(self):
        event = self.outcome(attributed=False)
        local_day = date.today() - timedelta(days=1)
        occurred = datetime.combine(local_day - timedelta(days=1), datetime.min.time(), timezone.utc) + timedelta(hours=22, minutes=30)
        event["occurredAt"] = occurred.isoformat()
        with self.store.connect() as conn:
            conn.execute("""UPDATE market_intelligence.records SET data=jsonb_set(
                jsonb_set(data,'{availableFrom}',to_jsonb(%s::text)),'{availableUntil}',to_jsonb(%s::text)) WHERE id=%s""",
                (local_day.isoformat(), local_day.isoformat(), event["recordId"]))
        self.assertNotEqual(occurred.date(), local_day)
        self.assertTrue(self.store.record_order_outcome(event)["recorded"])

    def test_completed_order_waits_for_concurrent_consent_revocation(self):
        event = self.outcome()
        original_connect = self.store.connect
        connected = threading.Event()

        def connect_worker():
            conn = original_connect()
            connected.set()
            return conn

        with ThreadPoolExecutor(max_workers=1) as executor:
            with original_connect() as revoking:
                revoking.execute("SELECT pg_advisory_xact_lock(68410202)")
                revoking.execute("UPDATE market_intelligence.profiles SET data=jsonb_set(data,'{analyticsConsent}','false') WHERE owner_id=%s", (self.other,))
                with patch.object(self.store, "connect", side_effect=connect_worker):
                    future = executor.submit(self.store.record_order_outcome, event)
                    self.assertTrue(connected.wait(5))
                    self.assertFalse(future.done())
            with self.assertRaisesRegex(ValueError, "consent"):
                future.result(timeout=5)
        self.assertEqual(self.store.order_outcomes(), [])
