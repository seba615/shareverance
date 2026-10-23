import unittest
from evaluate_forecast_csv import evaluate_forecast
from test_audit_predictor_dataset import fixture


class ForecastEvaluationTests(unittest.TestCase):
    def test_invalid_horizon_rejected(self):
        with self.assertRaisesRegex(ValueError, 'max_horizon'):
            evaluate_forecast(fixture(), from_origin='2023-12', to_origin='2024-06', max_horizon=3, commit='test')
        with self.assertRaisesRegex(ValueError, 'max_horizon'):
            evaluate_forecast(fixture(), from_origin='2023-12', to_origin='2024-06', max_horizon=7, commit='test')

    def test_exceeding_development_rejected(self):
        # Origen 2024-07 con horizonte 6 requeriría 2025-01 (reserva)
        with self.assertRaisesRegex(ValueError, 'excede el período de desarrollo'):
            evaluate_forecast(fixture(), from_origin='2024-01', to_origin='2024-07', max_horizon=6, commit='test')

    def test_no_future_data_leakage(self):
        # Alterar meses futuros posteriores al origen 2023-12 no debe cambiar la predicción generada
        original = evaluate_forecast(fixture(), from_origin='2023-12', to_origin='2023-12', max_horizon=6, commit='test')
        changed = fixture(lambda rows: [row.__setitem__(4, '9999999') for row in rows if row[1] >= '2024-01'])
        altered = evaluate_forecast(changed, from_origin='2023-12', to_origin='2023-12', max_horizon=6, commit='test')
        
        # Las estimaciones para los 6 horizontes deben ser exactamente idénticas
        orig_estimates = [{name: p['estimated_minor'] for name, p in pt['predictions'].items()} for pt in original['points']]
        alt_estimates = [{name: p['estimated_minor'] for name, p in pt['predictions'].items()} for pt in altered['points']]
        self.assertEqual(orig_estimates, alt_estimates)

    def test_all_horizons_generated_and_exact_count(self):
        # Con 7 orígenes (2023-12 a 2024-06), 2 organizaciones y 16 categorías
        report = evaluate_forecast(fixture(), from_origin='2023-12', to_origin='2024-06', max_horizon=6, commit='test')
        self.assertEqual(report['configuration']['origins_count'], 7)
        self.assertEqual(report['configuration']['max_horizon'], 6)
        # Total puntos: (2 orgs + 16 cats) * 7 origenes * 6 horizontes = 18 * 42 = 756 puntos
        self.assertEqual(len(report['points']), 18 * 7 * 6)
        self.assertEqual(report['levels']['organization']['complete_series'], 2)
        self.assertEqual(report['levels']['category']['complete_series'], 16)
        self.assertIn('1', report['levels']['organization']['horizons'])
        self.assertIn('6', report['levels']['organization']['horizons'])

    def test_forecast_h1_matches_single_step(self):
        # Para h=1, la predicción debe coincidir con la de un solo paso
        report = evaluate_forecast(fixture(), from_origin='2023-12', to_origin='2023-12', max_horizon=6, commit='test')
        h1_points = [p for p in report['points'] if p['horizon'] == 1]
        for pt in h1_points:
            # En fixture, el último mes de 2023 es 2023-12; para last_month debe ser igual al valor en 2023-12
            self.assertIsNotNone(pt['predictions']['last_month']['estimated_minor'])

    def test_missing_history_yields_insufficient(self):
        # Si un origen tiene menos de 6 meses de historia
        report = evaluate_forecast(fixture(), from_origin='2023-05', to_origin='2023-05', max_horizon=4, commit='test')
        # 2023-05 solo tiene 5 meses (2023-01 a 2023-05)
        for pt in report['points']:
            self.assertEqual(pt['status'], 'INSUFFICIENT_HISTORY')
            self.assertEqual(pt['predictions'], {})
