#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docker build -t shareverance-backup-tools:local -f "${root}/scripts/backup-tools.Dockerfile" "$root"
# No lee .env. El verificador sólo opera recursos únicos creados por su ejecución.
docker run --rm --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
    --mount "type=bind,source=${root},target=/repo,readonly" --entrypoint python \
    shareverance-backup-tools:local /repo/scripts/verify_backup_restore.py
