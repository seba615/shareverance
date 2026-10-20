[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Project, [string]$TargetDir = "", [string]$EnvFile = "")
if (-not $TargetDir) {
    $TargetDir = Join-Path (Split-Path $PSScriptRoot -Parent) ("backups/backup_" + (Get-Date).ToUniversalTime().ToString('yyyyMMdd_HHmmss'))
}
& (Join-Path $PSScriptRoot 'backup-run.ps1') -Action backup -Project $Project -BackupDir $TargetDir -EnvFile $EnvFile
