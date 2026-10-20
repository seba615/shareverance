# ==============================================================================
# Shareverance - Procedimiento de Restauración y Verificación Cruzada (E7.1 / A19) [PowerShell]
#
# Restaura:
# 1. Base de datos PostgreSQL a partir de db.dump
# 2. Configuración de credenciales de roles técnicos (configure-access.sql)
# 3. Volumen persistente de comprobantes desde receipts.tar.gz
# 4. Verificación exhaustiva de integridad referencial cruzada (DB <-> Filesystem)
#
# Uso:
#   .\scripts\restore.ps1 -BackupDir <ruta> [-SkipPause]
# ==============================================================================

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$BackupDir,
    [switch]$SkipPause
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

Push-Location $RootDir
try {
    if (-not (Test-Path $BackupDir)) {
        throw "El directorio de respaldo no existe: $BackupDir"
    }

    $manifestFile = Join-Path $BackupDir "manifest.json"
    $dumpFile = Join-Path $BackupDir "db.dump"
    $receiptsTar = Join-Path $BackupDir "receipts.tar.gz"

    foreach ($f in @($manifestFile, $dumpFile, $receiptsTar)) {
        if (-not (Test-Path $f)) {
            throw "Archivo obligatorio no encontrado en el respaldo: $f"
        }
    }

    Write-Host "=== Iniciando restauración de Shareverance desde: $BackupDir ===" -ForegroundColor Cyan
    Write-Host "Verificando integridad criptográfica de los artefactos de respaldo..."

    $manifest = Get-Content $manifestFile -Raw | ConvertFrom-Json
    $expectedDbHash = $manifest.database.dump_sha256.ToLower()
    $expectedReceiptsHash = $manifest.receipts.archive_sha256.ToLower()

    $actualDbHash = (Get-FileHash -Path $dumpFile -Algorithm SHA256).Hash.ToLower()
    $actualReceiptsHash = (Get-FileHash -Path $receiptsTar -Algorithm SHA256).Hash.ToLower()

    if ($expectedDbHash -ne $actualDbHash) {
        throw "Discrepancia SHA-256 en db.dump! Esperado: $expectedDbHash, Actual: $actualDbHash"
    }
    if ($expectedReceiptsHash -ne $actualReceiptsHash) {
        throw "Discrepancia SHA-256 en receipts.tar.gz! Esperado: $expectedReceiptsHash, Actual: $actualReceiptsHash"
    }
    Write-Host "Integridad de archivos confirmada (SHA-256 coincidente)." -ForegroundColor Green

    $dbName = "shareverance"
    $dbUser = "shareverance_dev"
    $javaDbPass = "shareverance_java_local_only"
    $pythonDbPass = "shareverance_python_sql_local_only"

    if (Test-Path .env) {
        Get-Content .env | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $k = $parts[0].Trim()
                $v = $parts[1].Trim()
                if ($k -eq "POSTGRES_DB") { $dbName = $v }
                if ($k -eq "POSTGRES_USER") { $dbUser = $v }
                if ($k -eq "JAVA_DB_PASSWORD") { $javaDbPass = $v }
                if ($k -eq "PYTHON_DB_PASSWORD") { $pythonDbPass = $v }
            }
        }
    }

    $pgContainer = (docker ps --filter "name=postgres" --filter "status=running" --format "{{.Names}}" | Select-Object -First 1)
    if (-not $pgContainer) {
        throw "No se encontró ningún contenedor de PostgreSQL en ejecución."
    }
    Write-Host "Contenedor PostgreSQL de destino: $pgContainer"

    $receiptsVol = (docker volume ls --filter "name=receipts_data" --format "{{.Name}}" | Select-Object -First 1)
    if (-not $receiptsVol) {
        $receiptsVol = "shareverance_receipts_data"
    }
    Write-Host "Volumen de comprobantes de destino: $receiptsVol"

    $pausedContainers = @()
    if (-not $SkipPause) {
        foreach ($svc in @("java-service", "python-service")) {
            $c = (docker ps --filter "name=$svc" --filter "status=running" --format "{{.Names}}" | Select-Object -First 1)
            if ($c) {
                Write-Host "Pausando $c..."
                docker pause $c | Out-Null
                $pausedContainers += $c
            }
        }
    }

    try {
        Write-Host "Restaurando base de datos PostgreSQL desde db.dump..."
        docker exec -i $pgContainer pg_restore -U $dbUser -d $dbName --clean --if-exists --no-owner < $dumpFile

        $configureSql = Join-Path $RootDir "database\configure-access.sql"
        if (Test-Path $configureSql) {
            Write-Host "Re-configurando roles y permisos de acceso (configure-access.sql)..."
            $sqlContent = Get-Content $configureSql -Raw
            $sqlContent | docker exec -i -e JAVA_DB_PASSWORD=$javaDbPass -e PYTHON_DB_PASSWORD=$pythonDbPass $pgContainer psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName
        }

        Write-Host "Restaurando archivos en volumen de comprobantes ($receiptsVol)..."
        docker volume create $receiptsVol | Out-Null
        docker run --rm -v "${receiptsVol}:/data" -v "${BackupDir}:/backup:ro" alpine:3.21 sh -c "rm -rf /data/* /data/..?* /data/.[!.]* 2>/dev/null || true; tar -xzf /backup/receipts.tar.gz -C /data"

        Write-Host ""
        Write-Host "=== Ejecutando Verificación de Integridad Cruzada ===" -ForegroundColor Cyan

        $restUsers = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM users;" 2>$null).Trim()
        $restOrgs = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM organizations;" 2>$null).Trim()
        $restMembers = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM user_organization;" 2>$null).Trim()
        $restCategories = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM categories;" 2>$null).Trim()
        $restExpenses = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM expenses;" 2>$null).Trim()
        $restSplits = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM expense_splits;" 2>$null).Trim()
        $restReceipts = (docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -c "SELECT COUNT(*) FROM receipts WHERE is_active = TRUE;" 2>$null).Trim()

        Write-Host "Conteos en base restaurada:"
        Write-Host "  - Usuarios:            $restUsers"
        Write-Host "  - Organizaciones:      $restOrgs"
        Write-Host "  - Membresías:          $restMembers"
        Write-Host "  - Categorías:          $restCategories"
        Write-Host "  - Gastos:              $restExpenses"
        Write-Host "  - Partes de gastos:    $restSplits"
        Write-Host "  - Comprobantes activos:$restReceipts"

        Write-Host "Verificando correspondencia de cada comprobante activo en el sistema de archivos..."
        $receiptRows = docker exec -i $pgContainer psql -U $dbUser -d $dbName -t -A -F'|' -c "SELECT id, organization_id, storage_path, file_size_bytes FROM receipts WHERE is_active = TRUE;" 2>$null

        $integrityErrors = 0
        $checkedReceipts = 0

        if ($receiptRows) {
            foreach ($row in ($receiptRows -split "`n")) {
                $line = $row.Trim()
                if (-not $line) { continue }
                $cols = $line.Split("|")
                $rId = $cols[0]
                $rOrg = $cols[1]
                $rPath = $cols[2]
                $rSize = $cols[3]

                $checkedReceipts++
                $fileCheck = (docker run --rm -v "${receiptsVol}:/data:ro" alpine:3.21 sh -c "if [ -f '/data/$rPath' ]; then wc -c < '/data/$rPath'; else echo 'MISSING'; fi" | Out-String).Trim()

                if ($fileCheck -eq "MISSING") {
                    Write-Host "ERROR DE INTEGRIDAD: Comprobante ID $rId (org $rOrg) no existe en /data/$rPath" -ForegroundColor Red
                    $integrityErrors++
                }
                elseif ($fileCheck -ne $rSize) {
                    Write-Host "ERROR DE INTEGRIDAD: Comprobante ID $rId tiene tamaño discordante (BD: $rSize bytes, Disco: $fileCheck bytes)" -ForegroundColor Red
                    $integrityErrors++
                }
            }
        }

        Write-Host "Comprobantes activos auditados: $checkedReceipts (errores: $integrityErrors)"
        if ($integrityErrors -gt 0) {
            throw "Falló la verificación de integridad cruzada post-restauración."
        }

        Write-Host "=== Restauración y verificación completadas exitosamente ===" -ForegroundColor Green
    }
    finally {
        foreach ($c in $pausedContainers) {
            Write-Host "Reanudando $c..."
            docker unpause $c | Out-Null
        }
    }
}
finally {
    Pop-Location
}
