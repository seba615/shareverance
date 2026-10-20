#!/usr/bin/env bash
set -euo pipefail
action="$1"; shift
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project=""; directory=""; env_file="${root}/.env"; replace=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --project) project="$2"; shift 2 ;;
        --directory) directory="$2"; shift 2 ;;
        --env-file) env_file="$2"; shift 2 ;;
        --replace-data) replace=(--replace-data); shift ;;
        *) echo 'Uso: --project PROYECTO --directory DIRECTORIO [--env-file ARCHIVO] [--replace-data para restaurar]' >&2; exit 1 ;;
    esac
done
[ -n "$project" ] || { echo 'Proyecto Compose explícito obligatorio.' >&2; exit 1; }
if [ -z "$directory" ] && [ "$action" = backup ]; then
    directory="${root}/backups/backup_$(date -u +%Y%m%d_%H%M%S)"
fi
[ -n "$directory" ] || { echo 'Directorio obligatorio para restaurar.' >&2; exit 1; }
if [ "$action" = backup ]; then mkdir -p "$directory"; fi
directory="$(cd "$directory" && pwd)"
docker build -t shareverance-backup-tools:local -f "${root}/scripts/backup-tools.Dockerfile" "$root"
environment=()
if [ -f "$env_file" ]; then
    environment=(--env-file "$env_file")
else
    environment=(-e JAVA_DB_PASSWORD -e PYTHON_DB_PASSWORD)
fi
# Docker Desktop Linux también expone este socket dentro de sus contenedores.
docker run --rm --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
    --mount "type=bind,source=${directory},target=/backup" "${environment[@]}" \
    shareverance-backup-tools:local "$action" --project "$project" --backup-dir /backup "${replace[@]}"
