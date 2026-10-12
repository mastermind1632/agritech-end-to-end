import math
import unittest
from datetime import date

from app.engine import cohort_fingerprint, demand_summary, distance_km, experiment_arm, feature_rows, forecast_months, matches, services
from app.models import FarmProfile, MarketRecord


def profile(owner="farmer-a", **changes):
    return {"ownerId": owner, "crop": "tomato", "province": "Limpopo", "district": "Capricorn",
            "latitude": -23.9, "longitude": 29.45, "hectares": 2, "capacityKg": 1000,
            "plantingMonth": 7, "harvestMonth": 10, "deliveryRadiusKm": 100,
            "businessRegistered": False, "consistentProduction": False,
            "analyticsConsent": True, "expenseConsent": False, **changes}


def record(key="request-a", **changes):
    return {"id": key, "ownerId": "buyer-b", "kind": "demand", "crop": "tomato", "quantityKg": 500,
            "province": "Limpopo", "district": "Capricorn", "latitude": -23.91, "longitude": 29.46,
            "availableFrom": "2026-10-01", "availableUntil": "2026-10-31", "pricePerKg": 10,
            "displayName": "Buyer B", "publicContact": "", **changes}


class EngineTest(unittest.TestCase):
    def test_matching_filters_crop_radius_volume_dates_and_ownership(self):
        bad = [record("wrong-crop", crop="maize"), record("far", latitude=-30),
               record("volume", quantityKg=1001), record("expired", availableUntil="2026-09-30"),
               record("future", availableFrom="2026-11-01", availableUntil="2026-11-30"),
               record("own", ownerId="farmer-a"), record("offer", kind="offer")]
        result = matches(profile(), [*bad, record()], "farmer-a", date(2026, 10, 12))
        self.assertEqual([r["id"] for r in result], ["request-a"])

    def test_next_harvest_handles_year_boundary(self):
        found = matches(profile(harvestMonth=1), [record(availableFrom="2027-01-01", availableUntil="2027-01-31")], "farmer-a", date(2026, 12, 12))
        self.assertEqual(len(found), 1)

    def test_empty_matches_do_not_invent_buyers(self):
        self.assertEqual(matches(profile(), [], "farmer-a"), [])

    def test_distance_and_cyclical_features(self):
        self.assertAlmostEqual(distance_km(profile(), profile()), 0)
        rows = feature_rows([profile(plantingMonth=1), profile(plantingMonth=12), profile(plantingMonth=7)])
        def d(a, b):
            return math.hypot(a["plantingMonth_sin"] - b["plantingMonth_sin"], a["plantingMonth_cos"] - b["plantingMonth_cos"])
        self.assertLess(d(rows[0], rows[1]), d(rows[0], rows[2]))

    def test_service_rules_respect_consent_and_no_export_certification(self):
        self.assertEqual([s["service"] for s in services(profile(), {"current": 200, "previous": 100})], ["business_companion"])
        self.assertIn("expense_insights", [s["service"] for s in services(profile(expenseConsent=True), {"current": 200, "previous": 100})])
        self.assertEqual(services(profile(expenseConsent=True, businessRegistered=True), {"current": 200, "previous": 0}), [])
        self.assertEqual(services(profile(hectares=100, crop="citrus", consistentProduction=True, businessRegistered=True))[0]["service"], "export_readiness")

    def test_fingerprint_ignores_nonconsented_profiles(self):
        self.assertEqual(cohort_fingerprint([profile()]), cohort_fingerprint([profile(), profile("no-consent", analyticsConsent=False)]))
        self.assertNotEqual(cohort_fingerprint([profile()]), cohort_fingerprint([profile(latitude=-24)]))

    def test_exploration_uses_only_qualified_underexposed_candidates(self):
        rows = [record(str(i), quantityKg=500-i) for i in range(12)]
        exposure = {str(i): 100 for i in range(11)}
        found = matches(profile(), rows, "farmer-a", date(2026, 10, 12), "exploration", exposure)
        self.assertEqual(len(found), 10)
        self.assertIn("11", [r["id"] for r in found])
        self.assertEqual(experiment_arm("farmer-a", True), experiment_arm("farmer-a", True))
        self.assertEqual(experiment_arm("farmer-a", False), "control")

    def test_demand_uses_weighted_prices_and_separates_searches(self):
        prices = [{"crop": "tomato", "province": "Limpopo", "observedOn": "2026-10-01", "pricePerKg": p,
                   "volumeKg": v, "sourceUrl": "https://example.org/market"} for p, v in [(10, 100), (20, 300)]]
        searches = [{"crop": "tomato", "province": "Limpopo", "occurredOn": "2026-10-12", "resultCount": 0}]
        result = demand_summary([record()], searches, prices, date(2026, 10, 12))[0]
        self.assertEqual(result["observedPricePerKg"], 17.5)
        self.assertEqual(result["buyerRequests"], 1)
        self.assertEqual(result["failedSearches"], 1)
        self.assertIn("Unknown", result["profitability"])
        buyer_search = {**searches[0], "kind": "demand"}
        self.assertEqual(demand_summary([], [buyer_search], [], date(2026, 10, 12)), [])

    def test_forecast_requires_observed_complete_history(self):
        self.assertEqual(forecast_months([])["method"], "insufficient_history")
        self.assertEqual(forecast_months([{"month": "2025-01-01", "quantityKg": 100}])["method"], "seasonal_averages")
        observations = [{"month": f"{year}-{month:02}-01", "quantityKg": 100 + month * 10} for year in (2024, 2025) for month in range(1, 13)]
        self.assertEqual(len(forecast_months(observations)["forecast"]), 3)
        self.assertEqual(forecast_months(observations[:-2])["method"], "seasonal_averages")

    def test_profile_validation_rejects_invalid_coordinates_unknown_fields_and_nan(self):
        base = {k: v for k, v in profile().items() if k != "ownerId"}
        self.assertEqual(FarmProfile(**base).crop, "tomato")
        for changes in ({"latitude": 90}, {"crop": "anything"}, {"hectares": float("nan")}, {"ownerId": "another-farmer"}, {"harvestMonth": 0}):
            with self.assertRaises(ValueError):
                FarmProfile(**{**base, **changes})

    def test_listing_validates_dates_and_price_units(self):
        base = {k: v for k, v in record().items() if k not in ("id", "ownerId")}
        self.assertEqual(MarketRecord(**base).quantityKg, 500)
        for changes in ({"availableUntil": "2026-09-01"}, {"quantityKg": 0}, {"kind": "offer", "pricePerKg": None}):
            with self.assertRaises(ValueError):
                MarketRecord(**{**base, **changes})
