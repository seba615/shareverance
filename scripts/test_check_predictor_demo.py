import unittest
from check_predictor_demo import verify_forecast


def response():
    quality = {'status': 'NOT_EVALUATED', 'evaluation_id': None, 'mae_minor': None,
               'wape_percentage': None, 'samples': None}
    points = [{'horizon': h, 'target_month': f'2025-{h:02}', 'estimated_amount_minor': 100,
               'quality': quality.copy()} for h in range(1, 5)]
    return {'organization_id': 1, 'origin_month': '2024-12', 'horizon_months': 4,
            'currency': 'ARS', 'contract_version': '2', 'simulated': True,
            'organization': {'status': 'OK', 'method': 'mock', 'forecasts': points}, 'categories': []}


class DemoForecastTests(unittest.TestCase):
    def test_correct_response_and_year_transition(self):
        verify_forecast(response(), 1, '2024-12', 4, {None: 100})

    def test_wrong_horizon_and_discontinuous_targets_rejected(self):
        r = response()
        with self.assertRaises(ValueError):
            verify_forecast(r, 1, '2024-12', 6, {None: 100})
        r['organization']['forecasts'][2]['target_month'] = '2025-05'
        with self.assertRaises(ValueError):
            verify_forecast(r, 1, '2024-12', 4, {None: 100})

    def test_insufficient_points_must_be_null(self):
        r = response()
        r['organization']['status'] = 'INSUFFICIENT_HISTORY'
        with self.assertRaises(ValueError):
            verify_forecast(r, 1, '2024-12', 4, {})
        for p in r['organization']['forecasts']:
            p['estimated_amount_minor'] = None
        verify_forecast(r, 1, '2024-12', 4, {})

    def test_unknown_quality_is_not_zero_error(self):
        r = response()
        r['organization']['forecasts'][0]['quality']['mae_minor'] = 0
        with self.assertRaises(ValueError):
            verify_forecast(r, 1, '2024-12', 4, {None: 100})
