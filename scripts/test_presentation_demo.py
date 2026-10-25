from pathlib import Path
import unittest
from unittest.mock import Mock, patch
import urllib.error
from decimal import Decimal
from load_presentation_demo import history_plan, shift_month, parts, PresentationClient

class PresentationPlanTest(unittest.TestCase):
    def test_shift_preserves_order_and_crosses_years(self):
        self.assertEqual('2024-10',shift_month('2023-01',21))
        self.assertEqual('2026-09',shift_month('2024-12',21))
        self.assertEqual('2027-01',shift_month('2026-12',1))
    def test_remainder_is_deterministic_and_exact(self):
        self.assertEqual({4:Decimal('5.01'),5:Decimal('5.00')},parts(1001,[5,4]))
        self.assertEqual(Decimal('10.01'),sum(parts(1001,[4,5]).values()))
        with self.assertRaises(ValueError): parts(1001,[4,4])
        with self.assertRaises(ValueError): parts(1,[4,5])
    def test_csv_dates_shift_but_amounts_and_universe_are_preserved(self):
        path=Path('/data/gastos.csv')
        if not path.exists(): path=Path(__file__).resolve().parents[1]/'demo/gastos_prueba.csv'
        plan,names,delta=history_plan(path.read_bytes(),'2026-09')
        self.assertEqual(192,len(plan));self.assertEqual(8,len(names));self.assertEqual(21,delta)
        self.assertEqual(3121121292,sum(p['cents'] for p in plan))
        self.assertEqual(24,len({p['date'][:7] for p in plan}))
        self.assertEqual({'equal','admin','user'},{p['kind'] for p in plan})
        self.assertEqual('2024-10-31',min(p['date'] for p in plan))
        self.assertEqual('2026-09-30',max(p['date'] for p in plan))

    def test_existing_admin_login_failure_never_registers_or_resets(self):
        client=object.__new__(PresentationClient)
        client.call=Mock(side_effect=urllib.error.HTTPError('local',401,'Denied',{},None))
        with patch.dict('os.environ',{'PRESENTATION_PASSWORD':'test-only-value'}):
            with self.assertRaises(urllib.error.HTTPError):
                client.account_named('admin@example.test','Admin',False,allow_create=False)
        self.assertEqual(1,client.call.call_count)
        self.assertEqual('/auth/login',client.call.call_args.args[1])
    def test_password_is_required_before_transmitting_credentials(self):
        client=object.__new__(PresentationClient)
        client.call=Mock()
        with patch.dict('os.environ',{'PRESENTATION_PASSWORD':''}):
            with self.assertRaises(ValueError):
                client.account_named('user@example.test','Usuario',False)
        client.call.assert_not_called()

if __name__=='__main__': unittest.main()
