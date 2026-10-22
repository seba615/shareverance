# Python: analítica y predicción

Fecha de revisión: 21/10/2026. Base revisada: `9ea8ce4` (main, incluye PR #60). La evidencia histórica conserva sus fechas y alcances.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado

FastAPI en `service-python/app/`. Existen esquemas estrictos, validación de historial, endpoints predictivos (`POST /internal/v2/predictions/monthly` y `POST /api/v1/predictions/monthly`) y predictor simulado. E5.1 implementa `POST /internal/v2/analytics/monthly` con lectura de vistas, comparación y evolución por cobertura explícita. Su verificación con PostgreSQL real se registra en el [roadmap](road_map.md). E5.2 integra su consulta pública Java y el panel React sin modificar este contrato interno; el predictor real evaluado continúa pendiente de E6.1.

E3.1 incorporó en PostgreSQL las vistas y el rol `shareverance_python` ([postgre.md](postgre.md)). E3.2 implementa en Python el repositorio de vistas (`ViewsRepository` con `psycopg[binary]`), conecta a PostgreSQL como `shareverance_python`, establece `shareverance.organization_id` local a cada transacción `BEGIN READ ONLY` con el ID autorizado por Java, cierra siempre la transacción (`COMMIT`/`ROLLBACK`) antes de liberar o cerrar la conexión, construye el tramo continuo de hasta 60 meses confirmados anteriores a `target_month` (completando con `0` los meses confirmados sin gasto desde el primer mes con gasto de cada categoría), protege `POST /internal/v2/predictions/monthly` con Bearer token compartido (`PYTHON_CLIENT_SECRET` y `PYTHON_CLIENT_SECRET_PREVIOUS` opcional para rotación) y retira el callback `GET /api/v1/predictions/organization/{id}/monthly` (`java_client.py`).

## Arquitectura requerida

Java autoriza al usuario y envía organización/período por HTTP interno autenticado. Python consulta vistas de PostgreSQL mediante credencial exclusiva de lectura, sin SELECT sobre tablas de negocio ni permisos de escritura. Contrato SQL en [postgre.md](postgre.md), contrato HTTP en [contratos_api.md](contratos_api.md).

Organizar routers, servicios, repositorio de vistas, validadores y modelo separadamente. No duplicar lógica de escritura, autorización de usuario o reparto. El contexto de organización no puede provenir de una llamada pública sin validación de Java. La restricción SQL por organización debe resolverse y probarse antes de considerar terminado el acceso.

## Historial

ARS, importes enteros en centavos, fecha del gasto, solo gastos vigentes. Máximo actual del contrato: 60 meses y 100 categorías. Meses ordenados y consecutivos; categorías únicas y cobertura conocida. No inferir que no hubo gasto solo porque no hay filas. Cero exige cobertura confiable.

Mes objetivo: siguiente al último mes cerrado de cobertura confiable, no siguiente al último registro con gasto. El recorte de 60 meses debe recortar también los metadatos de cobertura. Incluir meses cero hasta el último cerrado cuando corresponda. Categorías inactivas conservan sus gastos históricos; no se pierde gasto por filtrar el catálogo activo.

## Analítica

Total organizacional suma gastos una vez; no sumar gastos y repartos juntos. Participación individual suma sus partes. Evolución por mes y distribución por categoría deben conciliar con historial usando igual estado/período.

Comparación: diferencia = actual − anterior. Porcentaje = diferencia / anterior × 100 si anterior > 0; si anterior = 0, porcentaje null y mostrar sin base de comparación. Sin cobertura, devolver insuficiencia en lugar de cero. Una comparación de mes parcial debe señalar el corte y no presentarse como equivalente a dos meses cerrados.

## Predicción real

E6.1 incorpora un candidato SES (`alpha=0.3`, seis meses mínimos, versión 1) y herramientas offline de evaluación. El modo operativo predeterminado continúa siendo `mock`; `PREDICTOR_MODE=ses` es una opción explícita experimental, transmitida por Compose desde `.env`, para comprobar el candidato. En este modo `simulated:false` identifica el cálculo SES, sin certificar calidad predictiva ni aceptación de E6.1. No se activa automáticamente por obtener métricas ni por pasar pruebas. Dataset temporal de aceptación, métrica de decisión, umbral y regla de selección siguen abiertos y deben definirse antes de evaluar para aceptación. La UI predictiva E6.2 está implementada: consulta Java, muestra método/versión, insuficiencia y distinción mock/SES experimental. La aceptación integral de la vista y la ampliación multimes permanecen pendientes.

Desde `service-python`, el informe reproducible de regresión sintética se obtiene con:

```sh
uv run --locked python -m app.predictors.evaluation tests/fixtures/prediction_synthetic.json
```

El informe identifica SHA-256 del archivo, 18 meses sintéticos, seis meses iniciales, 12 orígenes temporales, `alpha=0.3` y `accepted:null`. Cada objetivo recibe únicamente meses anteriores; no hay ajuste con meses futuros. Compara SES, último mes y media histórica sobre los mismos orígenes. El CLI recibe valores mensuales en centavos; la fuente debe garantizar previamente meses consecutivos y cobertura confiable, porque una lista numérica no acredita por sí sola esas condiciones. El fixture no es representativo ni reemplaza el dataset de aceptación.

Métricas internas con unidades explícitas: `mae_ars` y `rmse_ars` convierten centavos a ARS; `mape_percentage` usa sólo meses reales positivos y declara `mape_samples`; `wape_percentage` suma todos los errores absolutos, incluidos meses reales cero, y divide por la suma de importes reales. Si la suma real es cero, WAPE es null: se conserva MAE/RMSE y no se inventa error relativo cero. Redondeo del informe a dos decimales con ROUND_HALF_UP. La media histórica sólo utiliza la ventana anterior y redondea su estimación a centavos con la misma regla. Estos campos pertenecen al informe offline y no cambian los DTO HTTP.

Resultado del fixture sintético: MAE SES 2460,04 ARS, último mes 1075,00 ARS y media histórica 4666,56 ARS. SES pierde ante último mes en esta serie; no se concluye superioridad general ni se promueve un método operativo. Los resultados no fijan retroactivamente un umbral de aceptación.

Mínimo del contrato actual: seis meses válidos por serie. Insuficiencia devuelve estimación null y explica meses requeridos/utilizados. Categorías con historia insuficiente pueden producir estado PARTIAL; no rellenar con un valor inventado.

Implementar suavizado exponencial simple y compararlo con último mes y media histórica como referencias. Elegir parámetros usando únicamente datos anteriores a cada fecha de evaluación. Backtesting con ventana temporal progresiva; reportar MAE en ARS y una medida relativa robusta a ceros cuando sea aplicable. No dividir aleatoriamente meses ni usar el futuro en entrenamiento.

Umbral cuantitativo de aceptación y dataset deben fijarse antes de evaluar. No se exige que un modelo complejo reemplace una referencia que funciona mejor. Mostrar método y versión; `simulated:false` identifica el cálculo SES experimental, sin implicar aceptación de precisión. Conservar su advertencia explícita hasta la evaluación de aceptación. Predicción del siguiente mes y proyección del cierre del actual son capacidades diferentes; esta última no entra automáticamente.

## Evaluación con el CSV de demostración

Ampliación planificada: [demostración integrada y horizonte de 4–6 meses](road_map.md#predictor-multihorizonte). El código y los comandos de esta guía siguen evaluando **un mes por origen**. No ofrecen todavía previsiones de 4/5/6 meses. La nueva evaluación debe mantener un origen común para todos los meses de cada previsión, sin incorporar reales futuros entre pasos; reportará errores por horizonte. La carga con usuario/organización/gastos en base aislada es P1-D, pendiente de implementación y revisión del plan.

El [plan complementario P1–P5](road_map.md#plan-complementario-del-predictor), seguimiento [#59](https://github.com/seba615/shareverance/issues/59), organiza el trabajo del equipo. Esta guía permite probar el evaluador **existente**; el evaluador CSV completo y la aceptación final todavía son trabajo pendiente.

### Datos y límites

`demo/gastos_prueba.csv` contiene totales mensuales, no gastos individuales: 50 organizaciones inventadas, ocho categorías por organización y 36 meses (2023–2025), en centavos ARS. La [procedencia documentada](../demo/README_DEMO.md) es estadística pública de canastas adaptada al proyecto. No equivale a 50 historiales independientes de hogares. P1 debe documentar la transformación y cobertura antes de usarlo como conjunto de aceptación. No se consulta la fuente externa ni se valida su transformación mediante esta guía.

No completar huecos con cero ni deducir cobertura del simple hecho de que existan filas. Para esta prueba se adopta la cobertura completa del ejemplo documentado de organización 1; esa convención de demostración no declara cobertura en PostgreSQL. Otros conjuntos requieren una declaración verificable de cobertura.

### Prueba exploratoria disponible hoy

Requisito: Docker Desktop con contenedores Linux. Ejecutar PowerShell desde la raíz del repositorio. La construcción utiliza las dependencias declaradas y `uv.lock`; no inicia servicios, publica puertos ni carga/modifica la base. Los resultados quedan en `backend-java/target/predictor-exploracion`, ignorado por Git.

El ejemplo selecciona organización 1 y **sólo 2023–2024** para mantener 2025 reservado. Suma categorías una vez por mes. Para probar una categoría, cambiar la selección como se indica debajo.

```powershell
$csvPath = (Resolve-Path 'demo/gastos_prueba.csv').Path
$outputDir = Join-Path $PWD.Path 'backend-java/target/predictor-exploracion'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$selectedRows = @(Import-Csv -LiteralPath $csvPath -Delimiter ';' | Where-Object {
    $_.organization_id -eq '1' -and $_.month -ge '2023-01' -and $_.month -le '2024-12'
})
if ($selectedRows.Count -ne 192) { throw 'Se esperaban 24 meses por ocho categorías; revisar CSV y cobertura.' }
if (@($selectedRows | Group-Object month,category_id | Where-Object Count -ne 1).Count) {
    throw 'Claves mensuales duplicadas.'
}
$monthlyGroups = @($selectedRows | Group-Object month | Sort-Object Name)
$amounts = @($monthlyGroups | ForEach-Object {
    if ($_.Count -ne 8) { throw 'Mes incompleto para esta demostración.' }
    [long]$total = 0
    foreach ($row in $_.Group) {
        if ($row.currency -ne 'ARS' -or $row.amount_minor -notmatch '^\d+$') {
            throw 'Moneda o importe inválido.'
        }
        $total += [long]$row.amount_minor
    }
    $total
})
$expectedMonths = @(0..23 | ForEach-Object { ([datetime]'2023-01-01').AddMonths($_).ToString('yyyy-MM') })
if (($monthlyGroups.Name -join ',') -ne ($expectedMonths -join ',')) { throw 'Meses discontinuos.' }
$inputFile = Join-Path $outputDir 'organizacion-1-desarrollo.json'
$inputJson = @{ amounts_minor = $amounts } | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText($inputFile, $inputJson, [Text.UTF8Encoding]::new($false))
Get-FileHash -LiteralPath $csvPath -Algorithm SHA256
docker build -t shareverance-predictor-evaluacion:local service-python
if ($LASTEXITCODE -ne 0) { throw 'Falló la construcción de la imagen.' }
$report = docker run --rm --network none --mount "type=bind,source=$outputDir,target=/evaluation,readonly" shareverance-predictor-evaluacion:local /app/.venv/bin/python -m app.predictors.evaluation /evaluation/organizacion-1-desarrollo.json
if ($LASTEXITCODE -ne 0) { throw 'Falló la evaluación.' }
$report | Set-Content -LiteralPath (Join-Path $outputDir 'informe-organizacion-1.json') -Encoding utf8
$report
```

La preparación es específica del ejemplo (IDs y ocho categorías); no sustituye al validador general de P3. Para categoría 1, filtrar adicionalmente `category_id -eq '1'`, exigir 24 filas y una fila por mes en vez de 192 y ocho; usar otros nombres de entrada/informe para conservar ambos resultados.

Salida esperada: `series_months:24`, `min_train_months:6`, `alpha:0.3`, `accepted:null`, hash de la entrada JSON y métricas de SES/último mes/media histórica. Cada método evalúa los mismos 18 objetivos, de julio de 2023 a diciembre de 2024, utilizando sólo meses anteriores. `accepted:null` significa que el informe no acepta ni activa ningún modelo. Guardar también hash del CSV, commit (`git rev-parse HEAD`), selección y comandos junto al informe si se comparte evidencia. No publicar datos privados.

### Evaluación final que debe implementar el equipo

P2 propone desarrollo 2023–2024 y reserva enero–diciembre de 2025. El equipo debe fijar la métrica principal, umbral de mejora y tolerancia de degradación por serie **antes** de mirar resultados finales. Si 2025 ya se utilizó para seleccionar parámetros, deja de ser una reserva independiente: declararlo y acordar otra evaluación, sin presentarlo como prueba intacta.

P3 debe aceptar CSV y rango objetivo explícitos; separar organización/categoría, validar cobertura/continuidad/unidades/límites y emitir hash original, commit/configuración, exclusiones, cantidades de muestras y predicción/error por mes. No basta con pasar sólo los doce valores de 2025 al CLI actual: perdería el entrenamiento anterior y evaluaría sólo los últimos seis. El CLI actual recorre todos los objetivos desde el séptimo mes y no tiene opción de reserva final.

En P4 se congelan método/parámetros usando desarrollo. Durante la reserva se permite incorporar el valor real de un mes ya transcurrido para predecir el siguiente (ventana progresiva), sin reajustar parámetros con los resultados reservados. Reportar MAE en ARS y WAPE por serie y por nivel; identificar cómo se agrega y evitar que una organización grande o la duplicación entre total y categorías oculten degradaciones. MAE nominal de 2023 y 2025 no implica el mismo poder adquisitivo; cualquier ajuste por inflación requiere fuente y protocolo separados, sin agregarlo silenciosamente.

Las comparaciones son offline. Probarlas en la pestaña Predicción requiere P5 y datos de desarrollo cargados de forma aislada mediante Java/procedimiento acordado, preservando relaciones y cobertura explícita. Este CSV no se carga automáticamente ni el informe cambia `PREDICTOR_MODE`. No modificar el entorno de validación humana para ejecutar esta guía.

## Pruebas y operación

Conservar pruebas del contrato actual mientras se migra. Cubrir tipos estrictos, campos desconocidos, meses duplicados/discontinuos, recorte, categorías nuevas/inactivas, cero frente a falta de datos, seis meses, límites de enteros y totales. Integración real de vistas y permisos con dos organizaciones; caída SQL, pool y limpieza de contexto entre conexiones. Evaluación reproducible del modelo y circuito Java→Python→PostgreSQL. Logs sin datos de comprobantes ni credenciales.

El POST histórico con historial puede servir para pruebas durante transición; no debe confundirse con el contrato operativo nuevo ni quedar expuesto sin autenticación. Retirar la dependencia inversa Python→Java al completar migración.
