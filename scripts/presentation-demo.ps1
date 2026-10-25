param(
    [ValidateSet('prepare','start','check','status','stop','test')]
    [string]$Action = 'status'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$project = 'shareverance-predictor-demo'
if ($Action -in @('start','status','stop')) {
    & (Join-Path $PSScriptRoot 'predictor-demo.ps1') $Action
    return
}
if ($Action -eq 'prepare') {
    & (Join-Path $PSScriptRoot 'predictor-demo.ps1') start
}
if ($Action -eq 'test') {
    & docker run --rm --network none --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" -m unittest discover -s /tools -p test_presentation_demo.py -v
} else {
    $credentialArgs = @()
    $localCredentials = Join-Path $repo 'demo/presentacion/.env.local'
    if ($env:PRESENTATION_PASSWORD) { $credentialArgs = @('--env','PRESENTATION_PASSWORD') }
    elseif (Test-Path -LiteralPath $localCredentials) { $credentialArgs = @('--env-file',$localCredentials) }
    else { throw 'Configurar PRESENTATION_PASSWORD en el entorno o en demo/presentacion/.env.local (ignorado por Git).' }
    $loaderArgs = @('/tools/load_presentation_demo.py','--csv','/data/gastos.csv','--assets','/assets')
    if ($Action -eq 'check') { $loaderArgs += '--check-only' }
    & docker run --rm @credentialArgs --network "${project}_default" --env-file (Join-Path $repo 'demo/predictor.env.example') --env PYTHONPATH=/app --mount "type=bind,source=$PSScriptRoot,target=/tools,readonly" --mount "type=bind,source=$repo/demo/gastos_prueba.csv,target=/data/gastos.csv,readonly" --mount "type=bind,source=$repo/demo/presentacion,target=/assets,readonly" --entrypoint /app/.venv/bin/python "$project-python-service" @loaderArgs
}
if ($LASTEXITCODE -ne 0) { throw "Falló la demo de presentación ($Action)." }
