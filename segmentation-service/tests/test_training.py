import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import joblib
import numpy as np
from sklearn.metrics import adjusted_rand_score

from app.training import NUMERIC, active_segment, active_segments, artifact_versions, cluster_silhouette, drift, invalidate_artifacts, minimum_profiles, promote, train
from test_engine import profile


class TrainingTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.env = patch.dict(os.environ, {"MODEL_DIR": self.directory.name})
        self.env.start()
        self.profiles = [profile(str(i), crop=("tomato", "maize", "citrus")[i % 3],
                                 hectares=(2, 30, 150)[i % 3] + i / 100,
                                 capacityKg=(1000, 20000, 100000)[i % 3],
                                 latitude=(-23.9, -26.2, -33.9)[i % 3] + i / 10000,
                                 longitude=(29.45, 28.04, 18.42)[i % 3] + i / 10000) for i in range(45)]

    def tearDown(self):
        self.env.stop()
        self.directory.cleanup()

    def test_cold_start(self):
        self.assertEqual(train(self.profiles[:5], track=False)["status"], "cold_start")

    def test_only_consented_profiles_count_towards_training_minimum(self):
        profiles = [{**p, "analyticsConsent": i < 29} for i, p in enumerate(self.profiles)]
        result = train(profiles, track=False)
        self.assertEqual(result["status"], "cold_start")
        self.assertEqual(result["profiles"], 29)

    def test_identical_profiles_fail_quality_gate_and_cannot_be_promoted(self):
        profiles = [profile(str(i)) for i in range(30)]
        result = train(profiles, track=False)
        self.assertEqual(result["status"], "insufficient_variation")
        self.assertFalse(result["qualityGate"])
        self.assertIsNone(result["silhouette"])
        with self.assertRaises(ValueError):
            promote("Reviewer", profiles)

    def test_auto_k_recovers_two_supported_groups_instead_of_forcing_three(self):
        profiles = [profile(str(i), crop="tomato" if i < 15 else "maize",
                            hectares=2 if i < 15 else 100, capacityKg=1000 if i < 15 else 100000) for i in range(30)]
        for algorithm in ("kmeans", "kprototypes"):
            with self.subTest(algorithm=algorithm):
                report = train(profiles, algorithm, track=False)
                self.assertTrue(report["qualityGate"])
                self.assertEqual(report["selectedClusters"], 2)
                self.assertEqual(sorted(report["clusterSizes"].values()), [15, 15])
                self.assertEqual(report["clusterSelection"]["method"], "best_valid_silhouette")
                self.assertIsNone(active_segment(profiles[0], profiles))

    def test_explicit_pilot_minimum_respects_two_groups_of_five_and_still_requires_review(self):
        with patch.dict(os.environ, {"SEGMENTATION_MIN_PROFILES": "9"}):
            with self.assertRaises(ValueError):
                minimum_profiles()
        profiles = [profile(str(i), crop="tomato" if i < 5 else "maize", hectares=2 if i < 5 else 100,
                            capacityKg=1000 if i < 5 else 100000) for i in range(10)]
        self.assertEqual(train(profiles, track=False)["status"], "cold_start")
        with patch.dict(os.environ, {"SEGMENTATION_MIN_PROFILES": "10"}):
            report = train(profiles, track=False)
            self.assertEqual(report["minimum"], 10)
            self.assertTrue(report["qualityGate"])
            self.assertIsNone(active_segment(profiles[0], profiles))
            promote("Pilot reviewer", profiles)
            self.assertEqual(active_segment(profiles[0], profiles)["method"], "kmeans")

    def test_real_kmeans_train_review_and_consent_invalidation(self):
        result = train(self.profiles, track=False)
        self.assertTrue(result["qualityGate"])
        learned = joblib.load(Path(self.directory.name) / "candidate.joblib")["assignments"]
        # A real fit should recover the three planted crop/size groups, regardless of label numbering.
        score = adjusted_rand_score([i % 3 for i in range(45)], [learned[p["ownerId"]] for p in self.profiles])
        self.assertGreater(score, 0.95)
        self.assertIsNone(active_segment(self.profiles[0], self.profiles))
        promote("Test reviewer", self.profiles)
        self.assertEqual(active_segment(self.profiles[0], self.profiles)["method"], "kmeans")
        self.assertIsNone(active_segment({**self.profiles[0], "analyticsConsent": False}, self.profiles))
        self.assertIsNone(active_segment(self.profiles[0], self.profiles[:-1]))
        self.assertEqual(drift(self.profiles)["status"], "stable")
        invalidate_artifacts()
        self.assertIsNone(active_segment(self.profiles[0], self.profiles))

    def test_kprototypes_and_geographic_hdbscan(self):
        for algorithm in ("kprototypes", "hdbscan"):
            result = train(self.profiles, algorithm, track=False)
            self.assertEqual(result["algorithm"], algorithm)
            self.assertGreaterEqual(len(result["clusterSizes"]), 2)
            self.assertEqual(result["silhouetteSampleSize"], sum(result["clusterSizes"].values()))

    def test_kprototypes_silhouette_uses_the_fitted_category_weight(self):
        numeric = np.zeros((4, len(NUMERIC)))
        numeric[:, 0] = [0, 1, 0, 1]
        features = np.column_stack((numeric, np.array([["a", "same"], ["a", "same"], ["b", "same"], ["b", "same"]])))
        labels = np.array([0, 0, 1, 1])
        # In-cluster cost is 1; across-cluster mean cost is gamma + 0.5.
        self.assertAlmostEqual(cluster_silhouette(features, labels, "kprototypes", gamma=4), 7 / 9)
        self.assertAlmostEqual(cluster_silhouette(features, labels, "kprototypes", gamma=0.1), -0.4)

    def test_silhouette_work_is_bounded_and_reproducible_for_large_cohorts(self):
        features = np.arange(10000).reshape(-1, 1)
        labels = np.arange(10000) % 3
        with patch("app.training.silhouette_score", return_value=0.5) as evaluate:
            cluster_silhouette(features, labels)
            first = evaluate.call_args.args[0].copy()
            cluster_silhouette(features, labels)
            self.assertEqual(len(first), 2000)
            np.testing.assert_array_equal(first, evaluate.call_args.args[0])

    def test_changed_consent_prevents_promotion(self):
        train(self.profiles, track=False)
        with self.assertRaises(ValueError):
            promote("Reviewer", self.profiles[:-1])

    def test_additions_preserve_reviewed_members_and_support_real_inference(self):
        for algorithm in ("kmeans", "kprototypes"):
            with self.subTest(algorithm=algorithm):
                train(self.profiles, algorithm, track=False)
                new = {**self.profiles[0], "ownerId": "new-farmer", "hectares": 2.05}
                enlarged = [*self.profiles, new]
                # New members may join while an otherwise unchanged training cohort is reviewed.
                promote("Reviewer", enlarged)
                original = active_segment(self.profiles[0], enlarged)
                inferred = active_segment(new, enlarged)
                self.assertEqual(original["assignment"], "training_cohort")
                self.assertEqual(inferred["assignment"], "predicted")
                self.assertEqual(inferred["segmentId"], original["segmentId"])
                self.assertEqual(inferred["method"], algorithm)
                resolved = active_segments(enlarged)
                self.assertEqual(len(resolved), 46)
                self.assertEqual(resolved["new-farmer"], inferred)

    def test_inference_rejects_unknown_categories_outliers_and_unconsented_profiles(self):
        for algorithm in ("kmeans", "kprototypes"):
            with self.subTest(algorithm=algorithm):
                train(self.profiles, algorithm, track=False)
                promote("Reviewer", self.profiles)
                for changes in ({"crop": "cabbage"}, {"province": "Gauteng"},
                                {"hectares": 1000000, "capacityKg": 1000000000}, {"analyticsConsent": False}):
                    new = {**self.profiles[0], "ownerId": "new-farmer", **changes}
                    enlarged = [*self.profiles, new]
                    self.assertIsNone(active_segment(new, enlarged))
                    self.assertEqual(active_segment(self.profiles[0], enlarged)["method"], algorithm)
                missing = {**self.profiles[0], "ownerId": "not-in-current-cohort"}
                self.assertIsNone(active_segment(missing, self.profiles))

    def test_changed_training_member_invalidates_model_until_retrained(self):
        train(self.profiles, track=False)
        promote("Reviewer", self.profiles)
        changed = [{**self.profiles[0], "hectares": 3}, *self.profiles[1:]]
        self.assertIsNone(active_segment(self.profiles[1], changed))
        self.assertEqual(active_segments(changed), {})
        with self.assertRaises(ValueError):
            promote("Reviewer", changed)
        # A stale individual API read must not be served against a different current profile.
        self.assertIsNone(active_segment(changed[0], self.profiles))

    def test_hdbscan_unseen_profiles_fall_back_without_disabling_reviewed_members(self):
        train(self.profiles, "hdbscan", track=False)
        promote("Reviewer", self.profiles)
        new = {**self.profiles[0], "ownerId": "new-farmer"}
        enlarged = [*self.profiles, new]
        self.assertIsNone(active_segment(new, enlarged))
        self.assertEqual(active_segment(self.profiles[0], enlarged)["method"], "hdbscan")

    def test_missing_or_damaged_artifacts_fall_back_and_are_detected(self):
        self.assertEqual(artifact_versions(), {"candidate": None, "active": None})
        report = train(self.profiles, track=False)
        promote("Reviewer", self.profiles)
        self.assertEqual(artifact_versions()["active"], report["version"])
        active = Path(self.directory.name) / "active.joblib"
        active.write_bytes(b"\x80broken")
        self.assertIsNone(active_segment(self.profiles[0], self.profiles))
        active.unlink()
        self.assertIsNone(artifact_versions()["active"])

    def test_mlflow_records_a_real_experiment(self):
        import mlflow
        uri = "sqlite:///" + (Path(self.directory.name) / "mlflow.db").as_posix()
        with patch.dict(os.environ, {"MLFLOW_TRACKING_URI": uri}):
            result = train(self.profiles, track=True)
            experiment = mlflow.get_experiment_by_name("agritech-market-segmentation")
            runs = mlflow.search_runs(experiment_ids=[experiment.experiment_id])
            self.assertIn(result["version"], runs["params.version"].tolist())
            # Release SQLite's pooled handles before Windows removes the temporary directory.
            mlflow.tracking.MlflowClient(tracking_uri=uri)._tracking_client.store.engine.dispose()
