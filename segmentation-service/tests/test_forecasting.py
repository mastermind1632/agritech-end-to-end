from datetime import date
import json
import unittest
from unittest.mock import patch

import pandas as pd

from app.forecasting import forecast_months, rolling_backtest


def observations(months=36, trend=0):
    return [{"month": month.date().isoformat(), "quantityKg": 100 + (index % 12) * 10 + index * trend}
            for index, month in enumerate(pd.date_range(end="2025-12-01", periods=months, freq="MS"))]


class ForecastingTests(unittest.TestCase):
    def test_two_years_use_an_explicitly_unbacktested_baseline_without_error_ranges(self):
        result = forecast_months(observations(24), today=date(2026, 1, 15))
        self.assertEqual(result["method"], "seasonal_naive")
        self.assertEqual(result["qualityStatus"], "experimental_baseline")
        self.assertEqual(result["validation"]["status"], "not_backtested")
        self.assertFalse(result["validation"]["modelBacktestPassed"])
        self.assertEqual([row["quantityKg"] for row in result["forecast"]], [100, 110, 120])
        self.assertTrue(all(row["lowerKg"] is None and row["upperKg"] is None for row in result["forecast"]))

    def test_actual_holt_winters_is_selected_when_it_improves_the_chronological_baseline(self):
        result = forecast_months(observations(trend=5), today=date(2026, 1, 15))
        self.assertEqual(result["method"], "holt_winters")
        validation = result["validation"]
        self.assertTrue(validation["modelBacktestPassed"])
        candidate, baseline = (validation["methods"][name] for name in ("holt_winters", "seasonal_naive"))
        self.assertLess(candidate["maeKg"], baseline["maeKg"])
        self.assertLess(candidate["wape"], baseline["wape"])
        self.assertEqual(candidate["sampleCoverage"], 1)
        self.assertEqual(candidate["samples"], 33)
        self.assertEqual(validation["evaluatedMonths"], 12)
        self.assertEqual([r["errorSampleCount"] for r in result["forecast"]], [12, 11, 10])
        self.assertTrue(all(r["lowerKg"] <= r["quantityKg"] <= r["upperKg"] for r in result["forecast"]))
        self.assertIn("not calibrated", validation["intervals"]["notice"])

    def test_same_season_each_year_prefers_the_exact_seasonal_baseline(self):
        result = forecast_months(observations(), today=date(2026, 1, 15))
        self.assertEqual(result["method"], "seasonal_naive")
        self.assertEqual(result["qualityStatus"], "backtested_baseline_preferred")
        self.assertEqual(result["validation"]["methods"]["seasonal_naive"]["maeKg"], 0)
        self.assertFalse(result["validation"]["modelBacktestPassed"])

    def test_rolling_origins_never_train_on_the_month_they_evaluate(self):
        series = pd.Series(range(36), index=pd.date_range("2023-01-01", periods=36, freq="MS"))
        histories = []
        def predict(history, horizon):
            histories.append(history.copy())
            return [float(history.iloc[-1] + step) for step in range(1, horizon + 1)]
        with patch("app.forecasting._holt_winters", side_effect=predict):
            report, errors = rolling_backtest(series)
        self.assertEqual([len(history) for history in histories], list(range(24, 36)))
        for history in histories:
            self.assertEqual(history.index[-1], series.index[len(history) - 1])
            self.assertLess(history.index[-1], series.index[len(history)])
        self.assertEqual(report["methods"]["holt_winters"]["maeKg"], 0)
        self.assertEqual(report["expectedSamples"], 33)
        self.assertEqual(len(errors["seasonal_naive"]), len(errors["holt_winters"]))

    def test_worse_model_never_supersedes_a_better_baseline(self):
        with patch("app.forecasting._holt_winters", side_effect=lambda history, horizon: [999999.0] * horizon):
            result = forecast_months(observations(trend=2), today=date(2026, 1, 15))
        self.assertEqual(result["method"], "seasonal_naive")
        self.assertFalse(result["validation"]["modelBacktestPassed"])
        self.assertEqual(result["validation"]["methods"]["holt_winters"]["sampleCoverage"], 1)

    def test_incomplete_model_backtest_reports_missing_predictions_and_uses_baseline(self):
        def predict(history, horizon):
            if len(history) == 30:
                raise ValueError("Synthetic fit failure")
            return [0.0] * horizon
        with patch("app.forecasting._holt_winters", side_effect=predict):
            result = forecast_months(observations(), today=date(2026, 1, 15))
        self.assertEqual(result["method"], "seasonal_naive")
        self.assertEqual(result["validation"]["status"], "model_backtest_incomplete")
        metrics = result["validation"]["methods"]["holt_winters"]
        self.assertEqual(metrics["samples"], 30)
        self.assertAlmostEqual(metrics["sampleCoverage"], 30 / 33)
        self.assertFalse(result["validation"]["modelBacktestPassed"])

    def test_final_model_fit_failure_falls_back_even_after_a_good_backtest(self):
        rows = [{"month": r["month"], "quantityKg": 100 + index} for index, r in enumerate(observations())]
        def predict(history, horizon):
            if len(history) == 36:
                raise ValueError("Latest fit failed")
            return [float(history.iloc[-1] + step) for step in range(1, horizon + 1)]
        with patch("app.forecasting._holt_winters", side_effect=predict):
            result = forecast_months(rows, today=date(2026, 1, 15))
        self.assertEqual(result["method"], "seasonal_naive")
        self.assertEqual(result["qualityStatus"], "model_fit_failed_baseline")
        self.assertFalse(result["validation"]["modelBacktestPassed"])

    def test_small_error_samples_do_not_claim_an_interval_for_distant_horizons(self):
        with patch("app.forecasting._holt_winters", side_effect=lambda history, horizon: [0.0] * horizon):
            result = forecast_months(observations(), horizon=12, today=date(2026, 1, 15))
        self.assertEqual(result["forecast"][-1]["errorSampleCount"], 1)
        self.assertIsNone(result["forecast"][-1]["lowerKg"])
        self.assertIsNotNone(result["forecast"][0]["lowerKg"])

    def test_zero_observed_volume_has_defined_mae_but_undefined_wape_and_valid_json(self):
        rows = [{**row, "quantityKg": 0} for row in observations()]
        with patch("app.forecasting._holt_winters", side_effect=lambda history, horizon: [0.0] * horizon):
            result = forecast_months(rows, today=date(2026, 1, 15))
        self.assertEqual(result["validation"]["methods"]["seasonal_naive"]["maeKg"], 0)
        self.assertIsNone(result["validation"]["methods"]["seasonal_naive"]["wape"])
        json.dumps(result, allow_nan=False)

    def test_backtest_work_is_bounded_to_twelve_recent_origins(self):
        with patch("app.forecasting._holt_winters", side_effect=lambda history, horizon: [0.0] * horizon) as model:
            result = forecast_months(observations(120), today=date(2026, 1, 15))
        self.assertEqual(model.call_count, 12)
        self.assertEqual(result["validation"]["minimumTrainingMonths"], 108)

    def test_invalid_or_duplicate_monthly_values_withhold_forecasts(self):
        rows = observations()
        for invalid in (float("nan"), float("inf"), -1):
            result = forecast_months([*rows[:-1], {**rows[-1], "quantityKg": invalid}], today=date(2026, 1, 15))
            self.assertEqual(result["forecast"], [])
            self.assertEqual(result["qualityStatus"], "withheld")
            json.dumps(result, allow_nan=False)
        self.assertEqual(forecast_months([*rows, rows[-1]], today=date(2026, 1, 15))["forecast"], [])

    def test_invalid_horizons_are_rejected(self):
        for horizon in (0, -1, 13, True, 1.5):
            with self.assertRaises(ValueError):
                forecast_months(observations(), horizon=horizon, today=date(2026, 1, 15))
