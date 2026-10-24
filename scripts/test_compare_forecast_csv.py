import unittest

from compare_forecast_csv import compare_forecast, comparison, summarize
from test_audit_predictor_dataset import fixture


def point(actual, last, mean, ses):
    return {'actual_minor': actual, 'predictions': {
        name: {'absolute_error_minor': abs(prediction - actual)}
        for name, prediction in [('last_month', last), ('historical_mean', mean), ('ses', ses)]}}


class ForecastComparisonTests(unittest.TestCase):
    def test_best_reference_is_per_series_and_ties_are_not_degraded(self):
        groups = [[point(100, 100, 110, 101)], [point(100, 110, 100, 100)]]
        result = summarize(groups)
        self.assertEqual(result['ses_stability']['worse_series'], 1)
        self.assertEqual(result['ses_stability']['tied_series'], 1)
        self.assertEqual(result['ses_stability']['worse_percentage'], 50)
        self.assertEqual(comparison(groups[1])['best_references'], ['historical_mean'])

    def test_unrounded_fraction_determines_relation(self):
        result = comparison([point(0, 0, 0, 0), point(0, 0, 0, 0), point(0, 0, 0, 1)])
        self.assertEqual(result['ses_delta_ars'], 0.0)
        self.assertEqual(result['ses_relation'], 'worse')
        self.assertEqual(result['ses_delta_minor_fraction'], {'numerator': 1, 'denominator': 3})
        self.assertIsNone(result['ses_relative_delta_percentage'])
        self.assertTrue(result['reference_zero_with_positive_ses_error'])

    def test_equal_series_mae_and_pooled_wape(self):
        result = summarize([[point(100, 110, 110, 110)],
                            [point(200, 230, 230, 230), point(200, 230, 230, 230)]])
        self.assertEqual(result['methods']['ses']['equal_series_mae_ars'], .20)
        self.assertEqual(result['methods']['ses']['pooled_wape_percentage'], 14)
        self.assertEqual(result['samples'], 3)
        self.assertIsNone(summarize([[point(0, 1, 1, 1)]])['methods']['ses']['pooled_wape_percentage'])

    def test_same_cohort_for_all_windows_and_counts(self):
        result = compare_forecast(fixture())
        for level, count in [('organization', 2), ('category', 16)]:
            summary = result['levels'][level]
            self.assertEqual(summary['eligible_series'], count)
            for h in (4, 5, 6):
                self.assertEqual(summary['windows'][str(h)]['samples'], count * 7 * h)
                self.assertEqual(summary['windows'][str(h)]['series_count'], count)
            self.assertEqual(summary['horizons']['6']['samples'], count * 7)
        self.assertIsNone(result['accepted'])
        self.assertIsNone(result['thresholds'])
        self.assertFalse(result['model_promoted'])
        self.assertFalse(result['final_evaluation_accredited'])

    def test_missing_h6_target_excludes_series_also_from_four_month_window(self):
        changed = fixture(lambda rows: rows.__setitem__(slice(None), [r for r in rows
                          if not (r[0] == '1' and r[2] == '1' and r[1] == '2024-12')]))
        result = compare_forecast(changed)
        self.assertEqual(result['levels']['organization']['excluded_series'], 1)
        self.assertEqual(result['levels']['category']['excluded_series'], 1)
        for h in (4, 5, 6):
            self.assertEqual(result['levels']['category']['windows'][str(h)]['series_count'], 15)
        excluded = [s for s in result['series'] if not s['eligible']]
        self.assertTrue(all(any(p['status'] == 'MISSING_TARGET' for p in s['excluded_points']) for s in excluded))

    def test_no_eligible_series_is_not_zero_error_or_success(self):
        result = compare_forecast(fixture(), from_origin='2023-01', to_origin='2023-01')
        h = result['levels']['category']['horizons']['1']
        self.assertEqual(h['status'], 'NO_ELIGIBLE_SERIES')
        self.assertEqual(h['series_count'], 0)
        self.assertIsNone(h['methods']['ses'])
        self.assertIsNone(h['ses_stability'])

    def test_2025_values_do_not_affect_comparison(self):
        original = compare_forecast(fixture())
        changed = compare_forecast(fixture(lambda rows: [r.__setitem__(4, '999999')
                                  for r in rows if r[1].startswith('2025')]))
        self.assertEqual(original['levels'], changed['levels'])
        self.assertEqual(original['series'], changed['series'])
        self.assertNotEqual(original['dataset_sha256'], changed['dataset_sha256'])

    def test_reserve_targets_rejected(self):
        with self.assertRaisesRegex(ValueError, 'excede'):
            compare_forecast(fixture(), to_origin='2024-07')
