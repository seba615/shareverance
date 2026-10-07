$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $port = "5173"
    if (Test-Path .env) {
        $line = Get-Content .env | Where-Object { $_ -match '^WEB_HOST_PORT=' } | Select-Object -Last 1
        if ($line) { $port = ($line -split '=', 2)[1].Trim() }
    }
    $r = Invoke-RestMethod -Uri "http://localhost:$port/api/dev/status" -TimeoutSec 15
    $r | Format-List
    if ($r.java -ne 'UP' -or $r.database -ne 'UP' -or $r.python -ne 'UP' -or $r.demoRows -ne 2) {
        throw "La comprobación no pasó. Revisar logs y estado de los servicios."
    }
    Write-Host "Entorno verificado: Java, PostgreSQL, Python y 2 registros de demostración." -ForegroundColor Green
} finally { Pop-Location }
