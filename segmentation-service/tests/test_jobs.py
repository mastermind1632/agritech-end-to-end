import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from app import jobs
from app.engine import cohort_fingerprint
from test_engine import profile


class FakeStore:
    def __init__(self, profiles):
        self.current_profiles = profiles
        self.now = datetime(2026, 10, 19, tzinfo=timezone.utc)
        self.jobs = {}
        self.snapshot = None
        self.outcomes = [{"eventId": "trusted-completion"}]

    def profiles(self):
        return self.current_profiles

    def with_cohort_lock(self, build):
        return build(self.profiles())

    def job(self, name, data=None):
        if data is not None:
            self.jobs[name] = {"completedAt": self.now.isoformat(), "data": dict(data)}
        return self.jobs.get(name)

    def publish_snapshot(self, build):
        self.snapshot = build()
        return self.snapshot

    def latest_snapshot(self):
        return self.snapshot

    def records(self):
        return []

    def searches(self):
        return []

    def prices(self):
        return []

    def order_outcomes(self):
        return self.outcomes

    def experiment_report(self):
        return []


class JobsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.env = patch.dict(os.environ, {"MODEL_DIR": self.directory.name})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.store = FakeStore([profile(f"farmer-{i}") for i in range(45)])
        self.attempts = 0
        self.algorithms = []
        self.quality = True

    def fake_train(self, profiles, algorithm="kmeans", k="auto"):
        self.attempts += 1
        self.algorithms.append(algorithm)
        report = {"status": "cold_start" if len(profiles) < 30 else "candidate", "profiles": len(profiles),
                  "algorithm": algorithm, "fingerprint": cohort_fingerprint(profiles)}
        if report["status"] == "candidate":
            report.update(version=f"candidate-{self.attempts}", qualityGate=self.quality,
                          reviewStatus="Agronomist/extension-officer review required")
            (self.root / "candidate.json").write_text(json.dumps(report))
            (self.root / "candidate.joblib").write_bytes(b"model-test-placeholder")
        return report

    def successful_snapshot(self, store):
        store.snapshot = {"generatedAt": store.now.isoformat()}
        store.job("nightly_snapshot", {"segments": 0, "demandRows": 0})
        return store.snapshot

    def cycle(self):
        with patch.object(jobs, "train", side_effect=self.fake_train), patch.object(jobs, "snapshot", side_effect=self.successful_snapshot):
            return jobs.run_cycle(self.store, self.store.now)

    def test_cold_start_rechecks_changed_data_and_training_threshold(self):
        self.store.current_profiles = self.store.current_profiles[:2]
        self.assertEqual(self.cycle()["training"]["status"], "cold_start")
        self.cycle()
        self.assertEqual(self.attempts, 1)
        self.store.current_profiles.append(profile("third"))
        self.assertEqual(self.cycle()["training"]["profiles"], 3)
        self.store.current_profiles.extend(profile(f"added-{i}") for i in range(27))
        self.assertEqual(self.cycle()["training"]["status"], "candidate")
        self.assertEqual(self.attempts, 3)
        self.assertTrue(self.store.jobs["monthly_training"]["data"]["qualityGate"])

    def test_rejected_candidate_waits_for_data_change_or_monthly_schedule(self):
        self.quality = False
        first = self.cycle()["training"]
        self.assertFalse(first["qualityGate"])
        self.assertIn("review", first["reviewStatus"])
        self.store.now += timedelta(minutes=5)
        self.assertEqual(self.cycle()["training"]["status"], "up_to_date")
        self.assertEqual(self.attempts, 1)
        self.store.current_profiles[0] = {**self.store.current_profiles[0], "hectares": 3}
        self.assertIn("cohort_changed", self.cycle()["training"]["trigger"])
        self.store.now = datetime(2026, 11, 1, tzinfo=timezone.utc)
        self.assertIn("monthly_schedule", self.cycle()["training"]["trigger"])
        self.assertEqual(self.attempts, 3)

    def test_missing_candidate_is_rebuilt_without_repeated_retraining(self):
        self.cycle()
        (self.root / "candidate.joblib").unlink()
        self.assertIn("candidate_missing", self.cycle()["training"]["trigger"])
        self.cycle()
        self.assertEqual(self.attempts, 2)

    def test_revoked_snapshot_is_rebuilt_even_when_todays_job_marker_remains(self):
        self.assertEqual(self.cycle()["snapshot"], "completed")
        self.assertEqual(self.cycle()["snapshot"], "up_to_date")
        self.store.snapshot = None  # Consent revocation deletes snapshots, preserving job history.
        self.assertEqual(self.store.jobs["nightly_snapshot"]["completedAt"][:10], "2026-10-19")
        self.assertEqual(self.cycle()["snapshot"], "completed")
        self.assertIsNotNone(self.store.latest_snapshot())
        self.assertEqual(self.cycle()["snapshot"], "up_to_date")
        self.assertEqual(self.attempts, 1)

    def test_offset_job_dates_use_utc_for_daily_and_legacy_monthly_schedules(self):
        self.store.now = datetime(2026, 1, 31, 23, 30, tzinfo=timezone.utc)
        self.cycle()
        for name in ("nightly_snapshot", "monthly_training"):
            self.store.jobs[name]["completedAt"] = "2026-02-01T01:00:00+02:00"
        self.store.jobs["monthly_training"]["data"].pop("trainingPeriod")
        result = self.cycle()
        self.assertEqual(result["snapshot"], "up_to_date")
        self.assertEqual(result["training"]["status"], "up_to_date")
        self.assertEqual(self.attempts, 1)
        self.store.now = datetime(2026, 2, 1, 0, 30, tzinfo=timezone.utc)
        result = self.cycle()
        self.assertEqual(result["snapshot"], "completed")
        self.assertIn("monthly_schedule", result["training"]["trigger"])
        self.assertEqual(self.attempts, 2)

    def test_explicit_training_period_is_utc_and_is_preserved(self):
        local = datetime.fromisoformat("2026-02-01T01:00:00+02:00")
        self.assertEqual(jobs.training_status({"status": "candidate"}, local)["trainingPeriod"], "2026-01")
        self.store.now = datetime(2026, 1, 31, 23, 30, tzinfo=timezone.utc)
        self.cycle()
        # An explicit UTC period takes precedence over the legacy completion timestamp.
        self.store.jobs["monthly_training"]["completedAt"] = "2026-02-01T03:00:00+02:00"
        self.assertEqual(self.cycle()["training"]["status"], "up_to_date")
        self.assertEqual(self.attempts, 1)

    def test_cli_promotion_normalizes_legacy_period_without_delaying_monthly_training(self):
        report = {"status": "active", "version": "reviewed-version", "algorithm": "kmeans",
                  "profiles": 45, "qualityGate": True, "reviewStatus": "Reviewed by Reviewer"}
        self.store.jobs["monthly_training"] = {"completedAt": "2026-02-01T01:00:00+02:00",
                                                "data": {"version": report["version"]}}
        self.store.initialize = lambda: None
        with patch("sys.argv", ["jobs", "promote", "--reviewed-by", "Reviewer"]), \
                patch.object(jobs, "Store", return_value=self.store), \
                patch.object(jobs, "promote", return_value=report), patch("builtins.print"):
            jobs.main()
        self.assertEqual(self.store.jobs["monthly_training"]["data"]["trainingPeriod"], "2026-01")

    def test_lost_promoted_artifact_creates_one_candidate_and_keeps_human_review(self):
        self.cycle()
        for suffix in ("json", "joblib"):
            (self.root / f"active.{suffix}").write_bytes((self.root / f"candidate.{suffix}").read_bytes())
        self.cycle()  # Observe the reviewed version before simulating its loss.
        (self.root / "active.joblib").unlink()
        self.assertIn("active_missing", self.cycle()["training"]["trigger"])
        self.cycle()
        self.assertEqual(self.attempts, 2)
        self.assertFalse((self.root / "active.joblib").exists())
        self.assertIn("review required", self.store.jobs["monthly_training"]["data"]["reviewStatus"])

    def test_lost_active_model_below_training_minimum_settles_on_cold_start(self):
        self.cycle()
        for suffix in ("json", "joblib"):
            (self.root / f"active.{suffix}").write_bytes((self.root / f"candidate.{suffix}").read_bytes())
        self.cycle()
        self.store.current_profiles = self.store.current_profiles[:5]
        for name in ("candidate", "active"):
            for suffix in ("json", "joblib"):
                (self.root / f"{name}.{suffix}").unlink()
        self.assertEqual(self.cycle()["training"]["status"], "cold_start")
        self.assertEqual(self.cycle()["training"]["status"], "up_to_date")
        self.assertEqual(self.attempts, 2)

    def test_snapshot_failure_does_not_block_training(self):
        with patch.object(jobs, "snapshot", side_effect=RuntimeError("snapshot unavailable")), \
                patch.object(jobs, "train", side_effect=self.fake_train), patch.object(jobs.logger, "exception"):
            result = jobs.run_cycle(self.store, self.store.now)
        self.assertEqual(result["snapshot"], "failed")
        self.assertEqual(result["training"]["status"], "candidate")
        self.assertEqual(self.attempts, 1)

    def test_training_failure_does_not_block_snapshot_and_is_retried(self):
        with patch.object(jobs, "snapshot", side_effect=self.successful_snapshot), \
                patch.object(jobs, "train", side_effect=RuntimeError("training unavailable")) as fit, \
                patch.object(jobs.logger, "exception"):
            result = jobs.run_cycle(self.store, self.store.now)
            jobs.run_cycle(self.store, self.store.now)
        self.assertEqual(result["snapshot"], "completed")
        self.assertEqual(result["training"]["status"], "failed")
        self.assertIn("nightly_snapshot", self.store.jobs)
        self.assertNotIn("monthly_training", self.store.jobs)
        self.assertEqual(fit.call_count, 2)
        self.assertEqual(self.store.jobs["worker_status"]["data"]["trainingStatus"], "failed")
        self.assertEqual(self.store.jobs["worker_status"]["data"]["snapshotStatus"], "up_to_date")

    def test_worker_reports_every_cycle_without_repeating_daily_or_monthly_work(self):
        self.cycle()
        self.store.now += timedelta(minutes=5)
        result = self.cycle()
        self.assertEqual(result["snapshot"], "up_to_date")
        self.assertEqual(result["training"]["status"], "up_to_date")
        self.assertEqual(self.store.jobs["worker_status"]["completedAt"], self.store.now.isoformat())
        self.assertEqual(self.attempts, 1)

    def test_training_policy_change_rechecks_unchanged_cohort_once(self):
        self.cycle()
        self.store.jobs["monthly_training"]["data"].pop("trainingPolicyVersion")
        self.assertIn("training_policy_changed", self.cycle()["training"]["trigger"])
        self.cycle()
        self.assertEqual(self.attempts, 2)

    def test_operator_algorithm_choice_is_preserved_during_recovery(self):
        self.store.job("monthly_training", {"status": "cold_start", "algorithm": "kprototypes"})
        self.cycle()
        (self.root / "candidate.joblib").unlink()
        self.cycle()
        self.assertEqual(self.algorithms, ["kprototypes", "kprototypes"])

    def test_snapshot_uses_reviewed_groups_and_suppresses_small_groups(self):
        self.store.current_profiles = self.store.current_profiles[:8]
        learned = {p["ownerId"]: {"name": "Reviewed tomato group", "method": "kmeans", "version": "reviewed-v1",
                    "segmentId": "1", "assignment": "training_cohort", "reviewStatus": "Reviewed by agronomist"}
                   for p in self.store.current_profiles[:6]}
        with patch.object(jobs, "active_segments", return_value=learned), \
                patch.object(jobs, "drift", return_value={"status": "stable"}), \
                patch.object(jobs, "demand_summary", return_value=[]) as demand:
            result = jobs.snapshot(self.store)
        self.assertEqual(len(result["segments"]), 1)
        self.assertEqual(result["segments"][0]["farmers"], 6)
        self.assertEqual(result["segments"][0]["method"], "kmeans")
        self.assertNotIn("assignment", result["segments"][0])
        self.assertEqual(result["segmentation"], {"learnedProfiles": 6, "ruleProfiles": 2})
        self.assertEqual(result["suppressedSmallSegments"], 1)
        self.assertEqual(demand.call_args.kwargs["order_outcomes"], self.store.outcomes)

    def test_distinct_clusters_with_similar_names_remain_separate(self):
        self.store.current_profiles = self.store.current_profiles[:10]
        learned = {p["ownerId"]: {"name": "Tomato growers", "method": "kmeans", "version": "reviewed-v1",
                    "segmentId": str(i // 5), "reviewStatus": "Reviewed by agronomist"}
                   for i, p in enumerate(self.store.current_profiles)}
        with patch.object(jobs, "active_segments", return_value=learned), \
                patch.object(jobs, "drift", return_value={"status": "stable"}), \
                patch.object(jobs, "demand_summary", return_value=[]):
            result = jobs.snapshot(self.store)
        self.assertEqual([group["farmers"] for group in result["segments"]], [5, 5])
        self.assertEqual({group["segmentId"] for group in result["segments"]}, {"0", "1"})
