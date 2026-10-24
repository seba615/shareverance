"""Limpieza manual de comprobantes; aplicación sólo en ventana de mantenimiento."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path, PurePosixPath
import stat
import subprocess
import sys
import time
import uuid

from backup_engine import IMAGE, inspect, require, resolve


def docker(*args, source=None):
    result = subprocess.run(['docker', *args], input=source, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    require(result.returncode == 0, f'Falló Docker/{args[0]} (código {result.returncode}); operación detenida.')
    return result.stdout.decode().strip()


def safe_path(name):
    return (isinstance(name, str) and bool(name) and name != '.'
            and not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts
            and '\\' not in name and PurePosixPath(name).as_posix() == name
            and not any(ord(c) < 32 for c in name))


@contextmanager
def directory_fd(path):
    """Abrir cada componente sin seguir enlaces, incluida raíz y padres (Linux)."""
    path = Path(path).absolute()
    fd = os.open(path.anchor, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in path.parts[1:]:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        yield fd
    finally:
        os.close(fd)


def metadata(value):
    return {'size': value.st_size, 'mtime': value.st_mtime,
            'mtime_ns': value.st_mtime_ns, 'inode': value.st_ino}


def scan_local_directory(root_path):
    files = {}

    def walk(fd, prefix=''):
        for name in sorted(os.listdir(fd)):
            rel = prefix + name
            require(safe_path(rel), 'Ruta no permitida en volumen.')
            info = os.stat(name, dir_fd=fd, follow_symlinks=False)
            require(not stat.S_ISLNK(info.st_mode), 'Enlace simbólico no permitido.')
            if stat.S_ISDIR(info.st_mode):
                child = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
                try:
                    walk(child, rel + '/')
                finally:
                    os.close(child)
            else:
                require(stat.S_ISREG(info.st_mode), 'Archivo especial no permitido.')
                files[rel] = metadata(info)

    with directory_fd(root_path) as fd:
        walk(fd)
    return files


def delete_files(root_path, expected):
    """Revalidar todos los candidatos antes de borrar; no retirar directorios."""
    deleted = []
    for name in expected:
        require(safe_path(name), 'Ruta insegura en candidatos.')
    with directory_fd(root_path) as root_fd:
        def file_parent(name):
            return directory_fd(Path(root_path) / PurePosixPath(name).parent)

        for name, previous in expected.items():
            with file_parent(name) as fd:
                current = os.stat(PurePosixPath(name).name, dir_fd=fd, follow_symlinks=False)
                require(stat.S_ISREG(current.st_mode), 'Candidato dejó de ser archivo regular.')
                require(metadata(current) == previous, 'Candidato cambió desde la auditoría; no se aplica.')
        for name, previous in expected.items():
            with file_parent(name) as fd:
                leaf = PurePosixPath(name).name
                current = os.stat(leaf, dir_fd=fd, follow_symlinks=False)
                require(stat.S_ISREG(current.st_mode) and metadata(current) == previous,
                        'Candidato cambió durante la aplicación.')
                os.unlink(leaf, dir_fd=fd)
                deleted.append(name)
    return deleted


def evaluate_cleanup(files_on_disk, db_state, retention_hours, current_time=None):
    require(type(retention_hours) is int and retention_hours >= 1,
            'La ventana de retención debe ser de al menos 1 hora.')
    cutoff = (time.time() if current_time is None else current_time) - retention_hours * 3600
    linked = db_state.get('linked', [])
    rows = db_state.get('unlinked', [])
    paths = list(linked) + [row['path'] for row in rows]
    require(all(safe_path(p) for p in paths), 'Ruta en base de datos inválida.')
    require(len(paths) == len(set(paths)), 'Referencias de archivo duplicadas; revisar antes de limpiar.')
    require(all(safe_path(p) for p in files_on_disk), 'Ruta en disco inválida.')
    expired, recent = [], 0
    for row in rows:
        meta = files_on_disk.get(row['path'])
        if row['created_at'] < cutoff and (meta is None or meta['mtime'] < cutoff):
            expired.append({**row, 'file_exists_on_disk': meta is not None})
        else:
            recent += 1
    orphans = [{'path': p, **m} for p, m in sorted(files_on_disk.items())
               if p not in paths and m['mtime'] < cutoff]
    candidates = sorted([r['path'] for r in expired if r['file_exists_on_disk']]
                        + [r['path'] for r in orphans])
    deactivate = sorted(r['id'] for r in expired if r['is_active'])
    return {
        'retention_hours': retention_hours, 'cutoff_timestamp': cutoff,
        'cutoff_iso': datetime.fromtimestamp(cutoff, timezone.utc).isoformat(),
        'protected': {'linked_db_receipts': len(linked),
                      'linked_files_on_disk': sum(p in files_on_disk for p in linked),
                      'recent_unlinked_db_receipts': recent,
                      'recent_untracked_disk_files': sum(p not in paths and m['mtime'] >= cutoff
                                                        for p, m in files_on_disk.items())},
        'candidates': {'expired_unlinked_receipts': expired, 'orphan_disk_files': orphans,
                       'files_to_delete': candidates, 'rows_to_deactivate': deactivate},
        'totals': {'files_to_delete_count': len(candidates),
                   'rows_to_deactivate_count': len(deactivate),
                   'bytes_to_free': sum(files_on_disk[p]['size'] for p in candidates)},
    }


STATE_SQL = """SELECT json_build_object(
 'linked', COALESCE((SELECT json_agg(storage_path) FROM receipts WHERE expense_id IS NOT NULL), '[]'::json),
 'unlinked', COALESCE((SELECT json_agg(json_build_object('id',id,'path',storage_path,
 'size',file_size_bytes,'created_at',EXTRACT(EPOCH FROM created_at),'is_active',is_active))
 FROM receipts WHERE expense_id IS NULL), '[]'::json));"""


class Transaction:
    """Una sesión psql mantiene el bloqueo SQL hasta después del borrado físico."""
    def __init__(self, pg, database, user):
        self.process = subprocess.Popen(['docker', 'exec', '-i', pg, 'psql', '-X', '-qAt',
                                         '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, text=True)

    def query(self, statement):
        marker = 'cleanup_' + uuid.uuid4().hex
        self.process.stdin.write(statement + '\n\\echo ' + marker + '\n')
        self.process.stdin.flush()
        lines = []
        while True:
            line = self.process.stdout.readline()
            require(line != '', 'Falló sesión SQL; revisar operación antes de reiniciar servicios.')
            if line.strip() == marker:
                return '\n'.join(lines)
            lines.append(line.rstrip())

    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.close()
                self.process.wait(timeout=10)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait()
        self.process.stdout.close()


def assert_offline(services, volume):
    require(all(not info['State']['Running'] for service, info in services.items()
                if service != 'postgres'), 'Detener Java y Python antes de --apply; PostgreSQL debe seguir activo.')
    # Cualquier otro contenedor activo con el volumen puede escribir o usar comprobantes.
    require(not docker('ps', '-q', '--filter', f'volume={volume}'),
            'Hay contenedores activos usando comprobantes; cerrar escritores/lectores antes de --apply.')


def volume_worker(volume, mode, payload=None):
    args = ['run', '--rm', '--network', 'none']
    if payload is not None:
        args.append('-i')
    args += ['--mount', f'type=volume,source={volume},target=/data' + (',readonly' if mode == '_worker_scan' else ''),
             '--entrypoint', 'python', IMAGE, '/opt/cleanup_receipts.py', mode]
    return json.loads(docker(*args, source=json.dumps(payload).encode() if payload is not None else None))


def run_cleanup_job(project, retention_hours=24, apply=False, as_json=False):
    require(type(retention_hours) is int and retention_hours >= 1, 'Retención mínima: 1 hora.')
    services, pg, volume, database, user = resolve(project)
    # Mismo nombre que backup/restauración: exclusión entre todas las herramientas.
    operation = uuid.uuid4().hex
    lock = docker('create', '--name', 'shareverance-backup-lock-' + project,
                  '--label', 'shareverance.cleanup.operation=' + operation, IMAGE)
    tx = None
    try:
        if apply:
            assert_offline(services, volume)
            tx = Transaction(pg, database, user)
            tx.query("BEGIN; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s'; "
                     "SET LOCAL idle_in_transaction_session_timeout='120s'; "
                     'LOCK TABLE receipts IN ACCESS EXCLUSIVE MODE;')
            state = json.loads(tx.query(STATE_SQL))
        else:
            state = json.loads(docker('exec', pg, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                                      '-U', user, '-d', database, '-Atc', STATE_SQL))
        files = volume_worker(volume, '_worker_scan')
        evaluation = evaluate_cleanup(files, state, retention_hours)
        evaluation['project'] = project
        evaluation['deleted_files'] = []
        evaluation['deactivated_rows'] = []
        if apply:
            # Revalidar que ningún servicio fue arrancado mientras se tomó el inventario.
            current = resolve(project)
            require((pg, volume, database, user) == current[1:], 'Proyecto cambió durante mantenimiento.')
            assert_offline(current[0], volume)
            ids = evaluation['candidates']['rows_to_deactivate']
            if ids:
                literals = ','.join("'" + str(uuid.UUID(value)) + "'" for value in ids)
                count = tx.query('WITH changed AS (UPDATE receipts SET is_active=FALSE '
                                 f'WHERE id IN ({literals}) AND expense_id IS NULL AND is_active '
                                 f"AND created_at < to_timestamp({evaluation['cutoff_timestamp']}) "
                                 'RETURNING id) SELECT count(*) FROM changed;')
                require(int(count) == len(ids), 'Candidatos SQL cambiaron; operación detenida.')
            targets = {p: files[p] for p in evaluation['candidates']['files_to_delete']}
            deleted = volume_worker(volume, '_worker_delete', targets) if targets else []
            require(deleted == list(targets), 'Borrado incompleto; no se informa éxito.')
            tx.query('COMMIT;')
            evaluation['deleted_files'] = deleted
            evaluation['deactivated_rows'] = ids
        evaluation['status'] = 'APPLIED' if apply else 'SIMULATED'
        print(json.dumps(evaluation, indent=2) if as_json else
              f"{evaluation['status']}: proyecto {project}; archivos candidatos "
              f"{evaluation['totals']['files_to_delete_count']}; filas {evaluation['totals']['rows_to_deactivate_count']}. "
              'Auditoría no constituye autorización de una ejecución posterior; se recalculan candidatos.')
        return evaluation
    finally:
        try:
            if tx:
                tx.close()  # EOF revierte cualquier transacción sin COMMIT.
        finally:
            require(inspect('container', lock)['Config']['Labels'].get('shareverance.cleanup.operation') == operation,
                    'Bloqueo no pertenece a la operación.')
            docker('rm', lock)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '_worker_scan':
        print(json.dumps(scan_local_directory('/data')))
        return
    if len(sys.argv) == 2 and sys.argv[1] == '_worker_delete':
        print(json.dumps(delete_files('/data', json.load(sys.stdin))))
        return
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    parser.add_argument('--retention-hours', type=int, default=24)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--dry-run', action='store_true')
    modes.add_argument('--apply', action='store_true')
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    run_cleanup_job(args.project, args.retention_hours, args.apply, args.json)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'ERROR: {error} No reiniciar servicios hasta revisar cualquier aplicación parcial.', file=sys.stderr)
        sys.exit(1)
