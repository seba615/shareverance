import unittest
from evaluate_predictor_csv import evaluate
from test_audit_predictor_dataset import fixture


class CsvEvaluationTests(unittest.TestCase):
    def test_allow_reserve_flag_enables_2025_targets(self):
        report = evaluate(fixture(), '2025-01', '2025-01', 'test', allow_reserve=True)
        self.assertTrue(report['reserve_evaluated'])
        self.assertEqual(report['scope'], 'synthetic_reserve_evaluation')
        self.assertEqual(report['levels']['organization']['complete_series'], 2)
        self.assertEqual(report['levels']['category']['complete_series'], 16)
    def test_reserve_is_blocked_before_reading_data(self):
        with self.assertRaisesRegex(ValueError, 'reserva'):
            evaluate(b'invalid', '2025-01', '2025-12', 'test')

    def test_no_future_leakage(self):
        original = evaluate(fixture(), '2024-01', '2024-01', 'test')
        changed = fixture(lambda rows: [row.__setitem__(4, '999999') for row in rows if row[1] >= '2024-02'])
        altered = evaluate(changed, '2024-01', '2024-01', 'test')
        self.assertEqual(original['points'], altered['points'])
        self.assertEqual(original['levels'], altered['levels'])

    def test_levels_and_no_promotion(self):
        report = evaluate(fixture(), '2024-01', '2024-12', 'test')
        self.assertEqual(report['levels']['organization']['complete_series'], 2)
        self.assertEqual(report['levels']['category']['complete_series'], 16)
        self.assertEqual(len(report['points']), 18 * 12)
        self.assertIsNone(report['accepted'])
        self.assertFalse(report['reserve_evaluated'])
        for point in report['points']:
            self.assertEqual(len(point['predictions']), 3)

    def test_summary_uses_exact_equal_series_mae(self):
        report = evaluate(fixture(), '2024-01', '2024-01', 'test')
        metric = report['levels']['organization']['metrics']['last_month']
        self.assertEqual(metric['equal_series_mae_ars'], 0.54)
        self.assertEqual(metric['equal_series_mae_minor_fraction'], {'numerator': 54, 'denominator': 1})

    def test_six_month_boundary(self):
        report = evaluate(fixture(), '2023-06', '2023-07', 'test')
        for point in report['points']:
            self.assertEqual(point['status'], 'OK' if point['target_month'] == '2023-07' else 'INSUFFICIENT_HISTORY')

    def test_gap_does_not_become_zero(self):
        raw = fixture(lambda rows: rows.__setitem__(slice(None), [r for r in rows if r[1] != '2023-12']))
        report = evaluate(raw, '2024-01', '2024-01', 'test')
        self.assertTrue(all(p['status'] == 'INSUFFICIENT_HISTORY' for p in report['points']))
        self.assertEqual(report['levels']['organization']['complete_series'], 0)
        self.assertIsNone(report['levels']['category']['metrics']['ses'])

    def test_zero_metric_denominator(self):
        raw = fixture(lambda rows: [r.__setitem__(4, '0') for r in rows])
        report = evaluate(raw, '2024-01', '2024-01', 'test')
        metric = report['levels']['organization']['metrics']['ses']
        self.assertEqual(metric['equal_series_mae_ars'], 0)
        self.assertIsNone(metric['pooled_wape_percentage'])

    def test_missing_target_not_silently_dropped(self):
        raw = fixture(lambda rows: rows.__setitem__(slice(None), [r for r in rows if r[1] != '2024-01']))
        report = evaluate(raw, '2024-01', '2024-01', 'test')
        self.assertEqual(report['levels']['category']['excluded_series'], 16)
        self.assertTrue(all(p['status'] == 'MISSING_TARGET' for p in report['points']))
