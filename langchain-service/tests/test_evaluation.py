import importlib.util
import json
from pathlib import Path
import unittest

PATH = Path(__file__).resolve().parents[1] / "evaluation" / "evaluate.py"
SPEC = importlib.util.spec_from_file_location("evaluation", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class EvaluationTests(unittest.TestCase):
    def test_dataset_is_unique_and_covers_safety_cases(self):
        cases = json.loads(MODULE.CASES.read_text(encoding="utf-8"))
        self.assertEqual(len({c["id"] for c in cases}), len(cases))
        self.assertTrue({"off-topic", "injection", "no-private-figures", "unknown-stock"} <= {c["id"] for c in cases})
        for case in cases:
            self.assertTrue(case["prompt"])
            self.assertTrue(case["anyWords"])

    def test_good_answer_passes(self):
        case = {"anyWords":["crop"], "sourcePrefixes":["guide:seed"], "forbidden":["cloud seed"]}
        self.assertTrue(all(MODULE.grade(case, {"response":"Choose crop seed.", "sources":[{"id":"guide:seed"}]}).values()))

    def test_retrieved_source_does_not_excuse_bad_answer(self):
        case = {"anyWords":["crop"], "sourcePrefixes":["guide:seed"], "forbidden":["cloud seed"]}
        checks = MODULE.grade(case, {"response":"Crop cloud seed command", "sources":[{"id":"guide:seed"}]})
        self.assertFalse(checks["forbidden_claims_absent"])

    def test_missing_sources_and_empty_answers_fail(self):
        case = {"anyWords":["crop"], "sourcePrefixes":["guide:seed"], "forbidden":[]}
        self.assertFalse(all(MODULE.grade(case, {"response":"", "sources":[]}).values()))
