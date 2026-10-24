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

<a id="entorno-demo-predictor"></a>

### Entorno integrado P1-D: iniciar, cargar y probar

Implementación de [#63](https://github.com/seba615/shareverance/issues/63), relacionada con #59. Requiere Git y Docker Desktop con contenedores Linux; ejecutar PowerShell desde la raíz. No requiere Python local. Cada entrega posterior debe enlazar este procedimiento e indicar cualquier comando adicional necesario.

```powershell
# Construir/iniciar sólo la demostración (primera construcción requiere Internet).
.\scripts\predictor-demo.ps1 start
# Validar CSV, cargar por Java y verificar integración.
.\scripts\predictor-demo.ps1 load
# Comprobar sin crear entidades de negocio ni confirmar cobertura.
.\scripts\predictor-demo.ps1 check
.\scripts\predictor-demo.ps1 status
# Regresiones del cargador sin acceso a la base.
.\scripts\predictor-demo.ps1 test
# Detener sólo la demostración, sin borrar datos.
.\scripts\predictor-demo.ps1 stop
```

Para volver a usarlo: `start` y `check`. Los errores Docker/HTTP/validación producen fallo; no continuar como si una carga parcial estuviera completa. No hay reintentos automáticos de mutaciones: corregir la causa y repetir `load`. Recupera registros propios ya confirmados por Java y crea los faltantes. No hay atomicidad de toda la importación; cada operación Java es transaccional. El contenedor de carga tiene nombre fijo para impedir ejecuciones simultáneas del wrapper; evitar ediciones manuales concurrentes. Tras un corte abrupto revisar `docker ps -a --filter name=shareverance-predictor-demo-loader` si Docker informa conflicto, sin eliminar recursos ajenos.

Acceso: [http://localhost:5176](http://localhost:5176), ADMIN habitual `admin@shareverance.com`, con la contraseña habitual configurada en desarrollo (no publicada aquí), organización **Demo CSV 1 - desarrollo 2023-2024**. Usuario sin pertenencia: `predictor.sinacceso@example.test`, contraseña sintética `DemoPredictor123!`. El usuario `predictor.admin@example.test` / `DemoPredictor123!` se conserva como autor de la importación; la carga incorpora al ADMIN habitual mediante Java y verifica su membresía activa. El acceso con su contraseña se comprueba en la UI, sin incluirla en el cargador ni en esta guía. Son credenciales públicas exclusivamente de demostración. El seed dev también crea una organización inicial: seleccionar la del CSV, sin asumir sus IDs internos.

El proyecto fijo `shareverance-predictor-demo` usa `demo/predictor.env.example`, base `shareverance_predictor_demo`, red/imágenes propias y volúmenes `shareverance-predictor-demo_postgres_data` y `shareverance-predictor-demo_receipts_data`. El wrapper aísla variables conocidas de la sesión para no heredar configuración habitual. Sólo publica frontend en 127.0.0.1:5176; Java/Python/PostgreSQL son internos. No usa `compose.tools.yaml`, `.env` habitual ni `seed_demo_data.sql`. Si 5176 está ocupado, identificar al dueño; no detener otro entorno ni cambiar destinos silenciosamente.

La selección es organización externa 1, **2023-01 a 2024-12**, ocho categorías y 192 filas, validada antes de escribir. Cada fila es un gasto sintético individual al último día del mes, con descripción identificable y parte exacta; no representa movimientos diarios reales ni tiene comprobante. Java crea usuario, organización, membresía, categorías, gastos y partes; confirma cobertura después de conciliar. No hay SQL de escritura ni nuevas migraciones/contratos. La reserva de 2025 no se carga ni evalúa; el hash del archivo completo sólo identifica su versión.

La salida JSON contiene hash CSV, mapeo de IDs externos/internos, corte y conteos. Se puede guardar localmente:

```powershell
New-Item -ItemType Directory -Force backend-java/target/p1d | Out-Null
.\scripts\predictor-demo.ps1 check | Tee-Object backend-java/target/p1d/verificacion.json
```

Repetir `load` debe mostrar `created_this_run:0`, con **192 gastos, 192 partes, ARS 31.211.212,92 y 24 meses confirmados**. Se rechazan gastos propios modificados/anulados, partes divergentes, gastos ajenos, cobertura fuera del corte o cambio de CSV; no se sobrescribe ni elimina para resolverlos. Para ensayar edición, crear otra organización desde la UI. Los marcadores son identificadores operativos, no firmas ni permisos.

Recorrido manual:

1. Ingresar como ADMIN demo y seleccionar la organización del CSV.
2. Historial: filtrar 2023-01-01 a 2024-12-31. Calendario: navegar a ese rango; los totales están concentrados artificialmente al cierre mensual.
3. Panel: elegir enero de 2023 a diciembre de 2024; la ventana predeterminada actual puede no incluir estos datos.
4. Predicción: resolver automáticamente o elegir **2025-01**. Mock devuelve **ARS 2.114.866,85**, último total de diciembre de 2024, con 24 meses usados. Verifica integración, no precisión ni horizonte de 4–6 meses.
5. El usuario sin pertenencia no ve la organización; `check` verifica además HTTP 403 al solicitar su predicción.

`check` concilia cada gasto/parte y cobertura, los 24 totales/categorías de la API analítica Java→Python→vistas SQL, la predicción y la denegación. Login/CSRF sí generan sesiones/cookies aunque no se creen entidades de negocio. El wrapper todavía no permite 50 organizaciones, otros cortes ni SES. Las ampliaciones requieren nuevas tareas y guía actualizada. No usar `down -v` ni borrar volúmenes para repetir.

Ampliación planificada: [demostración integrada y horizonte de 4–6 meses](road_map.md#predictor-multihorizonte). El código y los comandos de esta guía siguen evaluando **un mes por origen**. No ofrecen todavía previsiones de 4/5/6 meses. La nueva evaluación debe mantener un origen común para todos los meses de cada previsión, sin incorporar reales futuros entre pasos; reportará errores por horizonte. P1-D tiene una implementación inicial de carga aislada para organización 1 hasta 2024-12, descrita arriba, con aceptación manual pendiente.

El [plan complementario P1–P5](road_map.md#plan-complementario-del-predictor), seguimiento [#59](https://github.com/seba615/shareverance/issues/59), organiza el trabajo del equipo. Esta guía permite probar el evaluador **existente**; el evaluador CSV completo y la aceptación final todavía son trabajo pendiente.

### Datos y límites

`demo/gastos_prueba.csv` contiene totales mensuales, no gastos individuales: 50 organizaciones inventadas, ocho categorías por organización y 36 meses (2023–2025), en centavos ARS. La [procedencia documentada](../demo/README_DEMO.md) es generación sintética, sin calibración oficial aplicada. No equivale a 50 historiales independientes de hogares. P1 debe distinguir cobertura del universo sintético de representatividad real y documentar generador/correspondencias pendientes antes de una aceptación experimental. No se consulta la fuente externa ni se valida su transformación mediante esta guía.

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

<a id="auditoria-p1"></a>

### Auditoría previa P1: cómo ejecutarla

Seguimiento [#67](https://github.com/seba615/shareverance/issues/67). El auditor no calcula predicciones ni errores, no importa gastos y no confirma cobertura real. Desde la raíz, con Docker Desktop Linux iniciado:

```powershell
.\scripts\predictor-demo.ps1 audit
.\scripts\predictor-demo.ps1 test
```

No requiere `start`/`load`: ejecuta un contenedor sin red y CSV en sólo lectura. La construcción de imagen puede necesitar Internet. Para guardar el manifiesto, crear `backend-java/target/p1-audit` y redirigir stdout de `audit` a `manifest.json` allí; comprobar salida satisfactoria antes de usarlo. El entorno integrado y acceso siguen en la [guía P1-D](#entorno-demo-predictor).

Alternativa offline con Python 3 local, sólo biblioteca estándar:

```powershell
python scripts/audit_predictor_dataset.py --csv demo/gastos_prueba.csv
python -m unittest discover -s scripts -p test_audit_predictor_dataset.py -v
python -m unittest discover -s scripts -p test_load_predictor_demo.py -v
```

Valida columnas UTF-8/`;`, IDs positivos, ARS, importes enteros no negativos dentro del límite de negocio, claves únicas, nombres estables, período 2023–2025, ocho categorías por organización y 36 meses por categoría. Huecos o categorías ausentes generan manifiesto incompleto y salida fallida; campos inválidos abortan. No imputa ceros. Informa cantidad de organizaciones, pero el universo debe contrastarse con la procedencia.

El manifiesto incluye SHA-256, series y meses faltantes. `coverage_confirmed:null` y `final_evaluation_eligible:null` impiden confundir estructura con aceptación. Busca totales organizacionales idénticos o exactamente proporcionales sólo en desarrollo 2023–2024. No encontrar coincidencias no demuestra independencia: puede existir escalado con redondeo, ruido u origen común. No utiliza 2025 para esa comparación ni calcula errores predictivos sobre la reserva.

CSV versionado: 14.400 filas, 50 organizaciones, 400 categorías, ocho por organización, 36 meses completos y cero filas de importe cero. Sin coincidencias idénticas/proporcionales exactas en totales organizacionales de desarrollo. Hash `99f1edca57202fc182dd74b469a832607a15c9e1d3a83f4ece3b6f6a69001996`.

**Procedencia aclarada para V1:** generación sintética con crecimiento nominal declarado de 4,5 % mensual, variación aleatoria y semilla no documentada; sin calibración INDEC ni fuente oficial directa. La cobertura declarada abarca sólo ocho categorías de hogares ficticios, no gasto real completo. Falta generador/fórmula exacta y correspondencia del paquete original con este CSV. No hay evidencia disponible de uso previo de 2025 para ajustar/comparar; no equivale a garantía de reserva intacta. La partición propuesta 2023 entrenamiento / 2024 selección / 2025 final encaja en desarrollo 2023–2024 de P2, sin alterar automáticamente las ventanas del evaluador. El equipo debe formalizar alcance, congelación y aceptación experimental; V1 no acredita precisión sobre hogares argentinos. P3 puede avanzar con datos sintéticos/desarrollo sin abrir la reserva.

### Protocolo formal de evaluación y reglas cuantitativas (P2)

**Estado: propuesta documental para aceptación manual, condicionada a P1.** Los umbrales del 5 % y 20 % son criterios de decisión propuestos, no resultados empíricos ni valores ya aceptados. P3 puede preparar el evaluador, pero la evaluación final requiere P1, aceptación explícita del protocolo y congelación de su configuración. Esta entrega no acepta ni activa un modelo.

Este protocolo corresponde a **un mes por origen (h=1)**. Los horizontes de 4–6 meses requieren P2-H y sus criterios por horizonte; no heredan automáticamente estos umbrales.

#### 1. Partición temporal estricta y reserva final

- Desarrollo: enero de 2023 a diciembre de 2024, para exploración, comparación y ajuste de parámetros mediante validación temporal.
- Reserva propuesta: enero a diciembre de 2025, evaluada una sola vez tras congelar en código el candidato, sus parámetros, las referencias, la política operativa y el protocolo. P1 debe comprobar procedencia, cobertura y si esos valores ya se usaron para seleccionar métodos; una reserva utilizada para selección deja de ser independiente y exige otra evaluación acordada.
- Para predecir un mes objetivo, usar solamente meses anteriores. En la reserva progresiva se puede incorporar un real ya transcurrido para el objetivo siguiente, sin reajustar parámetros ni elegir métodos a partir de los errores reservados.
- Registrar hash del CSV, commit, configuración y manifiesto de series/objetivos antes de ejecutar la reserva. Una corrección de implementación posterior debe declararse; no presentar una segunda ejecución como una primera evaluación intacta.

#### 2. Series, cobertura y comparabilidad

Una serie es el total mensual de una organización o una categoría identificada por organización y categoría. Cada objetivo requiere al menos seis meses consecutivos, cerrados y con cobertura confirmada, inmediatamente anteriores; se utiliza todo el historial continuo válido disponible del período de entrenamiento acordado. La insuficiencia se registra como `INSUFFICIENT_HISTORY`, sin estimación ni error inventados. No imputar ceros a huecos; cero confirmado sí es una observación válida.

Para la decisión final propuesta se exige cobertura de los doce objetivos de 2025 y sus antecedentes para cada serie elegible. P1 debe fijar el universo elegible antes de consultar resultados reservados. Informar series esperadas, elegibles, excluidas, motivo y muestras por método; una exclusión no puede depender del error obtenido. Si falta cobertura, no reducir silenciosamente el universo: acordar otro protocolo antes de abrir la reserva.

Todos los métodos deben evaluarse en exactamente las mismas series y fechas. Un fallo de cálculo de un candidato sobre un objetivo elegible bloquea su promoción; no se excluye ese objetivo para favorecerlo. Cada nivel requiere al menos una serie elegible; sin ella, la aceptación conjunta queda pendiente.

Reportar por separado totales organizacionales y categorías. Las organizaciones adaptadas del CSV no son observaciones independientes ni acreditan representatividad de hogares reales; P1 debe identificar su relación y las limitaciones de cualquier conclusión.

#### 3. Métricas y agregación reproducible

Calcular errores desde importes enteros en centavos. Convertir las métricas monetarias a ARS para presentación con `ROUND_HALF_UP` a dos decimales; las comparaciones de aceptación usan valores sin redondear.

- **MAE por serie:** suma de errores absolutos dividida por cantidad de objetivos.
- **WAPE por serie:** suma de errores absolutos dividida por suma de reales, multiplicada por 100; `null` si el denominador es cero.
- **RMSE por serie:** raíz del promedio de errores cuadrados, expresada en ARS.
- **MAPE informativo:** promedio del error porcentual absoluto solo sobre objetivos con real positivo; declarar `mape_samples` y devolver `null` sin muestras.
- **MAE de decisión por nivel:** media aritmética de los MAE de sus series elegibles, con el mismo peso por serie. Nunca combinar total organizacional y categorías en una sola métrica. La decisión exige cumplir ambos niveles.
- WAPE de resumen por nivel: sumar errores absolutos y reales de sus observaciones elegibles antes de dividir; `null` si la suma real es cero. RMSE y MAPE se conservan por serie; no intervienen en el umbral primario.

El peso igual por serie no elimina el efecto de escalas monetarias distintas. Publicar MAE, WAPE, diferencias y muestras por serie, además de resúmenes, para que los totales grandes no oculten degradaciones. Todo se expresa en ARS nominales: no demuestra precisión ajustada por inflación ni permite inferir causas del crecimiento del gasto. Un ajuste por inflación requiere fuente y protocolo separados.

#### 4. Regla propuesta de promoción

Aplicar las condiciones siguientes **por separado en cada nivel**, sin elegir un ganador por serie usando la reserva:

1. **Mejora global:** MAE del candidato menor o igual a 0,95 por el menor MAE de nivel de `last_month` y `historical_mean`. Esto exige al menos 5 % de mejora frente a ambas referencias. Si esa mejor referencia tiene MAE cero, un candidato con MAE cero empata: no demuestra mejora ni se promueve; un error positivo pierde.
2. **Frecuencia de degradación:** para cada serie, comparar el MAE candidato con el menor MAE de sus dos referencias. Contar como degradada si es estrictamente mayor; un empate no cuenta. Exigir `5 * series_degradadas <= series_elegibles`, equivalente a un máximo del 20 %, sin redondear cantidades. El denominador incluye todas las series elegibles del nivel fijadas previamente.
3. **Magnitud de degradación:** el 20 % anterior mide frecuencia, no tamaño del error. Informar por serie la diferencia absoluta y relativa contra la mejor referencia, además del máximo deterioro. Con referencia cero y candidato positivo, la degradación relativa es indefinida (`null`), debe señalarse explícitamente y cuenta como serie degradada. No se inventa un límite adicional de magnitud: este riesgo requiere revisión manual antes de aceptar el protocolo; si se exige un tope, fijarlo antes de abrir la reserva.
4. **Decisión:** cumplir los umbrales solo habilita revisión humana de evidencia; no activa el modelo automáticamente ni demuestra precisión en usuarios reales. Un nivel que incumple impide la promoción conjunta propuesta.

#### 5. Método operativo y control de sesgo

La política operativa se elige exclusivamente en desarrollo y se congela antes de la reserva. Como referencia operativa propuesta, elegir por nivel el método simple con menor MAE de desarrollo; en empate, `last_month`. La selección queda registrada y no se cambia por serie mirando errores de 2025.

Registrar también error medio firmado por serie, definido como promedio de predicción menos real: positivo indica sobreestimación y negativo subestimación. Permite inspeccionar sesgo, pero no se usa la expresión «sesgo divergente» como criterio automático sin umbral definido. Cualquier veto cuantitativo adicional por sesgo debe acordarse y congelarse antes de evaluar la reserva.

Si el candidato no cumple, conservar la referencia elegida en desarrollo y documentar los resultados adversos. No seleccionar retrospectivamente la referencia ganadora de 2025 para presentarla como validación independiente; hacerlo requeriría una nueva evaluación. Cualquier política híbrida o fallback por serie debe definirse con desarrollo y evaluarse como candidato completo congelado.

---

### Evaluación final que debe implementar el equipo

<a id="evaluador-p3"></a>

#### P3 inicial: evaluador CSV de desarrollo

Seguimiento [#69](https://github.com/seba615/shareverance/issues/69), basado en la auditoría P1 y propuesta P2. Esta entrega permite experimentar con **h=1 y objetivos sólo de 2023–2024**. Reutiliza los predictores SES alpha 0.3, último mes y media histórica; no selecciona ni activa un modelo. Cualquier rango que alcance 2025 se rechaza. No sustituye la aceptación/congelación pendiente de P2 ni implementa P2-H/P3-H.

Desde la raíz, con Docker Desktop Linux activo:

```powershell
# No iniciar base ni frontend para evaluar offline.
New-Item -ItemType Directory -Force backend-java/target/p3 | Out-Null
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12 > backend-java/target/p3/desarrollo.json
# Si el comando falla, no utilizar el archivo parcial.
.\scripts\predictor-demo.ps1 test
```

El contenedor ejecuta sin red, scripts/CSV en sólo lectura y dependencias declaradas en el Dockerfile/lock Python. La construcción puede necesitar Internet. Los logs de construcción no se mezclan con stdout del JSON; errores conservan salida fallida. El evaluador no requiere ni altera servicios de aplicación. Para iniciar el entorno integrado seguir [P1-D](#entorno-demo-predictor), cuyo predictor operativo no cambia.

El JSON identifica hash original del CSV, commit proporcionado por Git (base si hay cambios locales sin commit), SHA-256 del archivo evaluador, rango/alpha/mínimo/versión de métodos. Incluye predicción, real, error absoluto/firmado por objetivo y método, métricas por serie y resúmenes **separados** organización/categoría. La media de MAE por nivel usa peso igual por serie y se conserva además como fracción exacta de centavos; WAPE agrupado usa suma de errores/suma de reales. No decidir con importes redondeados. El informe siempre declara `accepted:null`, `model_promoted:false` y `reserve_evaluated:false`.

Cada objetivo recibe sólo el historial continuo anterior hasta el primer hueco, sin completar ceros ni consultar observaciones futuras. Con menos de seis meses: `INSUFFICIENT_HISTORY`; sin real del objetivo: `MISSING_TARGET`. Ambos conservan el punto sin estimación/error inventados. Los resúmenes usan sólo series con todos los objetivos solicitados válidos; informan universo esperado, completo y excluido. Esta exclusión técnica no constituye aceptación del universo final de P2. Una excepción del predictor aborta el informe, no elimina objetivos silenciosamente. El universo organizacional requiere ocho categorías; un mes total sólo existe si las ocho tienen observación. Cobertura se asume **declarada para el experimento sintético**, no real.

La ejecución sobre CSV V1 con objetivos 2024 produjo 50 series organizacionales completas y 400 categorías completas, cero excluidas, 5.400 puntos compartidos por los tres métodos. MAE medio por organización: SES 262.123,06 ARS, último mes 119.270,11 ARS, media histórica 649.680,74 ARS; por categoría: 35.975,26 / 27.109,02 / 81.344,79 ARS respectivamente. SES no mejora último mes en este experimento de desarrollo. No es evaluación final ni precisión demostrada sobre hogares reales; no se ajustaron parámetros con 2025 ni se aplicaron umbrales de promoción.

La propuesta P2 anterior define desarrollo 2023–2024, reserva 2025 y reglas de decisión para revisión. Su aceptación manual, la auditoría P1 y la resolución de cualquier parámetro adicional deben registrarse **antes** de mirar resultados finales. Si 2025 ya se utilizó para seleccionar parámetros, deja de ser una reserva independiente: declararlo y acordar otra evaluación, sin presentarlo como prueba intacta.

P3 debe aceptar CSV y rango objetivo explícitos; separar organización/categoría, validar cobertura/continuidad/unidades/límites y emitir hash original, commit/configuración, exclusiones, cantidades de muestras y predicción/error por mes. No basta con pasar sólo los doce valores de 2025 al CLI actual: perdería el entrenamiento anterior y evaluaría sólo los últimos seis. El CLI actual admite rango objetivo y exploración explícita de reserva, pero no una evaluación final acreditada.

#### P4: barrido reproducible y reserva histórica exploratoria

Seguimiento [#71](https://github.com/seba615/shareverance/issues/71). La [evidencia corregida](evidencia/p4/README.md) conserva las métricas ya publicadas de 2025 como exploratorias: no consta aceptación previa de P2 ni manifiesto/configuración congelados antes de abrir la reserva. No se acredita ejecución única, aceptación de calidad o selección operativa independiente. 2025 ya fue consultado y no debe presentarse como intacto en una evaluación posterior. El código no implementa un modo de evaluación final acreditada.

Para reproducir desarrollo desde la raíz, sin iniciar servicios y con Docker Desktop Linux:

```powershell
New-Item -ItemType Directory -Force backend-java/target/p4 | Out-Null
.\scripts\predictor-demo.ps1 test
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12 -SweepAlpha > backend-java/target/p4/desarrollo-alpha.json
# Alternativa para un único alpha, sólo desarrollo:
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12 -Alpha 0.7
```

El grid fijo 0.1/0.3/0.5/0.7/0.9/0.99/1.0 se ejecuta únicamente en desarrollo y conserva hashes, configuración, métricas y deterioro exacto frente a la mejor referencia por serie. No combinar SweepAlpha con AllowReserve. Alpha 0.7 mejora el MAE de categorías frente a último mes (26.388,43 vs 27.109,02 ARS), pero no organizaciones; alpha 1 reproduce último mes. No afirmar imposibilidad general de mejora. El candidato histórico alpha 0.3 no se presenta como resultado óptimo de selección.

AllowReserve permanece como acceso **exploratorio explícito**, no como autorización metodológica de evaluación final. Exige enero–diciembre de 2025 completos, todas las series del archivo sin exclusiones y alpha histórico 0.3; rechaza rangos parciales/mixtos, huecos y ajustes de alpha sobre reserva. El informe marca synthetic_reserve_exploratory, accepted:null, model_promoted:false y final_evaluation_accredited:false. El flag no garantiza ejecución única ni verifica por sí solo un universo previamente acordado. Sus controles se prueban con fixtures sintéticos; no se reabre rutinariamente la reserva real.

Los resultados históricos muestran mayor error de SES 0.3 que de último mes, pero no convierten los umbrales propuestos en criterios aceptados ni promueven la referencia ganadora de 2025. Sus frecuencias de deterioro publicadas se comparaban con último mes; se identifican como tales y no como el cálculo P2 frente al mínimo de ambas referencias por serie. Las decisiones de P5 y la evaluación multihorizonte siguen pendientes.

Las comparaciones son offline. Probarlas en la pestaña Predicción requiere P5 y datos de desarrollo cargados de forma aislada mediante Java/procedimiento acordado, preservando relaciones y cobertura explícita. Este CSV no se carga automáticamente ni el informe cambia `PREDICTOR_MODE`. No modificar el entorno de validación humana para ejecutar esta guía.

## Pruebas y operación

<a id="protocolo-multihorizonte"></a>

### P5-H: integración de previsiones multihorizonte (4 a 6 meses)

Relacionado con [#81](https://github.com/seba615/shareverance/issues/81). Extensión operativa del predictor para admitir previsiones a mediano plazo (horizontes de 4, 5 o 6 meses) proyectadas desde un mes base de origen con cobertura histórica confirmada.

- **Contratos internos y públicos:**
  - Python: POST /internal/v2/predictions/forecast (protegido por token Bearer compartido interno, consulta de vistas SQL autorizadas acotada por tenant). Soporta hasta 60 meses continuos confirmados hasta origin_month.
  - Java: GET /api/v1/organizations/{organizationId}/predictions/forecast?originMonth=YYYY-MM&horizonMonths=4|5|6 valida pertenencia autorizada a la organización, resuelve mes de origen predeterminado y orquesta la consulta HTTP hacia Python.
  - UI React: PredictionPanel.tsx incorpora selector de modalidad entre Próximo mes (1 mes) y Previsión multihorizonte (4 a 6 meses), selector de horizonte (4, 5 o 6), visualización de trayectorias multimes para la organización y desglose por categorías, y advertencias explícitas de calidad NOT_EVALUATED e incertidumbre creciente.
- **Calidad predictiva e incertidumbre:** conforme a las conclusiones de P2-H, P4-H y P5, al no existir modelos de tendencia/inflación promovidos ni calibración de intervalos de confianza, las estimaciones multimes se emiten en estado quality.status: NOT_EVALUATED sin declarar intervalos de probabilidad.
- **Compatibilidad aditiva:** los contratos y endpoints vigentes de monthly no sufren alteraciones.

### P4-H: comparación descriptiva y estabilidad multihorizonte

Relacionado con [#79](https://github.com/seba615/shareverance/issues/79). `scripts/compare_forecast_csv.py` reutiliza P3-H con seis horizontes y una cohorte completa común para todas las ventanas 4/5/6, métodos y orígenes. Informa MAE exacto con igual peso por serie, WAPE agrupado, muestras, exclusiones y deterioro de SES contra la mejor de ambas referencias por serie. La selección de referencia es descriptiva, no una política de producción.

Desde la raíz con PowerShell y Docker Desktop Linux: `scripts/predictor-demo.ps1 compare` y `test`, sin iniciar servicios ni reevaluar 2025. [Evidencia, resultados, formato y comandos completos](evidencia/p4h/README.md). Se registran configuración y hashes; no son congelación previa para evaluación final. Datos sintéticos, umbrales multihorizonte pendientes, sin aceptación o promoción automática. La aplicación continúa en monthly/mock; su ejecución start/load/check/stop permanece en la [guía P1-D](#entorno-demo-predictor).

### P5: decisión conservadora de no promoción

Relacionado con [#77](https://github.com/seba615/shareverance/issues/77). Se mantiene mock predeterminado y SES 0.3 experimental: la evidencia sintética disponible no justifica promoverlo. No se acredita evaluación final ni cierre de aceptación predictiva. P2 conserva umbrales propuestos; P2-H requiere criterios por horizonte y regla conjunta acordados.

En desarrollo 2024, SES 0.3 empeora el MAE agregado frente a último mes en ambos niveles; 50/50 organizaciones y 313/400 categorías empeoran frente a la mejor de ambas referencias por serie. Los resultados de 2025 son históricos exploratorios; sus 316/400 categorías se compararon sólo contra último mes, no contra la regla exacta P2. P3-H aporta comparación descriptiva de MAE agregado por horizonte, sin un nuevo criterio formal de aceptación. [Evidencia, límites y comandos de prueba P5](evidencia/p5/README.md).

### P3-H: evaluador multihorizonte offline de desarrollo

Seguimiento [#75](https://github.com/seba615/shareverance/issues/75), basado en el protocolo P2-H ([#73](https://github.com/seba615/shareverance/issues/73)). Se implementó y ejecutó el evaluador multihorizonte offline sobre 7 orígenes comunes de desarrollo (diciembre de 2023 a junio de 2024), proyectando horizontes h=1..6 hacia objetivos enteramente contenidos en 2024 sin tocar la reserva 2025. Evidencia completa y resumen en [docs/evidencia/p3h/README.md](evidencia/p3h/README.md).

- **Comportamiento por horizonte:** para h=1..6, el error MAE en organizaciones de last_month crece desde ARS 104.353,98 (WAPE 5,82 %) en h=1 hasta ARS 513.548,48 (WAPE 23,02 %) en h=6; en categorías crece desde ARS 24.267,09 (WAPE 10,82 %) hasta ARS 65.909,27 (WAPE 23,63 %). SES y media histórica incurren en errores significativamente mayores en todos los horizontes.
- **Aislamiento temporal:** verificado mediante pruebas unitarias; las proyecciones futuras dependen exclusivamente del corte común y no cambian ante modificaciones en meses posteriores al origen.
- **Comandos ejecutables:** scripts/predictor-demo.ps1 forecast -FromOrigin 2023-12 -ToOrigin 2024-06 -MaxHorizon 6 o mediante Python directo con scripts/evaluate_forecast_csv.py.

### Propuesta P2-H: protocolo de desarrollo para 4–6 meses

Relacionado con [#73](https://github.com/seba615/shareverance/issues/73). Diseño para revisión, sin nuevo predictor ejecutable. El [contrato propuesto](contratos_api.md#propuesta-multihorizonte) separa origen histórico de los meses proyectados y conserva monthly. Un pronóstico de H=4/5/6 incluye todos los horizontes 1…H, desde el mismo origen. Mock/último mes y SES sin tendencia producirían valores planos por horizonte; eso no demuestra capacidad multimes ni modela inflación. Una estrategia recursiva sólo puede utilizar estimaciones internas identificadas, nunca reales futuros o cobertura artificial.

Propuesta de validación en desarrollo: comparar h=1…6 sobre orígenes comunes diciembre de 2023 a junio de 2024, cuyos seis objetivos caben en 2024. Son siete orígenes por serie, pendientes de confirmar con el protocolo; incorporar datos observados al avanzar a **otro origen** es válido, incorporarlos entre pasos de la misma previsión no. Mantener calendario Buenos Aires, ventanas continuas confirmadas y mínimo seis meses por serie; registrar exclusiones antes de comparar y no imputar huecos como cero.

El futuro evaluador P3-H debe guardar hash CSV/código/configuración, origen, objetivo, h, método, predicción, real, error absoluto/firmado y muestras. Comparar mismos orígenes/series para todos los métodos y horizontes; reportar MAE/WAPE por h y nivel organizacional/categoría, sin mezclar ambos niveles ni tratar ventanas solapadas como muestras independientes. Conservar error firmado y casos cero. Si un origen no tiene seis reales posteriores, no reducir su horizonte silenciosamente para favorecer el modelo.

No heredar los umbrales 5 %/20 % de la propuesta P2 h=1: falta acordar mejora/degradación por horizonte, regla conjunta para 4/5/6 y mínimo de muestras. Siete orígenes sintéticos pueden ser insuficientes para estimar intervalos o justificar calidad estable. Para bandas debe definirse método, cobertura empírica y conjunto de validación apropiado antes de mostrar probabilidades. 2025 ya fue consultado en P4; no se lo vuelve a declarar reserva intacta. La evaluación final futura necesita otra partición/conjunto acordado y registro previo; todo experimento con V1 sigue sin representar hogares reales.

Cómo revisar **hoy** desde la raíz con Docker Desktop Linux:

```powershell
# Evaluación offline vigente, únicamente h=1:
.\scripts\predictor-demo.ps1 test
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12
# Interfaz vigente para comprobar contexto, no multihorizonte:
.\scripts\predictor-demo.ps1 start
.\scripts\predictor-demo.ps1 load
.\scripts\predictor-demo.ps1 check
# localhost:5176, acceso según la guía P1-D; detener conservando datos:
.\scripts\predictor-demo.ps1 stop
```

El rango propuesto de orígenes aún no tiene comando ejecutable; no interpretar los parámetros FromMonth/ToMonth actuales como horizontes futuros. P3-H debe añadir comandos específicos y pruebas de fuga temporal, h=1 equivalente, horizonte exacto, orígenes incompletos y ceros. No se ejecutan servicios ni nuevas pruebas de aplicación para esta propuesta documental.

Conservar pruebas del contrato actual mientras se migra. Cubrir tipos estrictos, campos desconocidos, meses duplicados/discontinuos, recorte, categorías nuevas/inactivas, cero frente a falta de datos, seis meses, límites de enteros y totales. Integración real de vistas y permisos con dos organizaciones; caída SQL, pool y limpieza de contexto entre conexiones. Evaluación reproducible del modelo y circuito Java→Python→PostgreSQL. Logs sin datos de comprobantes ni credenciales.

El POST histórico con historial puede servir para pruebas durante transición; no debe confundirse con el contrato operativo nuevo ni quedar expuesto sin autenticación. Retirar la dependencia inversa Python→Java al completar migración.
