[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Project,
      [Parameter(Mandatory=$true)][string]$BackupDir, [string]$EnvFile = "", [switch]$ReplaceData)
& (Join-Path $PSScriptRoot 'backup-run.ps1') -Action restore -Project $Project -BackupDir $BackupDir -EnvFile $EnvFile -ReplaceData:$ReplaceData
