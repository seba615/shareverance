"""Política y procedimiento de limpieza de comprobantes temporales y huérfanos.

Permite auditar (dry-run) y purgar de manera segura y controlada:
1. Comprobantes no asociados a un gasto (expense_id IS NULL) que excedan la ventana de retención (ej. 24h).
2. Archivos físicos en el volumen de comprobantes sin registro en la tabla `receipts` (huérfanos) que excedan la ventana de retención.
Garantías de seguridad:
- Nunca elimina ni altera comprobantes asociados a gastos (expense_id IS NOT NULL), incluso si el gasto está anulado.
- Nunca elimina archivos ni registros recientes (dentro de la ventana de retención).
- Rechaza enlaces simbólicos y rutas no permitidas (path traversal).
- En base de datos, desactiva lógicamente el registro (is_active = FALSE) sin borrar tablas de negocio.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import time

PROJECT_LABEL = "com.docker.compose.project"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def safe_path(name):
    if not name:
        return False
    path = PurePosixPath(name)
    return (
        not path.is_absolute()
        and ".." not in path.parts
        and "\\" not in name
        and not name.startswith("/")
    )


def docker(*args, output=None, source=None):
    result = subprocess.run(
        ["docker", *args],
        stdin=source,
        stdout=output or subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(
        result.returncode == 0,
        f"Falló Docker/{args[0]} (código {result.returncode}); operación detenida.",
    )
    return result.stdout.decode().strip() if output is None else None


def inspect(kind, name):
    return json.loads(docker(kind, "inspect", name))[0]


def resolve(project):
    require(re.fullmatch(r"[a-z0-9][a-z0-9_-]*", project), "Nombre de proyecto inválido.")
    services = {}
    for service in ("postgres", "java-service", "python-service"):
        ids = docker(
            "ps",
            "-aq",
            "--filter",
            f"label={PROJECT_LABEL}={project}",
            "--filter",
            f"label=com.docker.compose.service={service}",
        ).split()
        require(len(ids) <= 1, f"Servicio ambiguo en proyecto {project}: {service}.")
        if ids:
            info = inspect("container", ids[0])
            require(info["Config"]["Labels"].get(PROJECT_LABEL) == project, "Proyecto discordante.")
            services[service] = info
    require("postgres" in services and services["postgres"]["State"]["Running"], "PostgreSQL del proyecto debe estar ejecutándose.")
    volumes = docker(
        "volume",
        "ls",
        "-q",
        "--filter",
        f"label={PROJECT_LABEL}={project}",
        "--filter",
        "label=com.docker.compose.volume=receipts_data",
    ).split()
    require(len(volumes) == 1, "Se requiere exactamente un volumen receipts_data del proyecto.")
    volume = inspect("volume", volumes[0])
    require(volume["Labels"].get(PROJECT_LABEL) == project, "Volumen ajeno al proyecto.")

    pg = services["postgres"]
    env = dict(item.split("=", 1) for item in pg["Config"]["Env"] if "=" in item)
    database = env.get("POSTGRES_DB", env.get("POSTGRES_USER", "postgres"))
    user = env.get("POSTGRES_USER", "postgres")
    for value in (database, user):
        require(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value), "Nombre SQL no admitido.")
    return pg["Id"], volumes[0], database, user


def sql(pg, database, user, statement):
    return docker("exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database, "-Atc", statement)


def scan_local_directory(root_path):
    root = Path(root_path)
    files = {}
    if not root.exists():
        return files
    for path in sorted(root.rglob("*")):
        require(not path.is_symlink(), f"Enlace simbólico no permitido en comprobantes: {path.name}")
        if path.is_file():
            rel = path.relative_to(root).as_posix()
            require(safe_path(rel), f"Ruta no permitida en volumen: {rel}")
            stat = path.stat()
            files[rel] = {"size": stat.st_size, "mtime": stat.st_mtime}
    return files


def fetch_database_state(pg, database, user):
    query = """
    SELECT json_build_object(
        'linked', COALESCE((
            SELECT json_agg(storage_path)
            FROM receipts
            WHERE expense_id IS NOT NULL
        ), '[]'::json),
        'unlinked', COALESCE((
            SELECT json_agg(json_build_object(
                'id', id,
                'path', storage_path,
                'size', file_size_bytes,
                'created_at', EXTRACT(EPOCH FROM created_at),
                'is_active', is_active
            ))
            FROM receipts
            WHERE expense_id IS NULL
        ), '[]'::json)
    );
    """
    raw = sql(pg, database, user, query)
    return json.loads(raw)


def evaluate_cleanup(files_on_disk, db_state, retention_hours, current_time=None):
    require(retention_hours >= 1, "La ventana de retención debe ser de al menos 1 hora.")
    now = current_time if current_time is not None else time.time()
    cutoff = now - (retention_hours * 3600)
    cutoff_iso = datetime.fromtimestamp(cutoff, tz=timezone.utc).isoformat()

    linked_paths = set(db_state.get("linked", []))
    for p in linked_paths:
        require(safe_path(p), f"Ruta en base de datos inválida: {p}")

    unlinked_rows = db_state.get("unlinked", [])
    all_db_paths = set(linked_paths)

    unlinked_expired = []
    protected_unlinked_recent = 0

    for row in unlinked_rows:
        path = row["path"]
        require(safe_path(path), f"Ruta en base de datos inválida: {path}")
        all_db_paths.add(path)
        created_at = row["created_at"]
        if created_at < cutoff:
            unlinked_expired.append({
                "id": row["id"],
                "path": path,
                "size": row["size"],
                "is_active": row["is_active"],
                "file_exists_on_disk": path in files_on_disk,
            })
        else:
            protected_unlinked_recent += 1

    orphan_disk_files = []
    protected_disk_recent = 0
    protected_linked_disk = 0

    for path, meta in sorted(files_on_disk.items()):
        if path in linked_paths:
            protected_linked_disk += 1
            continue
        if path in all_db_paths:
            continue
        # Archivo físico en disco que no existe en la base de datos
        if meta["mtime"] < cutoff:
            orphan_disk_files.append({
                "path": path,
                "size": meta["size"],
                "mtime": meta["mtime"],
            })
        else:
            protected_disk_recent += 1

    files_to_delete = []
    for item in unlinked_expired:
        if item["file_exists_on_disk"]:
            files_to_delete.append(item["path"])
    for item in orphan_disk_files:
        files_to_delete.append(item["path"])

    rows_to_deactivate = [item["id"] for item in unlinked_expired if item["is_active"]]

    bytes_to_free = sum(
        files_on_disk[p]["size"] for p in files_to_delete if p in files_on_disk
    )

    return {
        "retention_hours": retention_hours,
        "cutoff_timestamp": cutoff,
        "cutoff_iso": cutoff_iso,
        "protected": {
            "linked_db_receipts": len(linked_paths),
            "linked_files_on_disk": protected_linked_disk,
            "recent_unlinked_db_receipts": protected_unlinked_recent,
            "recent_untracked_disk_files": protected_disk_recent,
        },
        "candidates": {
            "expired_unlinked_receipts": unlinked_expired,
            "orphan_disk_files": orphan_disk_files,
            "files_to_delete": sorted(files_to_delete),
            "rows_to_deactivate": sorted(rows_to_deactivate),
        },
        "totals": {
            "files_to_delete_count": len(files_to_delete),
            "rows_to_deactivate_count": len(rows_to_deactivate),
            "bytes_to_free": bytes_to_free,
        },
    }


def execute_local_cleanup(root_path, evaluation, dry_run=True, db_deactivate_fn=None):
    root = Path(root_path)
    result = dict(evaluation)
    if dry_run:
        result["status"] = "SIMULATED"
        result["deleted_files"] = []
        result["deactivated_rows"] = []
        return result

    deleted = []
    for rel_path in evaluation["candidates"]["files_to_delete"]:
        target = (root / rel_path).resolve()
        require(str(target).startswith(str(root.resolve())), f"Intento de escape fuera del root: {rel_path}")
        require(not target.is_symlink(), f"Enlace simbólico detectado: {rel_path}")
        if target.is_file():
            target.unlink()
            deleted.append(rel_path)

    deactivated = []
    row_ids = evaluation["candidates"]["rows_to_deactivate"]
    if row_ids and db_deactivate_fn is not None:
        db_deactivate_fn(row_ids)
        deactivated = row_ids

    result["status"] = "APPLIED"
    result["deleted_files"] = deleted
    result["deactivated_rows"] = deactivated
    return result


def worker_scan():
    root = Path("/data")
    files = {}
    if root.exists():
        for path in sorted(root.rglob("*")):
            require(not path.is_symlink(), "Enlace simbólico no permitido.")
            if path.is_file():
                rel = path.relative_to(root).as_posix()
                require(safe_path(rel), "Ruta no permitida.")
                stat = path.stat()
                files[rel] = {"size": stat.st_size, "mtime": stat.st_mtime}
    print(json.dumps(files))


def worker_delete(file_list_json):
    root = Path("/data")
    files = json.loads(file_list_json)
    deleted = 0
    for rel in files:
        require(safe_path(rel), f"Ruta insegura: {rel}")
        target = root / rel
        require(not target.is_symlink(), f"Enlace simbólico detectado: {rel}")
        if target.is_file():
            target.unlink()
            deleted += 1
    # Limpiar directorios vacíos dentro de /data (org_X)
    for org_dir in sorted(root.iterdir()):
        if org_dir.is_dir() and not any(org_dir.iterdir()):
            org_dir.rmdir()
    print(json.dumps({"deleted_count": deleted}))


def run_cleanup_job(project, retention_hours=24, apply=False, as_json=False):
    pg_id, volume_name, database, user = resolve(project)

    # 1. Escanear archivos en el volumen mediante contenedor seguro
    image = "shareverance-backup-tools:local"
    raw_scan = docker(
        "run",
        "--rm",
        "--mount",
        f"type=volume,source={volume_name},target=/data,readonly",
        "--entrypoint",
        "python",
        image,
        "/opt/cleanup_receipts.py",
        "_worker_scan",
    )
    files_on_disk = json.loads(raw_scan)

    # 2. Consultar estado en PostgreSQL
    db_state = fetch_database_state(pg_id, database, user)

    # 3. Evaluar candidatos
    evaluation = evaluate_cleanup(files_on_disk, db_state, retention_hours)

    # 4. Aplicar o Simular
    if not apply:
        evaluation["status"] = "SIMULATED"
    else:
        files_to_delete = evaluation["candidates"]["files_to_delete"]
        if files_to_delete:
            delete_payload = json.dumps(files_to_delete)
            docker(
                "run",
                "--rm",
                "-i",
                "--mount",
                f"type=volume,source={volume_name},target=/data",
                "--entrypoint",
                "python",
                image,
                "/opt/cleanup_receipts.py",
                "_worker_delete",
                source=delete_payload.encode("utf-8"),
            )
        rows_to_deactivate = evaluation["candidates"]["rows_to_deactivate"]
        if rows_to_deactivate:
            id_literals = ", ".join(f"'{id_val}'" for id_val in rows_to_deactivate)
            update_sql = f"UPDATE receipts SET is_active = FALSE WHERE id IN ({id_literals});"
            sql(pg_id, database, user, update_sql)
        evaluation["status"] = "APPLIED"

    if as_json:
        print(json.dumps(evaluation, indent=2))
    else:
        print("=" * 60)
        print(f" SHAREVERANCE - POLÍTICA Y LIMPIEZA DE COMPROBANTES")
        print("=" * 60)
        print(f" Proyecto:           {project}")
        print(f" Estado de corrida:  {evaluation['status']}")
        print(f" Ventana retención:  {retention_hours} horas")
        print(f" Fecha de corte:     {evaluation['cutoff_iso']}")
        print("-" * 60)
        print(f" Comprobantes vinculados protegidos:       {evaluation['protected']['linked_db_receipts']}")
        print(f" Comprobantes temporales recientes:       {evaluation['protected']['recent_unlinked_db_receipts']}")
        print(f" Archivos sin registrar recientes:        {evaluation['protected']['recent_untracked_disk_files']}")
        print("-" * 60)
        print(f" Comprobantes temporales expirados:       {len(evaluation['candidates']['expired_unlinked_receipts'])}")
        print(f" Archivos huérfanos en disco a purgar:    {len(evaluation['candidates']['orphan_disk_files'])}")
        print(f" Total de archivos a eliminar:            {evaluation['totals']['files_to_delete_count']}")
        print(f" Registros SQL a desactivar:              {evaluation['totals']['rows_to_deactivate_count']}")
        print(f" Espacio en disco a liberar:              {evaluation['totals']['bytes_to_free']} bytes")
        print("=" * 60)
        if not apply:
            print(" NOTA: Modo dry-run activo. No se realizaron modificaciones.")
            print(" Para ejecutar la limpieza efectiva, use la opción --apply.")
            print("=" * 60)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "_worker_scan":
        worker_scan()
        return
    if len(sys.argv) > 1 and sys.argv[1] == "_worker_delete":
        worker_delete(sys.stdin.read())
        return

    parser = argparse.ArgumentParser(
        description="Auditoría y limpieza de comprobantes temporales y huérfanos de Shareverance."
    )
    parser.add_argument(
        "--project",
        default="shareverance",
        help="Nombre del proyecto Docker Compose (por defecto: shareverance).",
    )
    parser.add_argument(
        "--retention-hours",
        type=int,
        default=24,
        help="Horas mínimas de antigüedad para purgar archivos no vinculados (por defecto: 24, mínimo 1).",
    )
    group = parser.add_mutually_exclusive_group()
    group.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulación de auditoría sin aplicar cambios (predeterminado).",
    )
    group.add_argument(
        "--apply",
        action="store_true",
        help="Aplica la eliminación física de archivos y la desactivación en base de datos.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Imprime el resultado en formato JSON estructurado.",
    )

    args = parser.parse_args()
    apply_mode = bool(args.apply)
    run_cleanup_job(
        project=args.project,
        retention_hours=args.retention_hours,
        apply=apply_mode,
        as_json=args.json,
    )


if __name__ == "__main__":
    main()
