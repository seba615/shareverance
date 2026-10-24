[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Project,
    [ValidateRange(1,2147483647)][int]$RetentionHours = 24,
    [switch]$Apply,
    [switch]$DryRun,
    [switch]$Json
)
$ErrorActionPreference = "Stop"
if ($Apply -and $DryRun) { throw 'Apply y DryRun son excluyentes.' }
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
docker build -t shareverance-backup-tools:local -f "$root/scripts/backup-tools.Dockerfile" "$root" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen; no se ejecutó limpieza.' }
$cleanupArgs = @('--project', $Project, '--retention-hours', $RetentionHours.ToString())
if ($Apply) { $cleanupArgs += '--apply' } else { $cleanupArgs += '--dry-run' }
if ($Json) { $cleanupArgs += '--json' }
docker run --rm --network none --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock `
    --entrypoint python `
    shareverance-backup-tools:local /opt/cleanup_receipts.py @cleanupArgs
if ($LASTEXITCODE -ne 0) { throw 'Limpieza fallida; revisar antes de reiniciar servicios.' }
