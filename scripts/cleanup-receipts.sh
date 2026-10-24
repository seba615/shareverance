#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docker build -t shareverance-backup-tools:local -f "${root}/scripts/backup-tools.Dockerfile" "$root" >/dev/null
exec docker run --rm --network none --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
    --entrypoint python \
    shareverance-backup-tools:local /opt/cleanup_receipts.py "$@"
