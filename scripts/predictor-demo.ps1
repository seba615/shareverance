param(
    [ValidateSet('start', 'load', 'check', 'check-forecast', 'status', 'stop', 'test', 'audit', 'evaluate', 'forecast', 'evaluate-trend', 'compare', 'load-summary')]
    [string]$Action = 'status',
    [string]$FromMonth = '2024-01',
    [string]$ToMonth = '2024-12',
    [switch]$AllowReserve,
    [switch]$SweepAlpha,
    [double]$Alpha = 0.3,
    [string]$FromOrigin = '2023-12',
    [string]$ToOrigin = '2024-06',
    [int]$MaxHorizon = 6
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$project = 'shareverance-predictor-demo'
$composeArgs = @('compose', '--project-name', $project, '--env-file', (Join-Path $repo 'demo/predictor.env.example'), '-f', (Join-Path $repo 'compose.yaml'))
# Aislar configuración de variables del entorno habitual y restaurarlas al salir.
$names = @('POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD','JAVA_DB_PASSWORD','PYTHON_DB_PASSWORD','PYTHON_CLIENT_SECRET','PYTHON_CLIENT_SECRET_PREVIOUS','PREDICTOR_MODE','WEB_HOST_PORT','RECEIPTS_STORAGE_PATH','COMPOSE_FILE','COMPOSE_PROJECT_NAME')
$saved = @{}
try {
    foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name); Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    switch ($Action) {
        'start' { & docker @composeArgs up --build -d --wait --wait-timeout 300 }
        'load-summary' {
            & docker run --rm --network "${project}_default" --env-file (Join-Path $repo 'demo/predictor.env.example') --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" /tools/load_individual_summary_demo.py
        }
        'status' { & docker @composeArgs ps }
        'stop' { & docker @composeArgs stop }
        'test' {
            & docker @composeArgs build python-service 1> $null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_load_predictor_demo.py -v
            if ($LASTEXITCODE -ne 0) { throw 'Fallaron pruebas del cargador.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_audit_predictor_dataset.py -v
            if ($LASTEXITCODE -ne 0) { throw 'Fallaron pruebas de auditoría.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_evaluate_predictor_csv.py -v
            if ($LASTEXITCODE -ne 0) { throw 'Fallaron pruebas del evaluador h=1.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_evaluate_forecast_csv.py -v
            if ($LASTEXITCODE -ne 0) { throw 'Fallaron pruebas del evaluador multihorizonte.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_compare_forecast_csv.py -v
            if ($LASTEXITCODE -ne 0) { throw 'Fallaron pruebas de comparación.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_check_predictor_demo.py -v
        }
        'evaluate' {
            & docker @composeArgs build python-service 1> $null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen.' }
            $commit = & git -C $repo rev-parse HEAD
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo identificar el commit.' }
            $evalArgs = @('/tools/evaluate_predictor_csv.py', '--csv', '/data/gastos.csv', '--from-month', $FromMonth, '--to-month', $ToMonth, '--commit', $commit)
            if ($AllowReserve) { $evalArgs += '--allow-reserve' }
            if ($SweepAlpha) { $evalArgs += '--sweep-alpha' }
            $evalArgs += @('--alpha', $Alpha.ToString([Globalization.CultureInfo]::InvariantCulture))
            & docker run --rm --network none --env PYTHONPATH=/app --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" @evalArgs
        }
        'audit' {
            & docker @composeArgs build python-service 1> $null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot/audit_predictor_dataset.py,target=/tools/audit.py,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" /tools/audit.py --csv /data/gastos.csv
        }
        { $_ -in 'forecast', 'evaluate-trend', 'compare' } {
            & docker @composeArgs build python-service 1> $null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen.' }
            $commit = & git -C $repo rev-parse HEAD
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo identificar el commit.' }
            if ($Action -eq 'compare') {
                if ($MaxHorizon -ne 6) { throw 'compare exige MaxHorizon=6 para comparar ventanas 4/5/6 con una cohorte común.' }
                $fArgs = @('/tools/compare_forecast_csv.py', '--csv', '/data/gastos.csv', '--from-origin', $FromOrigin, '--to-origin', $ToOrigin, '--commit', $commit)
            } else {
                $fArgs = @('/tools/evaluate_forecast_csv.py', '--csv', '/data/gastos.csv', '--from-origin', $FromOrigin, '--to-origin', $ToOrigin, '--max-horizon', $MaxHorizon.ToString(), '--commit', $commit)
                if ($Action -eq 'evaluate-trend') { $fArgs += '--include-trend' }
            }
            & docker run --rm --network none --env PYTHONPATH=/app --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" @fArgs
        }
        'check-forecast' {
            & docker run --rm --env-file (Join-Path $repo 'demo/predictor.env.example') --network "${project}_default" --env PYTHONPATH=/app --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" /tools/check_predictor_demo.py --csv /data/gastos.csv
        }
        default {
            $argsRun = @('run', '--rm', '--env-file', (Join-Path $repo 'demo/predictor.env.example'), '--env', 'PYTHONPATH=/app', '--name', "${project}-loader", '--network', "${project}_default", '--mount', "type=bind,source=$PSScriptRoot/load_predictor_demo.py,target=/tools/load_predictor_demo.py,readonly", '--mount', "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos_prueba.csv,readonly", '--entrypoint', '/app/.venv/bin/python', "$project-python-service", '/tools/load_predictor_demo.py', '--csv', '/data/gastos_prueba.csv')
            if ($Action -eq 'check') { $argsRun += '--check-only' }
            & docker @argsRun
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "Falló predictor-demo $Action (código $LASTEXITCODE)." }
} finally {
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$name" $saved[$name] }
    }
}
