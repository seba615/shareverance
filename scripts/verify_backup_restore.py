"""Prueba destructiva sólo en proyectos únicos creados aquí. No lee .env."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import time
import uuid

from backup_engine import IMAGE, audit, counts, docker, inspect, sql, volume_command

ROOT = Path(__file__).resolve().parent.parent


def verify(keep=False):
    operation = uuid.uuid4().hex[:12]
    projects = ["shareverance-e71-test-" + operation, "shareverance-e71-recovery-" + operation]
    owned_containers, owned_volumes = [], []
    completed = False
    artifacts = "shareverance-e71-backup-" + operation
    database, user = "shareverance_e71_test", "e71_admin"

    def create_volume(name, project=None):
        labels = ["--label", "shareverance.test.operation=" + operation]
        if project:
            labels += ["--label", "com.docker.compose.project=" + project,
                       "--label", "com.docker.compose.volume=receipts_data"]
        docker("volume", "create", *labels, name)
        owned_volumes.append(name)

    def create_container(name, *args):
        identifier = docker("run", "-d", "--name", name, "--label", "shareverance.test.operation=" + operation, *args)
        owned_containers.append(identifier)
        return identifier

    def tools(action, project, expect_success=True, **extra):
        # El socket es necesario para controlar exclusivamente los destinos identificados por proyecto.
        args = ["docker", "run", "--rm", "--mount", "type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock",
                "--mount", f"type=volume,source={artifacts},target=/backup",
                "-e", "JAVA_DB_PASSWORD=e71-java-test", "-e", "PYTHON_DB_PASSWORD=e71-python-test",
                IMAGE, action, "--project", project, "--backup-dir", "/backup"]
        if action == "restore":
            args += ["--replace-data"]
        result = subprocess.run(args, capture_output=True, text=True)
        if (result.returncode == 0) != expect_success:
            raise AssertionError(result.stdout + result.stderr)
        print(f"PASS {action}, proyecto {project}, éxito esperado={expect_success}", flush=True)
        return result

    def mutate(code):
        docker("run", "--rm", "--mount", f"type=volume,source={artifacts},target=/backup",
               "--entrypoint", "python", IMAGE, "-c", code)

    try:
        create_volume(artifacts)
        environments = []
        for project in projects:
            volume = project + "_receipts_data"
            create_volume(volume, project)
            pg = create_container(project + "-postgres", "--label", "com.docker.compose.project=" + project,
                "--label", "com.docker.compose.service=postgres", "--tmpfs", "/var/lib/postgresql/data",
                "-e", "POSTGRES_DB=" + database, "-e", "POSTGRES_USER=" + user,
                "-e", "POSTGRES_PASSWORD=e71-admin-test", "postgres:17.11-bookworm")
            for _ in range(40):
                ready = subprocess.run(["docker", "exec", pg, "pg_isready", "-U", user, "-d", database], capture_output=True)
                if ready.returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("PostgreSQL temporal no inició.")
            apps = [create_container(project + "-" + service, "--label", "com.docker.compose.project=" + project,
                      "--label", "com.docker.compose.service=" + service, "--mount", f"type=volume,source={volume},target=/data/receipts",
                      "--entrypoint", "python", IMAGE, "-c", "import signal,sys,time; signal.signal(signal.SIGTERM, lambda *_: sys.exit(0)); time.sleep(3600)")
                    for service in ("java-service", "python-service")]
            environments.append((pg, volume, apps))
        source, target = environments
        for migration in sorted((ROOT / "backend-java/src/main/resources/db/migration").glob("V*.sql")):
            with migration.open("rb") as stream:
                docker("exec", "-i", source[0], "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database, source=stream)
        seed = """INSERT INTO users(id,full_name,email,password_hash) VALUES(1,'Sintético','e71@example.invalid','no-login');
