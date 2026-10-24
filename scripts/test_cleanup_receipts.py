"""Pruebas unitarias para la política y procedimiento de limpieza de comprobantes."""

import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch, MagicMock

import cleanup_receipts as cleanup


class CleanupRulesTest(unittest.TestCase):

    def test_safe_path_validations(self):
        # Casos inválidos
        for invalid in ["", "/absoluto", "../fuera", "org/../../archivo.png", "org\\archivo.jpg", "/"]:
            self.assertFalse(cleanup.safe_path(invalid), f"Debería rechazar: {invalid}")

        # Casos válidos
        for valid in ["org_1/comprobante.jpg", "org_99/uuid-1234.png", "sub/dir/archivo.jpeg"]:
            self.assertTrue(cleanup.safe_path(valid), f"Debería aceptar: {valid}")

    def test_retention_hours_validation(self):
        with self.assertRaisesRegex(RuntimeError, "al menos 1 hora"):
            cleanup.evaluate_cleanup({}, {}, retention_hours=0)
        with self.assertRaisesRegex(RuntimeError, "al menos 1 hora"):
            cleanup.evaluate_cleanup({}, {}, retention_hours=-5)

    def test_linked_receipts_are_strictly_protected_even_if_old(self):
        now = 1700000000.0
        old_time = now - (48 * 3600)  # Hace 48 horas

        files_on_disk = {
            "org_1/gasto_activo.jpg": {"size": 2048, "mtime": old_time},
            "org_1/gasto_anulado.jpg": {"size": 4096, "mtime": old_time},
        }

        # Ambos tienen expense_id != null (uno activo y uno anulado)
        db_state = {
            "linked": ["org_1/gasto_activo.jpg", "org_1/gasto_anulado.jpg"],
            "unlinked": [],
        }

        res = cleanup.evaluate_cleanup(files_on_disk, db_state, retention_hours=24, current_time=now)

        self.assertEqual(res["protected"]["linked_db_receipts"], 2)
        self.assertEqual(res["protected"]["linked_files_on_disk"], 2)
        self.assertEqual(res["totals"]["files_to_delete_count"], 0)
        self.assertEqual(res["totals"]["rows_to_deactivate_count"], 0)
        self.assertEqual(res["totals"]["bytes_to_free"], 0)
        self.assertEqual(len(res["candidates"]["files_to_delete"]), 0)

    def test_recent_unlinked_receipts_are_protected(self):
        now = 1700000000.0
        recent_time = now - (2 * 3600)  # Hace 2 horas (menor a 24h)

        files_on_disk = {
            "org_1/reciente.jpg": {"size": 1024, "mtime": recent_time},
        }
        db_state = {
            "linked": [],
            "unlinked": [
                {
                    "id": "rec-1",
                    "path": "org_1/reciente.jpg",
                    "size": 1024,
                    "created_at": recent_time,
                    "is_active": True,
                }
            ],
        }

        res = cleanup.evaluate_cleanup(files_on_disk, db_state, retention_hours=24, current_time=now)

        self.assertEqual(res["protected"]["recent_unlinked_db_receipts"], 1)
        self.assertEqual(res["totals"]["files_to_delete_count"], 0)
        self.assertEqual(res["totals"]["rows_to_deactivate_count"], 0)
        self.assertEqual(res["totals"]["bytes_to_free"], 0)

    def test_expired_unlinked_receipts_are_flagged_for_cleanup(self):
        now = 1700000000.0
        old_time = now - (30 * 3600)  # Hace 30 horas (> 24h)

        files_on_disk = {
            "org_1/expirado.jpg": {"size": 5000, "mtime": old_time},
        }
        db_state = {
            "linked": [],
            "unlinked": [
                {
                    "id": "rec-exp-1",
                    "path": "org_1/expirado.jpg",
                    "size": 5000,
                    "created_at": old_time,
                    "is_active": True,
                }
            ],
        }

        res = cleanup.evaluate_cleanup(files_on_disk, db_state, retention_hours=24, current_time=now)

        self.assertEqual(len(res["candidates"]["expired_unlinked_receipts"]), 1)
        self.assertEqual(res["totals"]["files_to_delete_count"], 1)
        self.assertEqual(res["totals"]["rows_to_deactivate_count"], 1)
        self.assertEqual(res["totals"]["bytes_to_free"], 5000)
        self.assertIn("org_1/expirado.jpg", res["candidates"]["files_to_delete"])
        self.assertIn("rec-exp-1", res["candidates"]["rows_to_deactivate"])

    def test_orphan_disk_files_evaluated_by_mtime(self):
        now = 1700000000.0
        old_time = now - (48 * 3600)
        recent_time = now - (1 * 3600)

        files_on_disk = {
            "org_1/huerfano_antiguo.png": {"size": 3000, "mtime": old_time},
            "org_1/huerfano_reciente.png": {"size": 2000, "mtime": recent_time},
        }
        # Sin registros en base de datos
        db_state = {"linked": [], "unlinked": []}

        res = cleanup.evaluate_cleanup(files_on_disk, db_state, retention_hours=24, current_time=now)

        # El reciente queda protegido
        self.assertEqual(res["protected"]["recent_untracked_disk_files"], 1)
        # El antiguo queda como candidato de borrado
        self.assertEqual(len(res["candidates"]["orphan_disk_files"]), 1)
        self.assertIn("org_1/huerfano_antiguo.png", res["candidates"]["files_to_delete"])
        self.assertNotIn("org_1/huerfano_reciente.png", res["candidates"]["files_to_delete"])
        self.assertEqual(res["totals"]["files_to_delete_count"], 1)
        self.assertEqual(res["totals"]["bytes_to_free"], 3000)

    def test_delete_revalidates_metadata_and_parent_symlinks(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / 'org_1').mkdir()
            f = root / 'org_1' / 'expirado.jpg'
            f.write_bytes(b'old')
            manifest = cleanup.scan_local_directory(root)
            f.write_bytes(b'changed')
            with self.assertRaisesRegex(RuntimeError, 'cambió'):
                cleanup.delete_files(root, manifest)
            self.assertTrue(f.exists())
            manifest = cleanup.scan_local_directory(root)
            self.assertEqual(cleanup.delete_files(root, manifest), ['org_1/expirado.jpg'])
            self.assertTrue((root / 'org_1').is_dir())

    def test_recent_mtime_protects_old_database_row(self):
        now = 1700000000.0
        old = now - 48 * 3600
        files = {'org_1/recent.jpg': {'size': 10, 'mtime': now}}
        state = {'linked': [], 'unlinked': [{'id': 'old', 'path': 'org_1/recent.jpg',
                 'size': 10, 'created_at': old, 'is_active': True}]}
        result = cleanup.evaluate_cleanup(files, state, 24, now)
        self.assertEqual(result['totals']['files_to_delete_count'], 0)
        self.assertEqual(result['totals']['rows_to_deactivate_count'], 0)

    def test_duplicate_path_cannot_override_linked_protection(self):
        state = {'linked': ['org_1/same.jpg'], 'unlinked': [{'id': 'old',
                 'path': 'org_1/same.jpg', 'created_at': 0, 'size': 1, 'is_active': True}]}
        with self.assertRaisesRegex(RuntimeError, 'duplicadas'):
            cleanup.evaluate_cleanup({}, state, 24)

    def test_parent_link_rejected_at_delete_after_scan(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / 'org_1').mkdir()
            (root / 'org_1' / 'file.jpg').write_bytes(b'keep')
            manifest = cleanup.scan_local_directory(root)
            (root / 'org_1').rename(root / 'saved')
            (root / 'org_1').symlink_to(root / 'saved', target_is_directory=True)
            with self.assertRaises(OSError):
                cleanup.delete_files(root, manifest)
            self.assertEqual((root / 'saved' / 'file.jpg').read_bytes(), b'keep')

    def test_docker_bytes_use_input_not_stdin(self):
        fake = MagicMock(returncode=0, stdout=b'[]')
        with patch('cleanup_receipts.subprocess.run', return_value=fake) as run:
            self.assertEqual(cleanup.docker('run', source=b'{}'), '[]')
        self.assertEqual(run.call_args.kwargs['input'], b'{}')
        self.assertNotIn('stdin', run.call_args.kwargs)

    def test_scan_local_directory_rejects_symlinks(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            org_dir = root / "org_1"
            org_dir.mkdir(parents=True)

            real_file = org_dir / "real.jpg"
            real_file.write_bytes(b"REAL")

            link_file = org_dir / "enlace.jpg"
            try:
                link_file.symlink_to(real_file)
            except OSError:
                self.skipTest("Symlinks no soportados en este entorno")

            with self.assertRaisesRegex(RuntimeError, "Enlace simbólico no permitido"):
                cleanup.scan_local_directory(root)


if __name__ == "__main__":
    unittest.main()
