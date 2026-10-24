[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ScriptArgs
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
docker build -t shareverance-backup-tools:local -f "$root/scripts/backup-tools.Dockerfile" "$root" | Out-Null
docker run --rm --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock `
    --entrypoint python `
    shareverance-backup-tools:local /opt/cleanup_receipts.py @ScriptArgs
