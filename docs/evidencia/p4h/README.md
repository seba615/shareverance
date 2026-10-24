# P4-H: comparación descriptiva por horizonte y ventana

Relacionado con [#79](https://github.com/seba615/shareverance/issues/79). Base ejecutada: `fe25a9a` con cambios locales P4-H, identificados por los hashes del comparador, evaluador y modelos en [resumen.json](resumen.json). El commit base no se presenta como una configuración congelada previamente.

## Diseño y límites

CSV V1 sintético, siete orígenes comunes de diciembre 2023 a junio 2024, objetivos exclusivamente de 2024. Se reutiliza P3-H sin cambiar los modelos ni sus parámetros: SES 0.3, último mes y media histórica. El auditor lee estructura del CSV completo; ningún error ni objetivo de 2025 interviene en esta comparación.

La cohorte exige que cada serie tenga todos los orígenes y seis horizontes válidos. Se utiliza la misma cohorte para cada método, h=1…6 y ventanas H=4/5/6. Una serie incompleta se excluye de todas las ventanas, incluso si sólo falta un objetivo de h=6; no se imputa cero. El informe completo identifica cada punto excluido. Esto es deliberadamente más estricto que los resúmenes por horizonte de P3-H y permite comparar muestras comunes. Un nivel sin series elegibles informa NO_ELIGIBLE_SERIES y métricas null, no cero.

Por serie, MAE usa puntos con peso igual por origen y horizonte. Por nivel se promedian los MAE con igual peso por serie; WAPE agrupa errores absolutos sobre importes observados, con null si la base total es cero. Las fracciones exactas en centavos determinan relaciones y deterioro antes del redondeo a ARS. Para SES se compara contra el menor MAE entre ambas referencias **de esa serie y ese horizonte/ventana**. Empates no cuentan como deterioro. Se informa delta absoluto y relativo por serie; con referencia cero el relativo es null y el deterioro positivo se señala explícitamente.

Los puntos de ventanas solapadas reutilizan objetivos; sus conteos no son muestras independientes. No se construyen intervalos, porcentajes de confianza ni una regla de aceptación. El informe mantiene accepted:null, thresholds:null, model_promoted:false y final_evaluation_accredited:false. 2025 ya fue consultado en P4: una evaluación final necesita otro conjunto/protocolo acordado y registro previo. No se congela retroactivamente una reserva ni se promueve último mes como método operativo.

## Resultado ejecutado

50 organizaciones y 400 categorías elegibles, cero exclusiones. Detalle h=1…6 y estabilidad en resumen.json; informe completo local con 450 series en backend-java/target/p4h/informe.json (no versionado). El resumen incluye hash canónico del informe completo JSON (UTF-8, claves ordenadas, sin espacios) para comprobar identidad semántica.

| Nivel | H | MAE último mes ARS | MAE media ARS | MAE SES ARS | WAPE último mes / SES | Puntos | Series SES peores / elegibles |
| --- | --- | --- | --- | --- | --- | --- | --- |
| organization | 4 | 206233.13 | 634158.24 | 353544.31 | 10.77 % / 18.46 % | 1400 | 50/50 |
| organization | 5 | 247684.81 | 677294.55 | 396680.62 | 12.65 % / 20.25 % | 1750 | 50/50 |
| organization | 6 | 291995.43 | 722728.24 | 442114.32 | 14.57 % / 22.06 % | 2100 | 50/50 |
| category | 4 | 33524.44 | 79365.71 | 45795.21 | 14.00 % / 19.13 % | 11200 | 351/400 |
| category | 5 | 37620.62 | 84740.48 | 50936.37 | 15.37 % / 20.81 % | 14000 | 357/400 |
| category | 6 | 42335.40 | 90407.94 | 56417.72 | 16.90 % / 22.52 % | 16800 | 363/400 |

Último mes tiene menor MAE agregado que los otros métodos en ambas escalas para H=4/5/6 y h=1…6. Esto describe estos datos y parámetros; no afirma superioridad en cada serie, no acepta precisión real ni fija parámetros óptimos. P5 conserva mock predeterminado y SES experimental.

## Reproducir sin iniciar servicios

Desde la raíz, PowerShell y Docker Desktop Linux activo. La construcción puede necesitar Internet; los contenedores de evaluación se ejecutan sin red, con código/CSV de sólo lectura y sin base de datos. No requiere start/load.

```powershell
New-Item -ItemType Directory -Force backend-java/target/p4h | Out-Null
.\scripts\predictor-demo.ps1 test
.\scripts\predictor-demo.ps1 compare > backend-java/target/p4h/informe.json
if ($LASTEXITCODE -ne 0) { throw "Falló la comparación" }
```

compare fija seis horizontes y usa FromOrigin/ToOrigin (por defecto 2023-12/2024-06). MaxHorizon distinto de 6 se rechaza; el evaluador P3-H forecast conserva su selección 4/5/6. No usar este comando para abrir 2025: se rechazan orígenes cuyos seis objetivos exceden desarrollo.

Alternativa con un entorno Python que ya tenga las dependencias fijadas de service-python instaladas (el Python global sin ellas no basta):

```powershell
$previousPythonPath = $env:PYTHONPATH
Push-Location service-python
try {
    $env:PYTHONPATH=".;../scripts"
    uv run python -m unittest discover -s ../scripts -p test_compare_forecast_csv.py -v
    $commit = git rev-parse HEAD
    uv run python ../scripts/compare_forecast_csv.py --csv ../demo/gastos_prueba.csv --commit $commit
} finally {
    Pop-Location
    $env:PYTHONPATH = $previousPythonPath
}
```

## Entorno de aplicación vigente

La UI sigue siendo mensual; compare es offline. Para probar mock con usuario y organización CSV, seguir [P1-D](../../python_predictor.md#entorno-demo-predictor):

```powershell
.\scripts\predictor-demo.ps1 start
.\scripts\predictor-demo.ps1 load
.\scripts\predictor-demo.ps1 check
# Abrir http://localhost:5176 y elegir la organización CSV.
.\scripts\predictor-demo.ps1 stop
```

La detención conserva datos y volúmenes. Esta entrega no inició entornos integrados ni modificó bases.

## Comprobaciones de esta entrega

Wrapper test ejecutado en Docker: 43 pruebas aprobadas (8 cargador, 8 auditor, 13 evaluador mensual, 6 multihorizonte y 8 comparación). Cubren referencia por serie, fracciones antes del redondeo, ceros, ponderación, cohorte común, exclusión transversal, falta de series, invariancia ante cambios en 2025 y rechazo de objetivos de reserva. compare ejecutado mediante el wrapper; JSON parseado y resumen generado.

El intento inicial con Python global falló por falta de pydantic; se usó la imagen Docker con el lock existente, sin incorporar dependencias. La alternativa uv se documenta pero no se ejecutó en Windows. No se repitieron suites Java/frontend ni integración PostgreSQL, porque no cambiaron componentes operativos. Aceptación, integración y cierre manuales.
