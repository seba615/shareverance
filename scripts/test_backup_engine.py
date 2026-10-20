import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import backup_engine as engine


class BackupRulesTest(unittest.TestCase):
    def test_docker_error_never_becomes_success_or_exposes_arguments(self):
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 42, b"", b"secret")):
            with self.assertRaisesRegex(RuntimeError, "código 42") as failure:
                engine.docker("exec", "secret", "pg_restore")
            self.assertNotIn("secret", str(failure.exception))

    def test_invalid_project_is_rejected_before_docker(self):
        with patch.object(engine, "docker") as docker:
            with self.assertRaises(RuntimeError):
                engine.resolve("../otro")
            docker.assert_not_called()

    def test_ambiguous_project_is_rejected_without_selecting_first(self):
        with patch.object(engine, "docker", return_value="first\nsecond"):
            with self.assertRaisesRegex(RuntimeError, "ambiguo"):
                engine.resolve("prueba")

    def test_paths_reject_traversal_absolute_and_windows_paths(self):
        for path in ("../otro", "/absoluto", "org/../../otro", "org\\archivo", ""):
            self.assertFalse(engine.safe_path(path))
        self.assertTrue(engine.safe_path("org_1/archivo.png"))

    def test_audit_checks_all_receipts_including_historical_and_reports_orphans(self):
        with patch.object(engine, "sql", return_value=json.dumps([{"path": "org/archivo", "size": 3}])):
            self.assertEqual(engine.audit("pg", "db", "user", {"org/archivo": {"size": 3}, "huérfano": {"size": 5}}), 1)
            with self.assertRaises(RuntimeError):
                engine.audit("pg", "db", "user", {"org/archivo": {"size": 2}})
            with self.assertRaises(RuntimeError):
                engine.audit("pg", "db", "user", {})

    def test_duplicate_receipt_reference_is_error(self):
        with patch.object(engine, "sql", return_value=json.dumps([{"path": "a", "size": 1}] * 2)):
            with self.assertRaises(RuntimeError):
                engine.audit("pg", "db", "user", {"a": {"size": 1}})

    def test_snapshot_sql_failure_is_not_an_empty_database(self):
        with patch.object(engine, "docker", side_effect=RuntimeError("SQL falló")):
            with self.assertRaisesRegex(RuntimeError, "SQL falló"):
                engine.counts("pg", "db", "user")

    def manifest(self, root, path="org/archivo"):
        (root / "db.dump").write_bytes(b"PGDMP")
        with tarfile.open(root / "receipts.tar.gz", "w:gz") as archive:
            member = tarfile.TarInfo(path)
            member.size = 3
            archive.addfile(member, io.BytesIO(b"PNG"))
        body = {"version": 2, "entities": dict.fromkeys(engine.TABLES, 0),
                "sha256": {file: engine.fingerprint(root / file) for file in ("db.dump", "receipts.tar.gz")},
                "files": {path: {"size": 3, "sha256": "test"}}}
        (root / "manifest.json").write_text(json.dumps(body))
        return body

    def test_valid_inventory_and_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.manifest(root)
            self.assertEqual(engine.read_manifest(root)["version"], 2)

    def test_changed_dump_fails_before_any_docker_call(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(engine, "docker") as docker:
            root = Path(directory)
            self.manifest(root)
            (root / "db.dump").write_bytes(b"corrupto")
            with self.assertRaisesRegex(RuntimeError, "SHA-256"):
                engine.read_manifest(root)
            docker.assert_not_called()

    def test_unsafe_archive_even_with_matching_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.manifest(root, "../fuera")
            with self.assertRaises(RuntimeError):
                engine.read_manifest(root)

    def test_old_manifest_is_rejected_without_modifying_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "manifest.json").write_text('{"version":"1.0"}')
            with self.assertRaisesRegex(RuntimeError, "no compatible"):
                engine.read_manifest(root)
            self.assertEqual((root / "manifest.json").read_text(), '{"version":"1.0"}')


if __name__ == "__main__":
    unittest.main()
