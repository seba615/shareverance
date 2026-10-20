[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('backup','restore')][string]$Action,
      [Parameter(Mandatory=$true)][string]$Project,
      [Parameter(Mandatory=$true)][string]$BackupDir,
      [string]$EnvFile = "", [switch]$ReplaceData)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($Action -eq 'restore' -and -not $ReplaceData) { throw 'Restaurar requiere -ReplaceData y proyecto explícito.' }
if ($Action -eq 'backup') { New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null }
$absolute = (Resolve-Path -LiteralPath $BackupDir).Path
if (-not $EnvFile) { $EnvFile = Join-Path $root '.env' }
& docker build -t shareverance-backup-tools:local -f (Join-Path $PSScriptRoot 'backup-tools.Dockerfile') $root
if ($LASTEXITCODE -ne 0) { throw 'Falló la construcción de herramientas de respaldo.' }
$arguments = @('run','--rm','--mount','type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
               '--mount',"type=bind,source=$absolute,target=/backup")
if (Test-Path -LiteralPath $EnvFile) {
    $arguments += @('--env-file',(Resolve-Path -LiteralPath $EnvFile).Path)
} else {
    $arguments += @('-e','JAVA_DB_PASSWORD','-e','PYTHON_DB_PASSWORD')
}
$arguments += @('shareverance-backup-tools:local',$Action,'--project',$Project,'--backup-dir','/backup')
if ($ReplaceData) { $arguments += '--replace-data' }
& docker @arguments
if ($LASTEXITCODE -ne 0) { throw 'Falló el procedimiento; revisar el error anterior. No se informó éxito.' }
