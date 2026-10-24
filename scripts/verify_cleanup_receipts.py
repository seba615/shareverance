"""Verificación destructiva sólo en PostgreSQL tmpfs y volumen propios, sin puertos."""
import json
from pathlib import Path
import subprocess
import time
import uuid

import cleanup_receipts as cleanup
from backup_engine import IMAGE, docker, inspect, sql

ROOT = Path(__file__).resolve().parent.parent
PG_IMAGE = 'postgres:17.11-bookworm@sha256:3645570cccdfa447589da9f57dd740faa29b30938e861289a5574b6ca6b03826'


def verify(keep=False):
    operation = uuid.uuid4().hex
    project = 'cleanup-test-' + operation[:12]
    volume, pg, app = project + '-receipts', project + '-pg', project + '-java'
    owner_label = 'shareverance.cleanup.test=' + operation
    containers, volumes = [], []
    completed = False
    database, user = 'cleanup_test', 'cleanup_owner'

    def tools(*args, success=True):
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--mount',
                                 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
                                 '--entrypoint', 'python', IMAGE, '/opt/cleanup_receipts.py',
                                 '--project', project, '--json', *args], capture_output=True)
        assert (result.returncode == 0) == success, result.stderr.decode()
        return json.loads(result.stdout) if success else result

    def seed_files(extra=False):
        code = ("from pathlib import Path; import os,time; p=Path('/data/org_1'); p.mkdir(exist_ok=True); "
                "names=['failure.jpg']" if extra else
                "from pathlib import Path; import os,time; p=Path('/data/org_1'); p.mkdir(exist_ok=True); "
                "names=['active.jpg','annulled.jpg','expired.jpg','recent.jpg','modified.jpg',"
                "'race.jpg','orphan.jpg','orphan-recent.jpg']")
        code += ";\nfor n in names:\n f=p/n; f.write_bytes(b'synthetic'); t=time.time() if n in ['recent.jpg','modified.jpg','orphan-recent.jpg'] else time.time()-172800; os.utime(f,(t,t))"
        docker('run', '--rm', '--network', 'none', '--mount', f'type=volume,source={volume},target=/data',
               '--entrypoint', 'python', IMAGE, '-c', code)

    try:
        docker('volume', 'create', '--label', owner_label, '--label', 'com.docker.compose.project=' + project,
               '--label', 'com.docker.compose.volume=receipts_data', volume)
        volumes.append(volume)
        docker('run', '-d', '--name', pg, '--network', 'none', '--label', owner_label,
               '--label', 'com.docker.compose.project=' + project,
               '--label', 'com.docker.compose.service=postgres', '--tmpfs', '/var/lib/postgresql/data',
               '-e', 'POSTGRES_USER=' + user, '-e', 'POSTGRES_DB=' + database,
               '-e', 'POSTGRES_PASSWORD=cleanup-test-local-only', PG_IMAGE)
        containers.append(pg)
        for attempt in range(50):
            ready = subprocess.run(['docker', 'exec', pg, 'pg_isready', '-U', user, '-d', database],
                                   capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise RuntimeError('PostgreSQL temporal no inició.')
        for migration in sorted((ROOT / 'backend-java/src/main/resources/db/migration').glob('V*.sql')):
            with migration.open('rb') as stream:
                docker('exec', '-i', pg, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                       '-U', user, '-d', database, source=stream)
        sql(pg, database, user, """
INSERT INTO users(id,full_name,email,password_hash) VALUES(1,'Prueba','cleanup@example.invalid','no-login');
INSERT INTO organizations(id,name,type) VALUES(1,'Prueba limpieza','HOUSEHOLD');
INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(1,1,1,'ADMIN');
INSERT INTO categories(id,organization_id,name) VALUES(1,1,'Prueba');
INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description,is_active)
VALUES(1,1,1,1,1,1,'2026-01-01','Prueba',TRUE),(2,1,1,1,1,1,'2026-01-01','Prueba anulada',FALSE);
""")
        for index, name in enumerate(['active', 'annulled', 'expired', 'recent', 'modified', 'missing', 'race'], 1):
            linked = str(index) if index <= 2 else 'NULL'
            created = 'CURRENT_TIMESTAMP' if name == 'recent' else "CURRENT_TIMESTAMP - INTERVAL '48 hours'"
            sql(pg, database, user, f"""INSERT INTO receipts(id,organization_id,uploaded_by_user_id,
 storage_path,original_filename,content_type,file_size_bytes,expense_id,created_at)
 VALUES('00000000-0000-4000-8000-{index:012}',1,1,'org_1/{name}.jpg','prueba.jpg','image/jpeg',9,{linked},{created});""")
        seed_files()
        docker('run', '-d', '--name', app, '--network', 'none', '--label', owner_label,
               '--label', 'com.docker.compose.project=' + project,
               '--label', 'com.docker.compose.service=java-service', '--mount',
               f'type=volume,source={volume},target=/data/receipts', '--entrypoint', 'python',
               IMAGE, '-c', 'import time; time.sleep(3600)')
        containers.append(app)
        before = cleanup.volume_worker(volume, '_worker_scan')
        audit = tools()
        assert audit['status'] == 'SIMULATED' and len(audit['candidates']['files_to_delete']) == 3
        tools('--apply', success=False)
        assert cleanup.volume_worker(volume, '_worker_scan') == before
        # Revisión obsoleta: una asociación posterior a dry-run debe quedar protegida.
        sql(pg, database, user, "UPDATE receipts SET expense_id=1 WHERE storage_path='org_1/race.jpg';")
        docker('stop', app)
        tx = cleanup.Transaction(pg, database, user)
        try:
            tx.query("BEGIN; LOCK TABLE receipts IN ACCESS EXCLUSIVE MODE;")
            blocked = subprocess.run(['docker', 'exec', pg, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                                      '-U', user, '-d', database, '-c',
                                      "SET lock_timeout='150ms'; UPDATE receipts SET expense_id=1 WHERE storage_path='org_1/expired.jpg';"],
                                     capture_output=True)
            assert blocked.returncode != 0
            tx.query('ROLLBACK;')
        finally:
            tx.close()
        # Bloqueo compartido con backup/restauración: jamás eliminar el ajeno.
        lock = docker('create', '--name', 'shareverance-backup-lock-' + project, '--label', owner_label, IMAGE)
        containers.append(lock)
        tools('--apply', success=False)
        assert inspect('container', lock)['Id'] == lock
        docker('rm', lock)
        containers.remove(lock)
        applied = tools('--apply')
        assert applied['status'] == 'APPLIED'
        assert applied['deleted_files'] == ['org_1/expired.jpg', 'org_1/orphan.jpg']
        assert len(applied['deactivated_rows']) == 2  # expired y missing
        after = cleanup.volume_worker(volume, '_worker_scan')
        assert all(name in after for name in ['org_1/active.jpg', 'org_1/annulled.jpg',
                                              'org_1/recent.jpg', 'org_1/modified.jpg', 'org_1/race.jpg'])
        assert sql(pg, database, user, 'SELECT count(*) FROM receipts WHERE expense_id IS NOT NULL AND NOT is_active;') == '0'
        assert sql(pg, database, user, 'SELECT count(*) FROM expenses;') == '2'
        assert tools('--apply')['deleted_files'] == []
        # Un fallo SQL debe propagarse antes del borrado y revertir la transacción.
        seed_files(extra=True)
        sql(pg, database, user, """INSERT INTO receipts(id,organization_id,uploaded_by_user_id,storage_path,
original_filename,content_type,file_size_bytes,created_at) VALUES('00000000-0000-4000-8000-000000000008',
1,1,'org_1/failure.jpg','prueba.jpg','image/jpeg',9,CURRENT_TIMESTAMP-INTERVAL '48 hours');
CREATE FUNCTION cleanup_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'prueba'; END; $$;
CREATE TRIGGER cleanup_fail BEFORE UPDATE ON receipts FOR EACH ROW EXECUTE FUNCTION cleanup_fail();""")
        tools('--apply', success=False)
        assert 'org_1/failure.jpg' in cleanup.volume_worker(volume, '_worker_scan')
        assert sql(pg, database, user, "SELECT is_active FROM receipts WHERE storage_path='org_1/failure.jpg';") == 't'
        sql(pg, database, user, 'DROP TRIGGER cleanup_fail ON receipts; DROP FUNCTION cleanup_fail();')
        tools('--apply')
        # Fallo al confirmar después de borrar: no hay rollback de filesystem ni falso APPLIED.
        seed_files(extra=True)
        sql(pg, database, user, """UPDATE receipts SET is_active=TRUE WHERE storage_path='org_1/failure.jpg';
CREATE FUNCTION cleanup_commit_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'prueba commit'; END; $$;
CREATE CONSTRAINT TRIGGER cleanup_commit_fail AFTER UPDATE ON receipts DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION cleanup_commit_fail();""")
        tools('--apply', success=False)
        assert 'org_1/failure.jpg' not in cleanup.volume_worker(volume, '_worker_scan')
        assert sql(pg, database, user, "SELECT is_active FROM receipts WHERE storage_path='org_1/failure.jpg';") == 't'
        sql(pg, database, user, 'DROP TRIGGER cleanup_commit_fail ON receipts; DROP FUNCTION cleanup_commit_fail();')
        seed_files(extra=True)  # Recuperación sintética explícita sólo en estos recursos aislados.
        tools('--apply')
        print('Verificado: PostgreSQL real, auditoría, aplicación, preservación, retención, '
              'asociación posterior a auditoría, bloqueo SQL concurrente, exclusión de mantenimiento, '
              'fallo SQL sin borrado, fallo COMMIT parcial sin falso éxito y repetición sin cambios.')
        completed = True
        if keep:
            print(json.dumps({'project': project, 'operation': operation, 'containers': containers, 'volumes': volumes}))
    finally:
        if not keep or not completed:
            for container in reversed(containers):
                assert inspect('container', container)['Config']['Labels'].get('shareverance.cleanup.test') == operation
                docker('rm', '-f', container)
            for target in volumes:
                assert inspect('volume', target)['Labels'].get('shareverance.cleanup.test') == operation
                docker('volume', 'rm', target)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--keep', action='store_true')
    verify(parser.parse_args().keep)
