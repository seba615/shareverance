import csv
import io
import unittest
from load_predictor_demo import dataset, MONTHS, verify_expense


def fixture(change=None):
    rows = [['1', month, str(cat), f'categoría {cat}', '10001', 'ARS'] for month in MONTHS for cat in range(1, 9)]
    if change:
        change(rows)
    output = io.StringIO()
    writer = csv.writer(output, delimiter=';')
    writer.writerow(['organization_id', 'month', 'category_id', 'category', 'amount_minor', 'currency'])
    writer.writerows(rows)
    return output.getvalue().encode()


class DatasetTests(unittest.TestCase):
    def test_complete_and_exact(self):
        rows, names = dataset(fixture())
        self.assertEqual(len(rows), 192)
        self.assertEqual(len(names), 8)
        self.assertEqual(sum(row['amount'] for row in rows), 1920192)

    def test_future_and_other_org_not_used(self):
        rows, _ = dataset(fixture(lambda r: r.extend([
            ['1', '2025-01', '1', 'futuro', '999999', 'ARS'],
            ['2', '2023-01', '1', 'ajeno', '999999', 'ARS'],
        ])))
        self.assertEqual(len(rows), 192)
        self.assertEqual(max(r['month'] for r in rows), '2024-12')

    def test_duplicate(self):
        with self.assertRaisesRegex(ValueError, 'duplicada'):
            dataset(fixture(lambda r: r.append(r[0])))

    def test_missing(self):
        with self.assertRaises(ValueError):
            dataset(fixture(lambda r: r.pop()))

    def test_currency_amount_limits(self):
        for column, value in [(5, 'USD'), (4, '-1'), (4, '0'), (4, '1.5'), (4, '1000000000000')]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                dataset(fixture(lambda r: r[0].__setitem__(column, value)))

    def test_inconsistent_name(self):
        with self.assertRaises(ValueError):
            dataset(fixture(lambda r: r[0].__setitem__(3, 'otro nombre')))

    def test_business_amount_and_parts(self):
        row = {'month': '2024-02', 'amount': 10001}
        detail = {'expense': {'organizationId': 2, 'categoryId': 7, 'paidByMembershipId': 3,
                             'createdByUserId': 4, 'amount': '100.01', 'expenseDate': '2024-02-29',
                             'isActive': True, 'splitType': 'INDIVIDUAL'},
                  'splits': [{'userOrganizationId': 3, 'amount': '100.01'}]}
        verify_expense(detail, row, 7, 3, 2, 4)
        detail['splits'][0]['amount'] = '100.00'
        with self.assertRaises(ValueError):
            verify_expense(detail, row, 7, 3, 2, 4)

    def test_altered_expense_not_overwritten(self):
        detail = {'expense': {'organizationId': 99}}
        with self.assertRaises(ValueError):
            verify_expense(detail, {'month': '2024-02', 'amount': 10001}, 7, 3, 2, 4)


if __name__ == '__main__':
    unittest.main()
