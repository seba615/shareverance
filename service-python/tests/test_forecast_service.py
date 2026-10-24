import unittest
from uuid import uuid4
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.services.predictions import PredictionService
from app.schemas.requests import ForecastRequest, CategoryCoverage, MonthlyHistory, CategoryTotal


class ForecastServiceTests(unittest.TestCase):
    def setUp(self):
        self.mock_service = PredictionService(MockPredictor())
        self.ses_service = PredictionService(SimpleExponentialSmoothingPredictor(alpha=0.3))

    def _make_request(self, num_months=12, horizon_months=4):
        cats = [
            CategoryCoverage(category_id=1, available_from_month="2023-01"),
            CategoryCoverage(category_id=2, available_from_month="2023-01"),
        ]
        history = []
        for i in range(1, num_months + 1):
            m = f"2023-{i:02d}"
            history.append(MonthlyHistory(
                month=m,
                category_totals=[
                    CategoryTotal(category_id=1, amount_minor=1000 * i),
                    CategoryTotal(category_id=2, amount_minor=2000 * i),
                ]
            ))
        return ForecastRequest(
            contract_version="1",
            request_id=uuid4(),
            organization_id=1,
            currency="ARS",
            origin_month=f"2023-{num_months:02d}",
            horizon_months=horizon_months,
            categories=cats,
            history=history,
        )

    def test_forecast_mock_produces_flat_trajectory(self):
        req = self._make_request(num_months=12, horizon_months=6)
        resp = self.mock_service.predict_forecast(req)
        self.assertEqual(resp.status, "OK")
        self.assertEqual(resp.origin_month, "2023-12")
        self.assertEqual(resp.horizon_months, 6)
        self.assertEqual(len(resp.organization.forecasts), 6)
        
        # Último valor total fue 1000*12 + 2000*12 = 36000
        for pt in resp.organization.forecasts:
            self.assertEqual(pt.estimated_amount_minor, 36000)
            self.assertEqual(pt.quality.status, "NOT_EVALUATED")

        self.assertEqual(resp.organization.forecasts[0].target_month, "2024-01")
        self.assertEqual(resp.organization.forecasts[5].target_month, "2024-06")

    def test_forecast_insufficient_history(self):
        req = self._make_request(num_months=5, horizon_months=4)
        resp = self.mock_service.predict_forecast(req)
        self.assertEqual(resp.status, "INSUFFICIENT_HISTORY")
        self.assertEqual(resp.organization.status, "INSUFFICIENT_HISTORY")
        self.assertEqual(len(resp.organization.forecasts), 4)
        for pt in resp.organization.forecasts:
            self.assertIsNone(pt.estimated_amount_minor)
            self.assertEqual(pt.quality.status, "NOT_EVALUATED")


if __name__ == "__main__":
    unittest.main()
