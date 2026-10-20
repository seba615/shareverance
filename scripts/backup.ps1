# ==============================================================================
# Shareverance - Procedimiento de Respaldo Consistente (E7.1 / A19) [PowerShell]
#
# Genera una copia consistente de:
# 1. Base de datos PostgreSQL (pg_dump formato personalizado .dump)
# 2. Volumen persistente de comprobantes (tarball comprimido .tar.gz)
# 3. Manifiesto de integridad (manifest.json con SHA-256 y conteos de negocio)
#
# Uso:
#   .\scripts\backup.ps1 [-TargetDir <ruta>]
# ==============================================================================

[CmdletBinding()]
param (
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

Push-Location $RootDir
try {
    $dbName = "shareverance"
    $dbUser = "shareverance_dev"
    if (Test-Path .env) {
        Get-Content .env | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $k = $parts[0].Trim()
                $v = $parts[1].Trim()
                if ($k -eq "POSTGRES_DB") { $dbName = $v }
                if ($k -eq "POSTGRES_USER") { $dbUser = $v }
            }
        }
    }

    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmss")
    $isoTimestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

    if (-not $TargetDir) {
        $TargetDir = Join-Path $RootDir "backups\backup_$timestamp"
    }

    if (-not (Test-Path $TargetDir)) {
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }

    Write-Host "=== Iniciando respaldo consistente de Shareverance ($isoTimestamp) ===" -ForegroundColor Cyan
    Write-Host "Directorio de destino: $TargetDir"

    $pgContainer = (docker ps --filter "name=postgres" --filter "status=running" --format "{{.Names}}" | Select-Object -First 1)
    if (-not $pgContainer) {
        throw "No se encontró ningún contenedor de PostgreSQL en ejecución."
    }
    Write-Host "Contenedor PostgreSQL: $pgContainer"

    $receiptsVol = (docker volume ls --filter "name=receipts_data" --format "{{.Name}}" | Select-Object -First 1)
    if (-not $receiptsVol) {
        $receiptsVol = "shareverance_receipts_data"
    }
    Write-Host "Volumen de comprobantes: $receiptsVol"

    $javaContainer = (docker ps --filter "name=java-service" --filter "status=running" --format "{{.Names}}" | Select-Object -First 1)
    $wasPaused = $false
    if ($javaContainer) {
        Write-Host "Pausando $javaContainer durante la ventana de copia consistente..."
        docker pause $javaContainer | Out-Null
        $wasPaused = $true
    }

    try {
        $dumpFile = Join-Path $TargetDir "db.dump"
        Write-Host "Realizando volcado de PostgreSQL (formato custom -Fc)..."
        docker exec -i $pgContainer pg_dump -U $dbUser -d $dbName -Fc > $dumpFile

        Write-Host "Extrayendo métricas de entidades de negocio..."
        $countUsers = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM users;" 2>$null).Trim()
        $countOrgs = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM organizations;" 2>$null).Trim()
        $countMembers = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM user_organization;" 2>$null).Trim()
        $countCategories = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM categories;" 2>$null).Trim()
        $countExpenses = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM expenses;" 2>$null).Trim()
        $countSplits = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM expense_splits;" 2>$null).Trim()
        $countReceipts = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM receipts WHERE is_active = TRUE;" 2>$null).Trim()
        $countCoverage = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM analytics_month_coverage WHERE is_confirmed = TRUE;" 2>$null).Trim()

        Write-Host "Empaquetando volumen de comprobantes ($receiptsVol)..."
        $receiptsTar = Join-Path $TargetDir "receipts.tar.gz"
        docker run --rm -v "${receiptsVol}:/data:ro" -v "${TargetDir}:/backup" alpine:3.21 sh -c "tar -czf /backup/receipts.tar.gz -C /data . 2>/dev/null || tar -czf /backup/receipts.tar.gz -C /data --exclude='lost+found' ."

        $totalReceiptFiles = (docker run --rm -v "${TargetDir}:/backup:ro" alpine:3.21 sh -c "tar -tzf /backup/receipts.tar.gz 2>/dev/null | grep -v '/$' | grep -v 'lost+found' | wc -l" | Out-String).Trim()

        Write-Host "Calculando sumas de verificación SHA-256..."
        $dbHash = (Get-FileHash -Path $dumpFile -Algorithm SHA256).Hash.ToLower()
        $receiptsHash = (Get-FileHash -Path $receiptsTar -Algorithm SHA256).Hash.ToLower()

        $manifestContent = @"
{
  "version": "1.0",
  "created_at": "$isoTimestamp",
  "database": {
    "name": "$dbName",
    "dump_file": "db.dump",
    "dump_sha256": "$dbHash",
    "entities": {
      "users": $countUsers,
      "organizations": $countOrgs,
      "user_organization": $countMembers,
      "categories": $countCategories,
      "expenses": $countExpenses,
      "expense_splits": $countSplits,
      "receipts_active": $countReceipts,
      "analytics_month_coverage": $countCoverage
    }
  },
  "receipts": {
    "volume_name": "$receiptsVol",
    "archive_file": "receipts.tar.gz",
    "archive_sha256": "$receiptsHash",
    "total_files": $totalReceiptFiles
  }
}
"@
        $manifestFile = Join-Path $TargetDir "manifest.json"
        [System.IO.File]::WriteAllText($manifestFile, $manifestContent, [System.Text.Encoding]::UTF8)

        Write-Host "=== Respaldo completado exitosamente ===" -ForegroundColor Green
        Write-Host "Archivos generados en: $TargetDir"
        Write-Host "  - db.dump          (SHA256: $dbHash)"
        Write-Host "  - receipts.tar.gz  (SHA256: $receiptsHash, $totalReceiptFiles archivos)"
        Write-Host "  - manifest.json    (Auditoría y conteos de negocio)"
    }
    finally {
        if ($wasPaused -and $javaContainer) {
            Write-Host "Reanudando $javaContainer..."
            docker unpause $javaContainer | Out-Null
        }
    }
}
finally {
    Pop-Location
}
