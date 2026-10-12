import os
import tempfile
import unittest
from unittest.mock import patch

from app.training import active_segment, drift, invalidate_artifacts, promote, train
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

    def test_real_kmeans_train_review_and_consent_invalidation(self):
        result = train(self.profiles, track=False)
        self.assertTrue(result["qualityGate"])
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

    def test_changed_consent_prevents_promotion(self):
        train(self.profiles, track=False)
        with self.assertRaises(ValueError):
            promote("Reviewer", self.profiles[:-1])

    def test_mlflow_records_a_real_experiment(self):
        from pathlib import Path
        import mlflow
        uri = "sqlite:///" + (Path(self.directory.name) / "mlflow.db").as_posix()
        with patch.dict(os.environ, {"MLFLOW_TRACKING_URI": uri}):
            result = train(self.profiles, track=True)
            experiment = mlflow.get_experiment_by_name("agritech-market-segmentation")
            runs = mlflow.search_runs(experiment_ids=[experiment.experiment_id])
            self.assertIn(result["version"], runs["params.version"].tolist())
            # Release SQLite's pooled handles before Windows removes the temporary directory.
            mlflow.tracking.MlflowClient(tracking_uri=uri)._tracking_client.store.engine.dispose()
