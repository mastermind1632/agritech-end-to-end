import os
import unittest
from datetime import date
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
        self.store.save_profile(self.owner, {**p, "analyticsConsent": False})
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
