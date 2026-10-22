import csv
import io
import unittest
from audit_predictor_dataset import audit, COLUMNS, EXPECTED


def fixture(change=None):
    rows = [[str(org), month, str(cat), f'categoría {cat}', str((index + 1) * org * cat), 'ARS']
            for org in (1, 2) for index, month in enumerate(EXPECTED) for cat in range(1, 9)]
    if change:
        change(rows)
    out = io.StringIO()
    writer = csv.writer(out, delimiter=';')
    writer.writerow(COLUMNS)
    writer.writerows(rows)
    return out.getvalue().encode()


class AuditTests(unittest.TestCase):
    def test_structure_is_not_coverage_or_acceptance(self):
        report = audit(fixture())
        self.assertTrue(report['structurally_complete'])
        self.assertIsNone(report['final_evaluation_eligible'])
        self.assertIsNone(report['category_series'][0]['coverage_confirmed'])
        self.assertFalse(report['forecast_metrics_computed'])

    def test_exact_scaling_identified_not_independence(self):
        relationships = audit(fixture())['development_relationships']
        self.assertEqual(relationships['exact_proportional_organization_totals'], [[1, 2]])
        self.assertFalse(relationships['independence_demonstrated'])

    def test_reserve_values_do_not_change_development_relationships(self):
        changed = fixture(lambda rows: [row.__setitem__(4, '999') for row in rows if row[1].startswith('2025')])
        self.assertEqual(audit(fixture())['development_relationships'], audit(changed)['development_relationships'])

    def test_gap_is_reported_not_imputed(self):
        report = audit(fixture(lambda rows: rows.pop(0)))
        self.assertFalse(report['structurally_complete'])
        self.assertEqual(report['category_series'][0]['missing_months'], ['2023-01'])

    def test_zero_is_observed_not_coverage(self):
        report = audit(fixture(lambda rows: rows[0].__setitem__(4, '0')))
        self.assertEqual(report['zero_rows'], 1)
        self.assertIsNone(report['category_series'][0]['coverage_confirmed'])

    def test_whole_category_absent_is_not_complete(self):
        report = audit(fixture(lambda rows: rows.__setitem__(slice(None), [r for r in rows if r[0] != '1' or r[2] != '8'])))
        self.assertFalse(report['structurally_complete'])
        self.assertEqual(report['organization_series'][0]['category_count'], 7)

    def test_duplicate_and_invalid_fields_rejected(self):
        with self.assertRaises(ValueError):
            audit(fixture(lambda rows: rows.append(rows[0])))
        for index, value in [(0, '-1'), (1, '2023-13'), (4, '1.5'), (4, '-1'), (5, 'USD')]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                audit(fixture(lambda rows: rows[0].__setitem__(index, value)))

    def test_name_change_and_extra_column_rejected(self):
        with self.assertRaises(ValueError):
            audit(fixture(lambda rows: rows[8].__setitem__(3, 'otra')))
        with self.assertRaises(ValueError):
            audit(fixture(lambda rows: rows[0].append('extra')))
