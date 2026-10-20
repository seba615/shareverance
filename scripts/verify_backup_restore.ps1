[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& docker build -t shareverance-backup-tools:local -f (Join-Path $PSScriptRoot 'backup-tools.Dockerfile') $root
if ($LASTEXITCODE -ne 0) { throw 'Falló la construcción del verificador.' }
& docker run --rm --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' `
    --mount "type=bind,source=$root,target=/repo,readonly" --entrypoint python `
    shareverance-backup-tools:local /repo/scripts/verify_backup_restore.py
if ($LASTEXITCODE -ne 0) { throw 'Falló la verificación aislada.' }
