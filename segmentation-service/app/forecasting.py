"""Monthly volume outlooks with chronological evaluation and an explicit seasonal baseline.

Backtests describe errors on recorded market observations, not calibrated future
demand probabilities. Missing observations are never treated as zero demand.
"""
from datetime import date
import math
import warnings

MIN_TRAIN_MONTHS = 24
MIN_BACKTEST_MONTHS = 36
MAX_BACKTEST_MONTHS = 12
MIN_INTERVAL_SAMPLES = 5


def _seasonal_naive(history, horizon):
    return [float(history.iloc[-12 + step % 12]) for step in range(horizon)]


def _holt_winters(history, horizon):
    from statsmodels.tsa.holtwinters import ExponentialSmoothing
    from statsmodels.tools.sm_exceptions import ConvergenceWarning

    with warnings.catch_warnings(record=True) as captured:
        warnings.simplefilter("always", ConvergenceWarning)
        # Perfectly periodic series can have zero residual variance (undefined AIC).
        warnings.simplefilter("ignore", RuntimeWarning)
        fit = ExponentialSmoothing(history, trend="add", seasonal="add", seasonal_periods=12).fit()
    if any(issubclass(w.category, ConvergenceWarning) for w in captured):
        raise ValueError("Holt-Winters fit did not converge")
    if not fit.mle_retvals.get("success", True):
        raise ValueError("Holt-Winters fit did not converge")
    values = [float(value) for value in fit.forecast(horizon)]
    if not all(math.isfinite(value) for value in values):
        raise ValueError("Holt-Winters returned nonfinite predictions")
    return [max(0.0, value) for value in values]


def _metrics(pairs, expected_samples, successful_folds):
    absolute_error = sum(abs(actual - prediction) for actual, prediction, _ in pairs)
    actual_total = sum(abs(actual) for actual, _, _ in pairs)
    return {"maeKg": absolute_error / len(pairs) if pairs else None,
            "wape": absolute_error / actual_total if actual_total else None,
            "samples": len(pairs), "expectedSamples": expected_samples,
            "sampleCoverage": len(pairs) / expected_samples if expected_samples else 0,
            "successfulFolds": successful_folds}


def rolling_backtest(series, horizon=3):
    """Expand training through each origin; evaluate only later, completed months.

    At most the last 12 months are held out, bounding model fitting to 12 folds.
    The same folds and forecast horizons are used for both methods. Missing model
    predictions lower its reported coverage and prevent selecting that model.
    """
    empty = {"status": "not_backtested", "minimumHistoryMonths": MIN_BACKTEST_MONTHS,
             "folds": 0, "evaluatedMonths": 0, "expectedSamples": 0,
             "wapeUnit": "fraction", "methods": {}, "modelBacktestPassed": False}
    errors = {"holt_winters": [], "seasonal_naive": []}
    if len(series) < MIN_BACKTEST_MONTHS:
        return empty, errors
    start = max(MIN_TRAIN_MONTHS, len(series) - MAX_BACKTEST_MONTHS)
    expected_samples = 0
    successes = {method: 0 for method in errors}
    for origin in range(start, len(series)):
        history = series.iloc[:origin]
        steps = min(horizon, len(series) - origin)
        expected_samples += steps
        for method, predict in (("seasonal_naive", _seasonal_naive), ("holt_winters", _holt_winters)):
            try:
                predictions = predict(history, steps)
            except (ValueError, ArithmeticError, RuntimeError):
                continue
            successes[method] += 1
            errors[method].extend((float(series.iloc[origin + step]), prediction, step + 1)
                                  for step, prediction in enumerate(predictions))
    report = {**empty, "status": "backtested", "folds": len(series) - start,
              "evaluatedMonths": len(series) - start, "expectedSamples": expected_samples,
              "firstEvaluationMonth": series.index[start].date().isoformat(),
              "lastEvaluationMonth": series.index[-1].date().isoformat(),
              "minimumTrainingMonths": start,
              "methods": {method: _metrics(pairs, expected_samples, successes[method])
                          for method, pairs in errors.items()},
              "notice": "Rolling chronological historical errors; not proof of future accuracy or total regional demand"}
    if report["methods"]["holt_winters"]["sampleCoverage"] < 1:
        report["status"] = "model_backtest_incomplete"
    return report, errors


