"""Respaldo/restauración local. Docker y biblioteca estándar; sin autodetección global."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tarfile
import time
import uuid

IMAGE = "shareverance-backup-tools:local"
PROJECT_LABEL = "com.docker.compose.project"
TABLES = {
    "users": "users", "organizations": "organizations", "memberships": "user_organization",
    "categories": "categories", "expenses": "expenses", "splits": "expense_splits",
    "receipts": "receipts", "receipts_active": "receipts WHERE is_active",
    "coverage_confirmed": "analytics_month_coverage WHERE is_confirmed",
}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def docker(*args, output=None, source=None):
    # Nunca imprimir argumentos: pueden contener credenciales de aprovisionamiento.
    result = subprocess.run(["docker", *args], stdin=source, stdout=output or subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    require(result.returncode == 0, f"Falló Docker/{args[0]} (código {result.returncode}); operación detenida.")
    return result.stdout.decode().strip() if output is None else None


def inspect(kind, name):
    return json.loads(docker(kind, "inspect", name))[0]


def resolve(project):
    require(re.fullmatch(r"[a-z0-9][a-z0-9_-]*", project), "Nombre de proyecto inválido.")
    services = {}
    for service in ("postgres", "java-service", "python-service"):
        ids = docker("ps", "-aq", "--filter", f"label={PROJECT_LABEL}={project}",
                     "--filter", f"label=com.docker.compose.service={service}").split()
        require(len(ids) <= 1, f"Servicio ambiguo en proyecto {project}: {service}.")
        if ids:
            info = inspect("container", ids[0])
            require(info["Config"]["Labels"].get(PROJECT_LABEL) == project, "Proyecto discordante.")
            require(not info["State"].get("Paused"), f"Servicio previamente pausado: {service}.")
            services[service] = info
    require("postgres" in services and services["postgres"]["State"]["Running"], "PostgreSQL del proyecto debe estar ejecutándose.")
    volumes = docker("volume", "ls", "-q", "--filter", f"label={PROJECT_LABEL}={project}",
                     "--filter", "label=com.docker.compose.volume=receipts_data").split()
    require(len(volumes) == 1, "Se requiere exactamente un volumen receipts_data del proyecto.")
    volume = inspect("volume", volumes[0])
    require(volume["Labels"].get(PROJECT_LABEL) == project, "Volumen ajeno al proyecto.")
    for service in ("java-service", "python-service"):
        if service in services:
            mounts = [m for m in services[service]["Mounts"] if m["Destination"] == "/data/receipts"]
            require(len(mounts) == 1 and mounts[0].get("Name") == volumes[0], "Montaje de comprobantes discordante.")
    pg = services["postgres"]
    env = dict(item.split("=", 1) for item in pg["Config"]["Env"] if "=" in item)
    database, user = env.get("POSTGRES_DB", env.get("POSTGRES_USER", "postgres")), env.get("POSTGRES_USER", "postgres")
    for value in (database, user):
        require(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value), "Nombre SQL no admitido por este procedimiento.")
    return services, pg["Id"], volumes[0], database, user


def sql(pg, database, user, statement):
    return docker("exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database, "-Atc", statement)


def counts(pg, database, user):
    fields = ",".join(f"'{key}',(SELECT count(*) FROM {table})" for key, table in TABLES.items())
    return json.loads(sql(pg, database, user, f"SELECT json_build_object({fields})"))


def fingerprint(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_path(name):
    path = PurePosixPath(name)
    return bool(name) and not path.is_absolute() and ".." not in path.parts and "\\" not in name


def worker(mode):
    root = Path("/data")
    if mode == "_snapshot":
        files = {}
        for path in sorted(root.rglob("*")):
            require(not path.is_symlink(), "Enlace simbólico en comprobantes.")
            if path.is_file():
                files[path.relative_to(root).as_posix()] = {"size": path.stat().st_size, "sha256": fingerprint(path)}
            else:
                require(path.is_dir(), "Archivo especial no permitido.")
        print(json.dumps(files))
    elif mode == "_pack":
        with tarfile.open(fileobj=sys.stdout.buffer, mode="w|gz") as archive:
            for path in sorted(root.rglob("*")):
                require(not path.is_symlink(), "Enlace no permitido.")
                if path.is_file():
                    archive.add(path, arcname=path.relative_to(root).as_posix(), recursive=False)
    elif mode == "_unpack":
        with tarfile.open(fileobj=sys.stdin.buffer, mode="r|gz") as archive:
            for member in archive:
                require(safe_path(member.name) and member.isfile(), "Entrada tar no admitida.")
                archive.extract(member, path=root, filter="data")
    elif mode == "_replace":
        # /data es exclusivamente el volumen previamente validado del proyecto.
        for path in root.iterdir():
            if path.is_symlink() or path.is_file():
                path.unlink()
            else:
                shutil.rmtree(path)
        shutil.copytree("/stage", root, dirs_exist_ok=True)


def volume_command(volume, mode, output=None, source=None, stage=None):
    args = ["run", "--rm"]
    if source is not None:
        args.append("-i")
    args += ["--mount", f"type=volume,source={volume},target=/data" + (",readonly" if mode in ("_snapshot", "_pack") else "")]
    if stage:
        args += ["--mount", f"type=volume,source={stage},target=/stage,readonly"]
    return docker(*args, IMAGE, mode, output=output, source=source)


def audit(pg, database, user, files):
    rows = json.loads(sql(pg, database, user,
        "SELECT COALESCE(json_agg(json_build_object('path',storage_path,'size',file_size_bytes)), '[]'::json) FROM receipts"))
    referenced = set()
    for row in rows:
        name = row["path"]
        require(safe_path(name) and name not in referenced, "Referencia de comprobante inválida o duplicada.")
        require(name in files and files[name]["size"] == row["size"], "Comprobante ausente o tamaño discordante.")
        referenced.add(name)
    # Huérfanos se identifican y preservan; su limpieza es una decisión aparte.
    return len(set(files) - referenced)


def stop_apps(services, pg, database, user, stopped):
    for name in ("java-service", "python-service"):
        if name in services and services[name]["State"]["Running"]:
            stopped.append(services[name]["Id"])
            docker("stop", "--time", "30", services[name]["Id"])
    for _ in range(30):
        remaining = sql(pg, database, user,
            "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend'")
        if remaining == "0":
            return
        time.sleep(1)
    raise RuntimeError("Persisten conexiones SQL externas; cerrar clientes antes de continuar.")


def read_manifest(root):
    manifest = json.loads((root / "manifest.json").read_text())
    require(manifest.get("version") == 2, "Formato de respaldo no compatible; no se modificaron datos.")
    require(set(manifest["entities"]) == set(TABLES), "Manifiesto de entidades incompleto.")
    for name in ("db.dump", "receipts.tar.gz"):
        require(fingerprint(root / name) == manifest["sha256"][name], "SHA-256 discordante; no se modificaron datos.")
    require(all(safe_path(name) for name in manifest["files"]), "Paths inválidos en manifiesto.")
    with tarfile.open(root / "receipts.tar.gz", "r:gz") as archive:
        seen = set()
        for member in archive:
            require(safe_path(member.name) and member.isfile() and member.name not in seen, "Archivo tar inseguro o duplicado.")
            require(member.name in manifest["files"] and member.size == manifest["files"][member.name]["size"], "Inventario tar discordante.")
            seen.add(member.name)
        require(seen == set(manifest["files"]), "Inventario tar incompleto.")
    return manifest


def run(action, project, root, replace):
    services, pg, volume, database, user = resolve(project)
    print(f"Proyecto: {project}; base: {database}; volumen: {volume}", flush=True)
    manifest = None
    if action == "restore":
        require(replace, "Restaurar requiere --replace-data y proyecto explícito.")
        manifest = read_manifest(root)
        # pg_restore --list valida el formato del volcado antes de detener servicios.
        with (root / "db.dump").open("rb") as source:
            docker("exec", "-i", pg, "pg_restore", "--list", source=source)
        for key in ("JAVA_DB_PASSWORD", "PYTHON_DB_PASSWORD"):
            require(os.environ.get(key), f"Falta {key}; no se modificaron datos.")
        require(os.environ["JAVA_DB_PASSWORD"] != os.environ["PYTHON_DB_PASSWORD"], "Las credenciales SQL deben ser distintas.")
    else:
        require(not any(root.iterdir()), "El destino del respaldo debe estar vacío; no se sobrescriben copias.")
    operation = uuid.uuid4().hex
    lock = docker("create", "--name", "shareverance-backup-lock-" + project,
                  "--label", "shareverance.backup.operation=" + operation, IMAGE)
    stopped, stage, stage_db = [], None, None
    success = False
    try:
        stop_apps(services, pg, database, user, stopped)
        if action == "backup":
            os.umask(0o077)
            files = json.loads(volume_command(volume, "_snapshot"))
            orphan_count = audit(pg, database, user, files)
            with (root / "db.dump").open("xb") as output:
                docker("exec", pg, "pg_dump", "-U", user, "-d", database, "-Fc", output=output)
            with (root / "receipts.tar.gz").open("xb") as output:
                volume_command(volume, "_pack", output=output)
            require(files == json.loads(volume_command(volume, "_snapshot")), "Archivos cambiaron durante el respaldo.")
            manifest = {"version": 2, "created_at": datetime.now(timezone.utc).isoformat(),
                        "source_project": project, "database": database, "entities": counts(pg, database, user),
                        "files": files, "orphan_files": orphan_count,
                        "sha256": {name: fingerprint(root / name) for name in ("db.dump", "receipts.tar.gz")}}
            (root / "manifest.json").write_text(json.dumps(manifest, indent=2))
        else:
            stage_name = "shareverance-restore-" + operation
            docker("volume", "create", "--label", "shareverance.backup.operation=" + operation, stage_name)
            require(inspect("volume", stage_name)["Labels"].get("shareverance.backup.operation") == operation,
                    "Volumen temporal no pertenece a la operación.")
            stage = stage_name
            with (root / "receipts.tar.gz").open("rb") as source:
                volume_command(stage, "_unpack", source=source)
            require(json.loads(volume_command(stage, "_snapshot")) == manifest["files"], "SHA-256 o inventario de archivos discordante.")
            with Path("/opt/configure-access.sql").open("rb") as source:
                docker("exec", "-i", "-e", "JAVA_DB_PASSWORD=" + os.environ["JAVA_DB_PASSWORD"],
                       "-e", "PYTHON_DB_PASSWORD=" + os.environ["PYTHON_DB_PASSWORD"], pg,
                       "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database, source=source)
            stage_name = "restore_" + operation
            docker("exec", pg, "createdb", "-U", user, stage_name)
            stage_db = stage_name
            for target in (stage_db, database):
                with (root / "db.dump").open("rb") as source:
                    docker("exec", "-i", pg, "pg_restore", "-U", user, "-d", target,
                           "--clean", "--if-exists", "--no-owner", "--exit-on-error", "--single-transaction", source=source)
                # pg_dump sin --create no incluye ACL de la base; reponer la
                # política V7, también sobre la base temporal recién creada.
                sql(pg, target, user, f'REVOKE CONNECT, TEMPORARY ON DATABASE "{target}" FROM PUBLIC; '
                    f'REVOKE ALL ON DATABASE "{target}" FROM shareverance_java, shareverance_python; '
                    f'GRANT CONNECT ON DATABASE "{target}" TO shareverance_java, shareverance_python')
                require(counts(pg, target, user) == manifest["entities"], "Conteos restaurados distintos del manifiesto.")
                require(audit(pg, target, user, manifest["files"]) == manifest["orphan_files"], "Auditoría de referencias discordante.")
            volume_command(volume, "_replace", stage=stage)
            require(json.loads(volume_command(volume, "_snapshot")) == manifest["files"], "Archivos finales discordantes.")
        success = True
        print(f"{action}: verificación correcta; archivos sin referencia preservados: {manifest['orphan_files']}.")
    finally:
        # Sólo se retiran recursos propios de esta operación, nunca recursos del proyecto.
        try:
            if stage_db:
                docker("exec", pg, "dropdb", "-U", user, stage_db)
            if stage:
                docker("volume", "rm", stage)
        finally:
            try:
                if success or action == "backup":
                    for container in stopped:
                        docker("start", container)
                elif stopped:
                    print("Restauración fallida: los servicios permanecen detenidos. Revisar antes de reiniciarlos.", file=sys.stderr)
            finally:
                docker("rm", lock)


if __name__ == "__main__":
    try:
        if len(sys.argv) == 2 and sys.argv[1].startswith("_"):
            worker(sys.argv[1])
        else:
            parser = argparse.ArgumentParser(description="Respaldo local con proyecto Compose explícito.")
            parser.add_argument("action", choices=["backup", "restore"])
            parser.add_argument("--project", required=True)
            parser.add_argument("--backup-dir", required=True, type=Path)
            parser.add_argument("--replace-data", action="store_true")
            args = parser.parse_args()
            run(args.action, args.project, args.backup_dir, args.replace_data)
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
