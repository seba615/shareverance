param(
    [ValidateSet('start', 'load', 'check', 'status', 'stop', 'test')]
    [string]$Action = 'status'
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
        'status' { & docker @composeArgs ps }
        'stop' { & docker @composeArgs stop }
        'test' {
            & docker @composeArgs build python-service
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen.' }
            & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_load_predictor_demo.py -v
        }
        default {
            $argsRun = @('run', '--rm', '--name', "${project}-loader", '--network', "${project}_default", '--mount', "type=bind,source=$PSScriptRoot/load_predictor_demo.py,target=/tools/load_predictor_demo.py,readonly", '--mount', "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos_prueba.csv,readonly", '--entrypoint', '/app/.venv/bin/python', "$project-python-service", '/tools/load_predictor_demo.py', '--csv', '/data/gastos_prueba.csv')
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