def forecast_months(observations, horizon=3, today=None):
    import numpy as np
    import pandas as pd

    if not isinstance(horizon, int) or isinstance(horizon, bool) or not 1 <= horizon <= 12:
        raise ValueError("Choose a forecast horizon from 1 to 12 months")
    validation = {"status": "not_backtested", "minimumHistoryMonths": MIN_BACKTEST_MONTHS,
                  "modelBacktestPassed": False, "methods": {}}
    if not observations:
        return {"method": "insufficient_history", "forecast": [], "qualityStatus": "withheld",
                "validation": validation}
    today = today or date.today()
    this_month = pd.Timestamp(today.replace(day=1))
    completed = {}
    invalid = False
    for row in observations:
        month = pd.Timestamp(row["month"])
        if month >= this_month:
            continue
        value = float(row["quantityKg"])
        if month.day != 1 or month != month.normalize() or month in completed or not math.isfinite(value) or value < 0:
            invalid = True
        completed[month] = value
    if not completed:
        return {"method": "insufficient_history", "forecast": [], "qualityStatus": "withheld",
                "validation": validation}
    series = pd.Series(completed).sort_index()
    complete = series.reindex(pd.date_range(series.index.min(), series.index.max(), freq="MS"))
    coverage = {"completedObservedMonths": len(series), "expectedMonths": len(complete),
                "missingMonths": int(complete.isna().sum()),
                "firstObservedMonth": series.index[0].date().isoformat(),
                "lastObservedMonth": series.index[-1].date().isoformat(),
                "latestRequiredMonth": (this_month - pd.offsets.MonthBegin(1)).date().isoformat()}
    if invalid or len(complete) < MIN_TRAIN_MONTHS or complete.isna().any() or series.index.max() != this_month - pd.offsets.MonthBegin(1):
        finite = series[series.map(lambda value: math.isfinite(value) and value >= 0)]
        return {"method": "seasonal_averages", "forecast": [], "qualityStatus": "withheld",
                "averages": {str(month): float(value) for month, value in finite.groupby(finite.index.month).mean().items()},
                "dataCoverage": coverage, "validation": validation,
                "notice": "Forecast withheld until 24 consecutive completed observed months through the previous month are available; values must be finite, nonnegative and unique by month"}
    validation, errors = rolling_backtest(complete, horizon)
    method = "seasonal_naive"
    quality = "experimental_baseline"
    if validation["status"] != "not_backtested":
        baseline = validation["methods"]["seasonal_naive"]
        candidate = validation["methods"]["holt_winters"]
        tolerance = max(1e-8, baseline["maeKg"] * 1e-6)
        if candidate["sampleCoverage"] == 1 and candidate["maeKg"] < baseline["maeKg"] - tolerance:
            method = "holt_winters"
            quality = "backtested_model_improved"
            validation["modelBacktestPassed"] = True
        else:
            quality = "backtested_baseline_preferred"
    try:
        predictions = _holt_winters(complete, horizon) if method == "holt_winters" else _seasonal_naive(complete, horizon)
    except (ValueError, ArithmeticError, RuntimeError):
        method, quality = "seasonal_naive", "model_fit_failed_baseline"
        validation["modelBacktestPassed"] = False
        predictions = _seasonal_naive(complete, horizon)
    validation["selectedMethod"] = method
    validation["selectionBasis"] = ("Lower mean absolute error on identical chronological forecast horizons; ties prefer the seasonal baseline"
                                    if validation["status"] != "not_backtested" else
                                    "Seasonal baseline only; fewer than 36 completed months prevents a backtest")
    validation["intervals"] = {"method": "historical_absolute_error_percentile", "percentile": 0.9,
                               "minimumSamplesPerHorizon": MIN_INTERVAL_SAMPLES,
                               "notice": "Empirical historical error ranges, not calibrated confidence intervals or guaranteed bounds"}
    forecast = []
    for step, (month, prediction) in enumerate(zip(pd.date_range(this_month, periods=horizon, freq="MS"), predictions), 1):
        residuals = [abs(actual - predicted) for actual, predicted, ahead in errors[method] if ahead == step]
        radius = float(np.quantile(residuals, 0.9)) if len(residuals) >= MIN_INTERVAL_SAMPLES else None
        forecast.append({"month": month.date().isoformat(), "quantityKg": round(prediction, 2),
                         "lowerKg": round(max(0.0, prediction - radius), 2) if radius is not None else None,
                         "upperKg": round(prediction + radius, 2) if radius is not None else None,
                         "errorSampleCount": len(residuals)})
    return {"method": method, "forecast": forecast, "qualityStatus": quality,
            "validation": validation, "dataCoverage": coverage,
            "notice": ("Experimental seasonal baseline; no chronological backtest is available yet"
                       if validation["status"] == "not_backtested" else
                       "Chronologically backtested volume outlook, not guaranteed buyer demand or a calibrated prediction interval")}