INSERT INTO organizations(id,name,type) VALUES(1,'Prueba','HOUSEHOLD');
INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(1,1,1,'ADMIN');
INSERT INTO categories(id,organization_id,name) VALUES(1,1,'Prueba');
INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description) VALUES(1,1,1,1,1,150.01,'2026-03-01','Sintético');
INSERT INTO expense_splits(organization_id,expense_id,user_organization_id,amount) VALUES(1,1,1,150.01);
INSERT INTO receipts(id,organization_id,uploaded_by_user_id,storage_path,original_filename,content_type,file_size_bytes,expense_id)
VALUES('00000000-0000-4000-8000-000000000001',1,1,'org_1/00000000-0000-4000-8000-000000000001.png','sintetico.png','image/png',3,1);
INSERT INTO analytics_month_coverage(organization_id,covered_month,declared_by_user_id) VALUES(1,'2026-03-01',1);
"""
        sql(source[0], database, user, seed)
        docker("run", "--rm", "--mount", f"type=volume,source={source[1]},target=/data", "--entrypoint", "python", IMAGE, "-c",
               "from pathlib import Path; p=Path('/data/org_1'); p.mkdir(); (p/'00000000-0000-4000-8000-000000000001.png').write_bytes(b'PNG'); Path('/data/huérfano.txt').write_bytes(b'preservar')")
        tools("backup", projects[0])
        original = counts(source[0], database, user)
        source_files = json.loads(volume_command(source[1], "_snapshot"))
        # El proyecto de recuperación comienza sin tablas ni roles técnicos.
        assert sql(target[0], database, user, "SELECT count(*) FROM pg_roles WHERE rolname='shareverance_python'") == "0"
        tools("restore", projects[1])
        assert counts(target[0], database, user) == original
        assert json.loads(volume_command(target[1], "_snapshot")) == source_files
        assert audit(target[0], database, user, source_files) == 1
        # La exclusión por proyecto también evita restauraciones simultáneas.
        lock = docker("create", "--name", "shareverance-backup-lock-" + projects[1],
                      "--label", "shareverance.test.operation=" + operation, IMAGE)
        owned_containers.append(lock)
        tools("restore", projects[1], expect_success=False)
        assert all(inspect("container", app)["State"]["Running"] for app in target[2])
        docker("rm", lock)
        owned_containers.remove(lock)
        for role, password in [("shareverance_java", "e71-java-test"), ("shareverance_python", "e71-python-test")]:
            query = "SELECT count(*) FROM expenses" if role.endswith("java") else "SELECT count(*) FROM analytics.v_monthly_expenses"
            observed = docker("exec", "-e", "PGPASSWORD=" + password, target[0], "psql", "-h", "127.0.0.1", "-X", "-v", "ON_ERROR_STOP=1", "-U", role, "-d", database, "-Atc", query)
            assert observed == ("1" if role.endswith("java") else "0")
        for query in ("SELECT * FROM expenses", "UPDATE expenses SET amount=amount WHERE false", "CREATE TEMP TABLE forbidden(id int)"):
            denied = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=e71-python-test", target[0], "psql", "-h", "127.0.0.1",
                                     "-X", "-v", "ON_ERROR_STOP=1", "-U", "shareverance_python", "-d", database, "-c", query], capture_output=True)
            assert denied.returncode != 0, f"Permiso inesperado: {query}"
        assert sql(target[0], database, user,
                   "SELECT count(*) FROM pg_roles WHERE rolname IN ('shareverance_java','shareverance_python') AND (rolsuper OR rolcreaterole OR rolbypassrls OR rolinherit)") == "0"
        # No se ignoran hashes incorrectos; el destino y sus servicios siguen intactos.
        mutate("from pathlib import Path; p=Path('/backup/db.dump'); Path('/backup/original.dump').write_bytes(p.read_bytes()); p.write_bytes(b'corrupto')")
        tools("restore", projects[1], expect_success=False)
        assert all(inspect("container", app)["State"]["Running"] for app in target[2])
        assert counts(target[0], database, user) == original
        # Hash correcto pero formato pg_dump inválido tampoco altera el destino.
        mutate("import json,hashlib; from pathlib import Path; p=Path('/backup'); m=json.loads((p/'manifest.json').read_text()); (p/'original.json').write_text(json.dumps(m)); m['sha256']['db.dump']=hashlib.sha256((p/'db.dump').read_bytes()).hexdigest(); (p/'manifest.json').write_text(json.dumps(m))")
        tools("restore", projects[1], expect_success=False)
        assert counts(target[0], database, user) == original
        mutate("import json; from pathlib import Path; p=Path('/backup'); (p/'db.dump').write_bytes((p/'original.dump').read_bytes()); m=json.loads((p/'original.json').read_text()); m['entities']['users']=999; (p/'manifest.json').write_text(json.dumps(m))")
        tools("restore", projects[1], expect_success=False)
        assert counts(target[0], database, user) == original
        assert json.loads(volume_command(target[1], "_snapshot")) == source_files
        assert all(not inspect("container", app)["State"]["Running"] for app in target[2])
        # Recursos de otro proyecto conservan datos, archivos y estado durante todos los casos.
        assert counts(source[0], database, user) == original
        assert json.loads(volume_command(source[1], "_snapshot")) == source_files
        assert all(inspect("container", app)["State"]["Running"] for app in source[2])
        tools("backup", "proyecto-inexistente-" + operation, expect_success=False)
        print("PASS ciclo real, recuperación nueva, roles, huérfanos preservados, bloqueo concurrente, hashes/formato/conteos negativos y aislamiento.", flush=True)
        completed = True
        if keep:
            print(json.dumps({"projects": projects, "containers": owned_containers, "volumes": owned_volumes, "operation": operation}))
    finally:
        if not keep or not completed:
            for container in reversed(owned_containers):
                if inspect("container", container)["Config"]["Labels"].get("shareverance.test.operation") == operation:
                    docker("rm", "-f", container)
            for volume in reversed(owned_volumes):
                if inspect("volume", volume)["Labels"].get("shareverance.test.operation") == operation:
                    docker("volume", "rm", volume)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keep", action="store_true", help="Conservar recursos propios para probar wrappers; retirarlos después por ID y etiqueta.")
    verify(parser.parse_args().keep)
