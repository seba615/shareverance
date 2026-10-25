# Roadmap del MVP 1

Fecha de revisión: 25/10/2026. Base inspeccionada: `1486354` (main; PR #87, #89 y #90 integrados).

Revisión documental estática: distingue implementación observada, evidencia de ejecuciones anteriores y aceptación pendiente. No se ejecutaron pruebas funcionales nuevas en esta consolidación.

## Síntesis vigente

Implementados en main: negocio, repartos individual/iguales, historial/calendario, cobertura y vistas SQL, OCR, analítica, predicción mensual y 4/5/6 meses, tendencia log-lineal y gráfico histórico/proyección (PR #89). PR #87 fija log_trend y la apertura inicial en cuatro meses. Esto es inventario de código, no cambio de estados del tablero ni aceptación. La evidencia por tarea conserva fecha, base y límites. Pendientes de aceptación: revisión humana integral, evaluación metodológica y operación; no falta implementar el gráfico.


## Estado y dependencias

Estados: IMPLEMENTADO significa código observado; PENDIENTE requiere trabajo; VERIFICADO exige ejecución y evidencia vinculada a commit. Este relevamiento no ejecutó pruebas. No asignar PASS por contratos, mocks o inspección estática.

Síntesis vigente de implementación: autenticación, organizaciones, miembros/categorías, gastos y partes individual/iguales, historial, calendario, vistas SQL/cobertura, consumidor Python, comprobantes/OCR, panel analítico y vista predictiva tienen código integrado. El callback Python→Java fue retirado; React consume Java y Java orquesta Python. Existen evidencia automatizada y comprobaciones parciales en las secciones por tarea; esto no equivale a aceptación integral.

Predictor: tendencia log-lineal experimental predeterminada, mock y SES opcionales explícitos; evaluación offline mensual/multihorizonte, entorno sintético P1-D con carga repetible y rutas/tablas de previsión 4/5/6 implementadas. #85 registra la corrección y verificación integrada del recorrido de demostración. El objetivo de presentación usa V1 sintético, sin exigir datos reales. El gráfico combinado ya está integrado; siguen pendientes revisión humana y aceptación; no se acredita precisión real. Respaldo/restauración y limpieza tienen evidencia aislada; no sustituyen aceptación integral.

<a id="etapas"></a>

## Etapas — planificación histórica

La tabla siguiente conserva la planificación inicial y sus estados de seguimiento; no es un inventario vigente de código. La síntesis anterior y las evidencias por tarea distinguen implementación y validación. Esta consolidación documental no cambia estados de otras tareas, no marca entregas aceptadas ni sustituye el cierre manual.

<details>
<summary>Plan inicial y estados históricos (no representan el inventario vigente)</summary>

| Etapa / ID | Entrega | Dependencia | Estado | Responsable por función |
| --- | --- | --- | --- | --- |
| E0.1 | Documentos por componente, contratos y reglas | Ninguna | Redactado; revisión técnica pendiente | Mantenimiento |
| E0.2 | Fijar parámetros abiertos listados abajo | E0.1 | PENDIENTE | Producto/arquitectura |
| E1.1 | ARS, estados activos y permisos uniformes | E0 | PENDIENTE | Java |
| E1.2 | Reparto determinista y transacción gasto/partes | E1.1 | PENDIENTE | Java/datos |
| E1.3 | Versión optimista, 409 y último ADMIN concurrente | E1.1 | PENDIENTE | Java/datos |
| E2.1 | Completar miembros/categorías y carga manual | E1 | PENDIENTE | Front/Java |
| E2.2 | Filtros, paginación, detalle, edición y anulación | E2.1 | PENDIENTE | Front/Java |
| E2.3 | Calendario con detalle por fecha | E2.2, parámetros calendario | PENDIENTE | Front/Java |
| E3.1 | Roles SQL, vistas y cobertura confiable | E1, aislamiento definido | PENDIENTE | Datos/Python |
| E3.2 | Java→Python autenticado; Python→vistas; retirar callback | E3.1 | PENDIENTE | Java/Python |
| E4.1 | Volumen privado, carga y descarga autorizada | E1 | PENDIENTE | Java/infraestructura |
| E4.2 | Motor OCR, normalización y resultado editable | E4.1, motor/contrato | PENDIENTE | Python/front |
| E5.1 | Comparación y agregaciones conciliadas | E3.2 | PENDIENTE | Python |
| E5.2 | Panel, gráficos y tablas equivalentes | E5.1 | PENDIENTE | Front |
| E6.1 | Modelo real y evaluación temporal | E3.2, umbral/dataset | PENDIENTE | Python |
| E6.2 | Vista predictiva y estados de insuficiencia | E6.1 | PENDIENTE | Front/Java |
| E7.1 | Ejecutar aceptación completa, respaldo y restauración | E2–E6 | PENDIENTE | QA/todos los componentes |

E3 y E4 pueden progresar independientemente una vez estabilizado E1 y sus contratos. Cada tarea incorpora pruebas de su componente, actualización documental y evidencia. Personas y fechas se asignan al iniciar; no se inventan compromisos.

</details>

## Plan complementario del predictor

El circuito P1-D (carga aislada), evaluadores P3/P3-H, comparaciones P4/P4-H y rutas P5-H están implementados. El modo actual log_trend se documenta en [Python](python_predictor.md), con [evidencia sintética de tendencia](evidencia/tendencia/README.md). El gráfico está integrado desde PR #89. P2/P2-H conservan propuestas de criterios, no aceptación acreditada; el trabajo metodológico #77 mantiene su alcance y responsable.

Para reproducir el entorno desde raíz con Docker Desktop Linux y PowerShell:

```powershell
./scripts/predictor-demo.ps1 start
./scripts/predictor-demo.ps1 load
./scripts/predictor-demo.ps1 check-forecast
# Abrir http://localhost:5176, organización Demo CSV 1, pestaña Predicción.
./scripts/predictor-demo.ps1 stop
```

Stop conserva volúmenes. Acceso sintético, selección y límites en la [guía de demostración](evidencia/demo-sintetica/README.md). La UI abre cuatro meses; cinco/seis y próximo mes son opciones. Esta revisión no ejecutó esos comandos ni certifica el gráfico por inspección.

### Trabajo y aceptación pendientes

- Revisión humana integral de permisos, sesión, OCR, gastos, calendario, panel y proyección; registrar evidencia por criterio A01–A19.
- Revisar en navegador el gráfico y su integración, accesibilidad, cambios rápidos de organización/origen y errores; la presencia del componente no acredita esos casos.
- Formalizar criterios metodológicos aún propuestos y limitaciones del conjunto sintético; 2025 ya fue consultado y no es reserva independiente intacta. No se exige conseguir datos reales para la presentación.
- Resolver parámetros operativos y de cuenta aún abiertos; no confundir herramientas implementadas de respaldo/limpieza con aceptación operativa.
- Revisar las brechas de producto identificadas en alcance/frontend (edición de categorías y presentación individual) y decidir su aceptación conforme al alcance. No se agregan aquí funciones nuevas.

### Antecedentes del plan y evidencia por entrega

<details>
<summary>Plan original P1–P5/P1-H–P5-H y comprobaciones históricas</summary>

Los estados, defaults y pendientes de este bloque corresponden a la fecha/base de cada entrega; no sustituyen la síntesis vigente. En particular, mock predeterminado, gráfico pendiente y 2025 como reserva describen etapas anteriores. No son instrucciones actuales ni aceptación de umbrales.

Preparación de presentación sintética, [#85](https://github.com/seba615/shareverance/issues/85), base `380fd72`: V1 sintético es el conjunto previsto para la entrega demostrativa; no se exige conseguir datos reales para finalizar esa presentación. No implica precisión aceptada sobre hogares argentinos. Se corrigen en P5-H los campos UI points/horizon_step incompatibles con DTO forecasts/horizon, el cierre que retenía horizonte 4 y la autorización GET forecast ausente. Se conserva asignación de #81; sin nuevas rutas, migraciones ni promoción de modelo. [Recorrido reproducible y evidencia](evidencia/demo-sintetica/README.md), con start/load/check/check-forecast/stop.

Verificado: 41 pruebas frontend y build correctos; Maven en construcción Java, 137 casos sin fallos/errores y 50 omitidos (87 ejecutados). Wrapper test aprobó 47 pruebas de scripts, incluidas cuatro del validador de demo. Integración real en proyecto aislado shareverance-predictor-demo: load creó cero gastos sobre 192 existentes; check-forecast verificó 4/5/6, equivalencia h=1 mensual, origen recomendado, insuficiencia 2023-03/2022-12, categorías, 400/403 y ausencia de cambios en gastos/cobertura. Caída controlada de Python: 503 y gastos disponibles, recuperación tras reinicio. Capturas reales de seis meses, insuficiencia y fallo; no certifican matriz responsiva completa ni aceptación humana. Después de reiniciar Python se repitió check-forecast correctamente y se detuvo la demo conservando volúmenes. No se evaluó 2025 ni se cargaron objetivos como historial. El gráfico histórico/previsión y revisión humana siguen pendientes; no se marca P5-H ni otras tareas completadas.


P5-H, [#81](https://github.com/seba615/shareverance/issues/81): integración de previsiones mensuales multihorizonte (horizontes seleccionables de 4, 5 o 6 meses desde un origen común con cobertura confirmada). Se implementaron los contratos internos y endpoints aditivos: POST /internal/v2/predictions/forecast en Python (con token Bearer interno y lectura de vistas SQL acotada a la organización), GET /api/v1/organizations/{id}/predictions/forecast en Java (validación estricta de originMonth y horizonMonths 4|5|6, orquestando llamada a Python), y extensión de la UI React (PredictionPanel.tsx con alternancia entre 'Próximo mes' y 'Previsión multihorizonte', tabla de trayectoria multimes y advertencia de calidad NOT_EVALUATED). Se verificaron suites unitarias de scripts (54 pruebas aprobadas) y de servicio en Python (2 pruebas aprobadas). Sin promoción automática ni modificación de los contratos vigentes de monthly.


P4-H, [#79](https://github.com/seba615/shareverance/issues/79), base `fe25a9a`: comparación offline descriptiva de último mes, media histórica y SES 0.3 por h=1…6 y ventanas 4/5/6. [Comandos, evidencia y límites](evidencia/p4h/README.md). Misma cohorte completa de seis horizontes para todos los métodos y ventanas; exclusiones explícitas, fracciones exactas, MAE con igual peso por serie y WAPE agrupado. Se informa frecuencia/magnitud del deterioro contra la mejor referencia por serie; sin umbrales inventados, promoción, congelación retroactiva ni reapertura de 2025. 50 organizaciones y 400 categorías elegibles, cero exclusiones; último mes tiene menor MAE agregado en ambos niveles y todas las ventanas, sin afirmar superioridad en cada serie. Wrapper `compare` ejecutado y JSON parseado; 43 pruebas del wrapper en Docker aprobadas, incluidas ocho nuevas. Intento Python global falló por falta de pydantic; alternativa uv no ejecutada. No se iniciaron servicios integrados ni se alteraron bases, contratos o modelos. P4-H aporta evidencia de desarrollo; evaluación final, criterios por horizonte, aceptación y cierre siguen pendientes y manuales.

P5, [#77](https://github.com/seba615/shareverance/issues/77), corrección sobre `b94dc4f`: [evidencia, límites y comandos](evidencia/p5/README.md). Se mantiene mock predeterminado y SES 0.3 experimental, sin promoción ni cierre de aceptación predictiva. P2 conserva umbrales propuestos; P2-H no tiene umbrales por horizonte aceptados. Se distinguen conteos de desarrollo contra la mejor referencia (50/50 y 313/400) de los históricos exploratorios de 2025 contra último mes (50/50 y 316/400). P3-H describe MAE agregado, sin inventar un criterio de aceptación. No se acredita integración real por suites unitarias ni se repite reserva. Guía start/load/check/stop y evaluación offline enlazadas; sin cambios de modelos, contratos o migraciones. Aceptación y cierre manuales.

Comprobaciones de la corrección P5, 23/10/2026: dependencias frontend instaladas con npm ci desde el lock, 40 pruebas React aprobadas sin omisiones y build de producción correcto. Se comprobaron 63 enlaces relativos a archivos y git diff --check. No se repitieron suites Java/Python, pruebas con PostgreSQL real ni evaluación de 2025; no se iniciaron servicios. origin/main consultado en 32f17ec, incluido en la rama, y único PR abierto #78. Issue #77 conserva asignación a Ale2611; SQL local ajeno preservado.

P3-H, [#75](https://github.com/seba615/shareverance/issues/75), base 5775390: evaluador multihorizonte offline de desarrollo h=1..6 desde origen común. [Evidencia y resultados](evidencia/p3h/README.md). Se evaluaron 7 orígenes comunes de desarrollo (2023-12 a 2024-06) con objetivos enteros en 2024 (18.900 puntos compartidos, 50 organizaciones y 400 categorías completas). En todos los horizontes h=1..6, last_month supera a SES y media histórica; el error MAE de last_month crece monotónicamente con la distancia temporal proyectada (desde ARS 104.353,98 en h=1 hasta ARS 513.548,48 en h=6 en organizaciones, y de ARS 24.267,09 a ARS 65.909,27 en categorías). Aislamiento temporal probado sin fuga de información futura; modelos univariados sin tendencia generan trayectorias planas por horizonte. Se incorporan scripts/evaluate_forecast_csv.py, acción orecast en predictor-demo.ps1 y seis regresiones nuevas (46 pruebas unitarias aprobadas). Sin cambios de contrato HTTP ni servicios iniciados; aceptación manual.

P2-H, [#73](https://github.com/seba615/shareverance/issues/73), propuesta documental sobre `4ef1e57`: [semántica y DTO coordinados](contratos_api.md#propuesta-multihorizonte), [protocolo y ejecución vigente](python_predictor.md#protocolo-multihorizonte). Horizontes 4/5/6 desde un origen histórico común; default propuesto 4 y calidad/timeout/umbrales por horizonte pendientes de revisión Python/Java/front/QA. Conserva monthly y plantea operación forecast aditiva, no disponible todavía. Validación propuesta con siete orígenes comunes de desarrollo, sin reales futuros entre pasos. P4 no promueve modelo ni acredita reserva intacta: P5 requiere decisión, y P3-H puede preparar cálculo offline tras revisar P2-H. Sin código, migraciones, rutas nuevas ni servicios iniciados en esta tarea; aceptación y estados manuales. Cada implementación debe mantener instrucciones de start/load/check/stop y sus comandos nuevos.

P4, [#71](https://github.com/seba615/shareverance/issues/71), corrección sobre `f4d2a4e`: [evidencia y comandos](evidencia/p4/README.md). Se rectifica la evaluación de 2025 como histórica exploratoria, sin acreditar aceptación previa de P2, manifiesto/configuración congelados ni unicidad. La reserva ya fue consultada; no se presenta como intacta ni se selecciona un método operativo a partir de sus resultados. Métricas originales conservadas y contextualizadas, sin repetir reserva real. Se agrega barrido reproducible 2024 de siete alphas y deterioro exacto contra la mejor de ambas referencias por serie; alpha 0.7 sí mejora MAE de categorías, alpha 1 reproduce último mes. El candidato histórico 0.3 no se declara óptimo. AllowReserve exige doce meses de 2025 y series completas sin rangos mixtos, pero sólo habilita exploración con final_evaluation_accredited:false. Veintinueve regresiones en Docker aprobadas (8 cargador, 8 auditoría, 13 evaluador/barrido); grid real ejecutado sólo en desarrollo y JSON parseado. Sin servicios iniciados, migraciones, cambios HTTP o modelos promovidos; P5/P2-H/P3-H y aceptación manual pendientes.

P3 inicial, [#69](https://github.com/seba615/shareverance/issues/69), base `35b7ff2`: evaluador CSV offline de desarrollo h=1, con rango explícito limitado a 2023–2024 y reserva 2025 bloqueada. [Comandos y formato](python_predictor.md#evaluador-p3): `scripts/predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12` y `test`, sin levantar base. Reutiliza predictores existentes, reporta puntos/errores/métricas por serie/nivel, insuficiencia y objetivos ausentes; no acepta cobertura real ni promueve modelos. P2 conserva aceptación manual y P2-H/P3-H permanecen pendientes.

Ejecución de desarrollo: 50 organizaciones y 400 categorías completas, cero excluidas y 5.400 puntos. Resultado y límites en Python; reserva no evaluada. Primer CLI falló por ruta de importación `app`, corregida con PYTHONPATH=/app; se separó stdout de construcción del JSON y se repitió correctamente. Se inició Docker Desktop para usar dependencias existentes, sin iniciar entornos Shareverance ni alterar bases. La auditoría P1 también queda incluida en las pruebas en contenedor. Sin cambios de contratos/migraciones/dependencias. Informe local ignorado en backend-java/target/p3; hash del evaluador permite identificar código local sin commit. Evidencia de pruebas final se registra al entregar; no se declara aceptación integral.

Verificación final P3 inicial, 22/10/2026: `predictor-demo.ps1 test` aprobó 24 regresiones en Docker (ocho cargador, ocho auditoría, ocho evaluador), incluidos fuga temporal, seis meses, huecos, objetivos ausentes, reserva bloqueada, ceros y agregación exacta. El wrapper real rechazó objetivos 2025 con fallo; audit produjo JSON válido de 14.400 filas en Docker, resolviendo la limitación previa de P1. Informe de desarrollo 2024 escrito y parseado correctamente; 40 enlaces locales, parser PowerShell y formato comprobados. Sin nueva suite integral Java/React ni aceptación de calidad. Tarea #69 asignada a chelofabian y vinculada al proyecto 3, sin estados cambiados.

Publicación P3: [capturas y resumen](evidencia/p3/README.md). Se repitieron 24 regresiones, evaluación de desarrollo y rechazo de reserva; se revisó visualmente el resumen estático del JSON, identificado como evidencia offline y no pantalla del producto. Se inició sólo la demo para comprobar sesión ADMIN, organización CSV y predicción mock existente; check integrado correcto. Captura de interfaz en ancho estrecho, sin certificar responsividad completa. Servidor temporal de evidencia y demo detenidos al finalizar, conservando volúmenes. Sin secretos, datos reales o cambios de UI; no se aceptan ni promueven modelos.

Corrección de procedencia V1, 22/10/2026, #67: [ficha del dataset](../demo/README_DEMO.md#procedencia-y-validez-del-dataset-v1). La fuente directa es generación sintética, sin calibración oficial aplicada; se rectifica la atribución anterior a canastas públicas. El universo declarado es 50 hogares ficticios × 36 meses × ocho categorías, sin representatividad real. Supuestos declarados: base ilustrativa, tendencia nominal 4,5 % mensual, variación aleatoria y semilla; faltan generador, fórmula/semilla exactas y trazabilidad entre paquete original e IDs locales. No hay evidencia disponible de uso de 2025 para selección/ajuste; no constituye garantía de reserva intacta. Propuesta 2023 entrenamiento, 2024 validación/selección y 2025 final compatible con desarrollo 2023–2024 de P2, sin cambiar sus reglas automáticamente. P3 puede preparar evaluación experimental; V1 no permite aceptar precisión sobre hogares reales. El manifiesto registra procedencia declarada, límites y elegibilidad final pendiente. Ocho pruebas del auditor repetidas correctamente; no se ejecutaron pronósticos ni servicios.

P1, auditoría estructural inicial del 22/10/2026, [#67](https://github.com/seba615/shareverance/issues/67), base `e48b5de`: implementado auditor offline sin métricas predictivas ni carga SQL; [comandos y límites](python_predictor.md#auditoria-p1). Desde la raíz: `scripts/predictor-demo.ps1 audit` y `test`, sin iniciar servicios. Alternativa Python local documentada. La revisión de últimos cambios confirma PR #64 (P1-D) y #66 (propuesta P2) integrados, sin PR abiertos al comenzar. P2 mantiene aceptación manual y dependencias P1; el merge no acepta automáticamente umbrales ni calidad.

Comprobado con Python local 3.14: ocho regresiones del auditor y ocho del cargador aprobadas, manifiesto real con 14.400 filas, 50 organizaciones, 400 categorías y 36 meses completos; ocho categorías por organización, sin importes cero ni totales de desarrollo idénticos/exactamente proporcionales. No acredita independencia o cobertura real. No se calcularon pronósticos/errores de 2025. Docker Desktop no estaba iniciado: el comando Docker falló al conectar y su nueva rama audit queda sin verificar en contenedor. No se iniciaron entornos ni se alteraron bases. P1 no queda aceptado: falta evidencia de fuente/transformación, cobertura/universo y uso previo de reserva; la herramienta lo declara expresamente con elegibilidad final null. Sin estados de tablero cambiados ni cierre de tareas.

P1-D, entrega inicial de [#63](https://github.com/seba615/shareverance/issues/63): [procedimiento ejecutable del entorno integrado](python_predictor.md#entorno-demo-predictor). En cada tarea posterior del predictor incluir esta referencia y sus comandos adicionales de ejecución; no asumir que un entorno levantado por otro integrante está actualizado. Comandos desde la raíz: `scripts/predictor-demo.ps1 start`, `load`, `check`, `status`, `test` y `stop`. Implementación inicial limitada a organización externa 1 y corte 2024-12; la ampliación a 50 organizaciones y otros cortes permanece pendiente, sin cambiar el estado de aceptación del plan.

Evidencia visual P1-D del 21/10/2026: login con ADMIN habitual, selección CSV, historial general (192 gastos) y filtrado diciembre 2024 (ocho gastos, ARS 2.114.866,85), panel 2023–2024 con categorías y cobertura, predicción mock enero 2025 con 24 meses y aviso explícito, insuficiencia para abril 2023 y recuperación con Mes recomendado. Ocho regresiones y `check` integrado repetidos correctamente. Primera revisión en ancho estrecho; repetida con la resolución ampliada por el usuario para publicar [cuatro capturas legibles](evidencia/p1d/README.md) junto con esta entrega. Ocho regresiones y check integrado repetidos correctamente antes de publicar; guía revisada para acceso ADMIN habitual, comandos y límites actuales. Sin certificación responsiva integral ni ejecución de SES/OCR/multihorizonte. Tablas y gráficos anchos mantienen desplazamiento; las capturas no muestran contenido fuera de ese desplazamiento.

Implementado en `feat/p1d-entorno-demo-predictor`, base `2b798d0`: wrapper PowerShell con proyecto/puerto/credenciales sintéticas propios; importación HTTP Java con selección CSV validada, mapeo de IDs, gastos/partes individuales y cobertura. Repetición reconoce datos propios y rechaza divergencias sin sobrescribirlos; la carga completa no es una única transacción. `check` concilia gasto/parte, cobertura, analítica Java→Python→vistas y predicción mock, además del rechazo 403 para usuario sin pertenencia. No incorpora migraciones, dependencias nuevas ni cambios HTTP; no carga ni evalúa 2025 ni implementa horizontes multimes.

Comprobaciones del 21/10/2026, exclusivamente en proyecto `shareverance-predictor-demo`: ocho regresiones unitarias aprobadas (selección/corte, duplicados, huecos, importes, nombres y conciliación), carga inicial de 192 gastos/192 partes y ocho categorías, 24 meses confirmados, ARS 31.211.212,92. Segunda carga creó cero gastos. PostgreSQL 17.11 real confirmó conteos/sumas coincidentes y cero gastos desde 2025. La API analítica concilió los 24 meses y categorías; predicción mock de 2025-01: 211486685 centavos. Usuario ajeno: 403. `stop` y nuevo `start` conservaron datos y `check` volvió a pasar. Ambos frontends 5175/5176 respondieron 200; no se modificaron datos del entorno humano. El primer arranque detectó variables vacías heredadas de PowerShell que eclipsaban el env-file; se corrigió el aislamiento y se repitió el procedimiento correctamente. No se ejecutó una nueva suite integral de aceptación ni se certificó navegación humana. Sin estados cambiados ni tareas cerradas.

Seguimiento documental: [#59](https://github.com/seba615/shareverance/issues/59), relacionado con [#53](https://github.com/seba615/shareverance/issues/53). Este desglose permite coordinar trabajo posterior a la implementación inicial de E6.1; no reabre ni cambia estados de tareas anteriores. La aceptación humana del producto puede continuar en paralelo. La aceptación de precisión predictiva A12 requiere su propia evidencia.

Base observada: existen mock, candidato SES con alpha 0.3, referencias de último mes/media histórica y evaluación progresiva para una lista JSON. No existe todavía un evaluador directo del CSV con partición de desarrollo/evaluación final ni un criterio cuantitativo de promoción. El procedimiento ejecutable actual y el formato de los informes se mantienen en [Python: evaluación con CSV](python_predictor.md#evaluación-con-el-csv-de-demostración).

| ID | Entregable y criterio de revisión | Dependencias | Funciones participantes | Estado |
| --- | --- | --- | --- | --- |
| P1 | Auditar CSV: hash, procedencia, unidades, continuidad, duplicados, cobertura y relación entre organizaciones adaptadas. Declarar qué series son aptas y qué demuestra el conjunto. | CSV y documentación de origen | Datos/Python | Pendiente; inspección inicial registrada abajo |
| P2 | Proponer protocolo h=1: desarrollo 2023–2024/reserva 2025, seis meses mínimos, MAE por nivel con peso igual por serie, mejora del 5 % y frecuencia de degradación máxima del 20 %; casos cero, cobertura y selección operativa definidos en `docs/python_predictor.md`. | P1 | Python/producto/QA | Propuesta documentada; aceptación manual y P1 pendientes |
| P3 | Implementar evaluador CSV reproducible por organización y categoría, sin cargar base: validación estricta, corte temporal explícito, hash/configuración, predicción y error por objetivo, resúmenes comparables y pruebas contra fuga temporal. | P1, protocolo P2 acordado | Python | Pendiente |
| P4 | Comparar último mes, media histórica y SES; evaluar alpha en desarrollo, congelar método/configuración y ejecutar evaluación de reserva 2025. Evidencia registrada en docs/evidencia/p4/README.md concluye que SES no supera a last_month bajo el protocolo P2. | P2, P3 | Python/datos/QA | Evidencia de reserva completada (P4) |
| P5 | Decidir método operativo y documentar evidencia frente al umbral. Integrar configuración y mensajes si corresponde; validar Java→Python→vistas SQL, aislamiento, insuficiencia y frontend. Mantener referencia si no hay mejora demostrada. | P4 y decisión de aceptación | Python/Java/front/QA | Decisión conservadora documentada; mock predeterminado, SES experimental; integración y aceptación pendientes |

<a id="predictor-multihorizonte"></a>

### Ampliación para revisión del equipo: demostración integrada y horizonte de 4–6 meses

Requisito de la ampliación: ofrecer previsiones mensuales para horizontes seleccionables de **4, 5 o 6 meses**, con seis como máximo. Cada ejecución parte del mismo último mes cerrado con cobertura confirmada y devuelve los meses futuros consecutivos, desde h=1 hasta el horizonte elegido. La capacidad actual de un mes se conserva durante la transición; no satisface por sí sola esta ampliación. Esto es planificación, no una funcionalidad disponible ni una aceptación de calidad.

| ID | Entregable y criterio de revisión | Dependencias | Funciones participantes | Estado |
| --- | --- | --- | --- | --- |
| P1-D | Preparar entorno demostrativo aislado y carga repetible del CSV: empezar por un usuario ADMIN y una organización, luego ampliar a 50; membresías, categorías, gastos, partes y cobertura conciliados. Documentar inicio, acceso, corte de carga y repetición sin duplicados. | P1, revisión del plan | Datos/Java/QA | Pendiente |
| P2-H | Definir protocolo multihorizonte y contrato coordinado: origen común, horizontes 4/5/6, insuficiencia por serie, límites temporales y compatibilidad con consumidores actuales. Acordar métricas y umbrales por h=1…6 antes de evaluación final. | P2, revisión Python/Java/front | Python/Java/front/producto | Pendiente |
| P3-H | Extender evaluador y candidatos para predecir hasta seis meses desde cada origen sin utilizar valores futuros reales ni marcarlos como cobertura confirmada. Evaluador multihorizonte y evidencia de desarrollo 2024 registrados en docs/evidencia/p3h/README.md. | P3, P2-H | Python/QA | Implementado y evaluado en desarrollo (P3-H) |
| P4-H | Comparar métodos en ventanas de 4/5/6 meses y reportar MAE/WAPE y muestras por horizonte. Congelar parámetros antes de reserva; evaluar estabilidad por serie y evitar conclusiones de independencia con ventanas solapadas. | P3-H, P4 | Python/datos/QA | Pendiente |
| P5-H | Integrar previsiones multimes y gráfico accesible: histórico frente a previsión, selector 4/5/6, tabla equivalente, método y origen, calidad por horizonte y avisos basados en evidencia. Verificar circuito real con usuario de P1-D, permisos, insuficiencia y fallos. | P1-D, P4-H, contrato P2-H y decisión P5 | Python/Java/front/QA | Pendiente |

Estas tareas amplían P1–P5; no requieren un segundo roadmap. P1-D puede avanzar en paralelo con el evaluador offline tras validar el plan. P2-H precede cualquier cambio de API; P3-H/P4-H preceden la selección e integración multihorizonte. La implementación queda pendiente de revisión del plan y coordinación de tareas del equipo, sin modificar el entorno humano existente.

**Demostración integrada (P1-D).** Usar proyecto Compose, base y volúmenes propios, sin borrar datos del entorno habitual. Identificar las organizaciones como demostración y mantener un mapeo de IDs externos CSV a IDs internos; no forzar claves ni agregar columnas de descripción sin revisar el esquema. Usar campos existentes para nombres/descripciones y un manifiesto externo de carga para procedencia/correspondencias. El usuario tendrá credenciales sólo de desarrollo y pertenencia ADMIN verificable; no registrar contraseñas reales en Git. Un segundo usuario sin pertenencia permite comprobar denegación entre organizaciones.

Cada fila mensual por categoría se representa como un gasto sintético fechado al último día del mes, con descripción de total mensual de demostración, categoría, titular/autor y parte individual exacta. Cargar mediante operaciones Java autorizadas; cualquier herramienta de carga directa alternativa exige diseñar y probar explícitamente integridad y aislamiento, no saltarse silenciosamente las reglas de negocio. Declarar cobertura mediante Java sólo para meses completos incluidos en el corte. La repetición debe reconocer registros propios y no duplicarlos ni sobreescribir gastos manuales. Verificar conteos, sumas por organización/mes/categoría, partes, pertenencia y cobertura. No inventar comprobantes ni presentar el calendario diario resultante como distribución real del gasto.

Para probar precisión desde diciembre de 2024, cargar sólo hasta ese mes y conservar los reales de 2025 fuera de las vistas de predicción. Una ejecución de seis meses compara enero–junio de 2025 desde ese único origen, sin cargar enero real antes de estimar febrero de esa misma ejecución. Otro escenario puede avanzar el corte con reales ya transcurridos para una **nueva** previsión desde otro origen. La demostración con los 36 meses completos permite probar pantallas, pero no medir precisión futura sin valores observados posteriores.

**Evaluación y presentación (P2-H a P5-H).** En cada origen, entrenamiento sólo con meses anteriores o iguales al corte. Una estrategia recursiva puede reutilizar estimaciones como entrada interna identificada, nunca como observaciones reales; SES sin tendencia produce previsiones planas para varios horizontes, por lo que ampliar la cantidad de meses no garantiza modelar tendencia/inflación. Evaluar si SES, referencias u otro candidato justificado cumplen el objetivo sin anticipar un ganador. Para la propuesta de reserva 2025, usar sólo objetivos dentro de esa reserva; comparar horizontes sobre orígenes comunes completos (por ejemplo, diciembre de 2024 a junio de 2025 para h=1…6) y declarar fechas/muestras. No sumar errores de todas las ventanas como si fueran observaciones independientes.

El gráfico debe advertir que la incertidumbre puede aumentar con la distancia, sin afirmar una caída monótona de precisión ni porcentajes de confianza inventados. Mostrar error histórico por horizonte y número de muestras cuando exista evidencia; sin ella, indicar «precisión no evaluada para este horizonte». Si se proponen bandas de predicción, definir su método y verificar cobertura empírica antes de publicarlas como intervalos calibrados. No confundir error promedio histórico con garantía de un resultado futuro. Los avisos acompañan la estimación; no reemplazan la evaluación ni el criterio de aceptación.

Actualización documental #59: se incorpora esta ampliación como pendiente de revisión del equipo. Sin carga en base, creación de usuarios, cambios de contrato, modelo o gráfico en esta entrega.

P1/P3 pueden usar datos de demostración sin tocar el entorno humano. Java/front pueden revisar mensajes y pruebas existentes en paralelo, sin anticipar selección ni modificar contratos compartidos. Cada implementación debe consultar tareas/ramas/PR antes de empezar; el equipo asignará personas y creará o reutilizará seguimientos concretos. #59 registra este plan y guía, no acredita ejecución de P1–P5 ni reserva exclusiva del predictor.

Propuesta P2 revisada (22/10/2026): [protocolo de evaluación](python_predictor.md#protocolo-formal-de-evaluación-y-reglas-cuantitativas-p2), para un mes por origen. Propone mejora del 5 % en MAE y un máximo del 20 % de series degradadas, comprobados por separado en ambos niveles; define media de MAE con peso igual por serie, doce objetivos comunes de reserva, exclusiones previas, casos MAE cero y selección operativa congelada en desarrollo. El 20 % mide frecuencia, no magnitud: se reporta el deterioro y cualquier tope adicional requiere acuerdo previo a la reserva. P1, aceptación manual del protocolo y evaluación P3–P5 siguen pendientes; los porcentajes no tienen validación empírica en esta entrega. Mantener ARS nominales y declarar relaciones entre organizaciones sintéticas; no inferir independencia ni precisión real. P2-H sigue pendiente para 4–6 meses.

Inspección documental del 20/10/2026: CSV versionado con 14.400 filas, 50 organizaciones, 400 categorías y 36 meses entre 2023-01 y 2025-12; sin claves duplicadas organización/mes/categoría, moneda ARS y sin importes cero observados. La ausencia de ceros no prueba capacidad ante ceros ni cobertura real: conservar casos sintéticos de cero, huecos e insuficiencia. La procedencia publicada describe canastas de consumo adaptadas y organizaciones inventadas; falta verificar trazabilidad de la adaptación. La exploración de este conjunto no equivale a aceptación con gastos representativos de usuarios. No se cambian estados de E6/E7 ni se activa SES.

Comprobación de la guía #59, 20/10/2026, base `43c1472`: ejecutado el bloque PowerShell de `python_predictor.md`, construcción con Dockerfile/lock existentes y evaluación en contenedor sin red. CSV SHA-256 `99f1edca57202fc182dd74b469a832607a15c9e1d3a83f4ece3b6f6a69001996`; entrada JSON derivada `bef4fc3b271de8b782524fc17b17adfa17fd56a25b569ecf2ec4bb637b36e730`. Organización 1, total mensual, 24 meses de desarrollo, 18 objetivos: MAE SES 189.134,60 ARS, último mes 102.926,39 ARS, media histórica 414.275,29 ARS; WAPE respectivamente 13,02 %, 7,09 % y 28,52 %. `accepted:null`. No se evaluó 2025 ni se ajustaron parámetros. Resultado limitado a esa serie de desarrollo; no acredita superioridad general. Informe local en `backend-java/target/predictor-exploracion/informe-organizacion-1.json`, no versionado. Sin cambios de código, contratos, base o servicios humanos; no se ejecutaron suites de aplicación por esta entrega documental. #59 creado/asignado a `chelofabian` y vinculado al proyecto 3, sin cambiar estados.


</details>

## Parámetros que faltan precisar

El desarrollo complementario del predictor se desglosa en [Plan complementario del predictor](#plan-complementario-del-predictor), relacionado con E6.1/A12. No sustituye la aceptación humana de E7.1.

<details>
<summary>Registro original de parámetros y propuestas por etapa</summary>

Parte de estos parámetros ya se concretó en código (OCR, vistas, autenticación y gráficos). Los pendientes actuales se distinguen arriba y en los documentos de componente.

| Tema | Propuesta / condición de cierre |
| --- | --- |
| Calendario | Mes, total por día y detalle; confirmar interacciones, sin recurrencias |
| OCR | Elegir motor con boletas representativas; fijar límite de píxeles y tiempos |
| Descripción OCR | Precisar texto no utilizable y tratamiento de varias líneas; fallback Varios |
| Temporales | Definir vencimiento de archivos sin gasto; sin purga de base de negocio |
| Vistas SQL | E3.1: cobertura explícita por organización/mes cerrado y contexto SQL local a la transacción; consumidor y transición en E3.2 |
| Servicio interno | Fijar esquema de autenticación Java→Python y rotación de secreto |
| Gráficos | E5.2: SVG nativo con React, sin biblioteca adicional, con tablas accesibles equivalentes |
| Evaluación | E6.1 incorpora evaluación offline SES/último mes/media histórica; dataset temporal, métrica de decisión, umbral y selección para aceptación siguen pendientes |
| Operación | Tiempos/reintentos definitivos y retención de logs; E7.1 incorpora procedimiento y pruebas aisladas de respaldo/restauración; aceptación operativa pendiente |
| Cuenta | Explicitar inclusión/exclusión de recuperación de contraseña y verificación de email |


</details>

## Criterios integrales de aceptación

| ID | Comprobación | Resultado requerido |
| --- | --- | --- |
| A01 | Registro, login, recarga y logout | Sesión consistente; contraseña no expuesta; sesión revocada tras logout. |
| A02 | Usuario miembro de A cambia IDs para acceder a B | Lectura y escritura rechazadas en gastos, miembros, categorías, comprobantes y analítica; también mediante el circuito Python. |
| A03 | USER carga a su nombre / intenta otro titular | Primera aceptada; segunda rechazada. ADMIN puede elegir miembro válido del mismo tenant. |
| A04 | Crear gasto con reparto | Suma exacta de partes = total; sin miembros duplicados o ajenos; regla de centavos reproducible. |
| A05 | Error durante guardado de partes | Rollback completo: no queda gasto incompleto. Verificar con PostgreSQL. |
| A06 | Importes límite, cero, negativos y más de dos decimales | Reglas uniformes en alta y edición, sin redondeo silencioso . |
| A07 | Editar y anular | Permisos correctos; no cambia composición histórica sin acción autorizada; anulado excluido de métricas. |
| A08 | Historial filtrado y paginado | Sin omisiones/duplicados al navegar; detalle y totales coherentes con mismos filtros. |
| A09 | Mes sin gasto, categoría nueva, sin datos y más de 60 meses | Distinguir ausencia de cobertura de cero; contrato válido después del recorte. |
| A10 | Comparación conocida y base anterior cero | Fórmulas del contrato; porcentaje null cuando corresponde. |
| A11 | Predictor con menos de seis meses | Insuficiencia; estimación null; UI explica la limitación. |
| A12 | Modelo real | Evaluación temporal reproducible y comparación con referencia; no presentar mock como resultado real. |
| A13 | Python caído o lento | Error controlado y tiempo acotado; se puede seguir cargando/consultando gastos. |
| A14 | Comprobante válido y archivo inválido | Carga válida autorizada; rechazos por formato/tamaño/contenido/ruta; otro tenant no puede descargar. |
| A15 | OCR acertado, parcial y fallido | Usuario revisa antes de guardar; carga manual siempre disponible; no se duplica gasto. |
| A16 | Reinicio de contenedores | PostgreSQL y comprobantes conservados; sesiones humanas según política; renovación de tokens técnicos. |
| A17 | Dos ediciones simultáneas | Versión optimista: conflicto 409 visible; nunca gasto/partes incoherentes. |
| A18 | Demo en móvil y escritorio | Todas las vistas incluidas utilizables, con estados de vacío/error/carga y valores visibles sin depender solo del gráfico. |
| A19 | Copia y restauración | Recuperar base y archivos coherentes mediante procedimiento probado. |


A20 adicional: calendario concilia totales diarios/mensuales con historial, respeta organización/estado y abre el gasto correcto. Los 19 criterios anteriores se conservan y se complementan para cubrir calendario.

## Evidencia histórica por tarea

Las secciones siguientes registran implementaciones y ejecuciones en sus fechas/bases originales. Sus pendientes y defaults no reemplazan la síntesis vigente; no se repitieron pruebas funcionales en la consolidación #91.

## Evidencia y cierre

Por tarea registrar commit/PR, archivos modificados, comprobación ejecutada, resultado, fecha y límites. Pendiente no cambia a verificado si la prueba no se ejecutó. El MVP termina al completar criterios y parámetros necesarios, no por existir endpoints.

## Trazabilidad de definiciones

D01 documentación → README/alcance; D02–D04 funciones/vistas/ARS → alcance/front; D05–D10 miembros/categorías/reparto/edición/historial → alcance/java; D11–D14 OCR/archivos → python_ocr/java; D15–D17 cobertura/panel/modelo → predictor/front; D18 participación → alcance/postgre; D19 vistas → postgre/predictor; D20–D21 contratos/tiempos → contratos_api; D22–D27 seguridad/estados/operación/logs → java/postgre/alcance; D28 aceptación → este documento; D29 concurrencia → java/postgre. Las definiciones concretas se mantienen en su archivo principal.

## Consolidación documental

REGLAS_DE_NEGOCIO.md se sustituye por alcance_mvp.md. ROAD_MAP_PYTHON.md se sustituye por este roadmap y python_predictor.md. CONTRATO_PYTHON_V1.md se conserva como referencia compatible dentro de contratos_api.md. GUIA_DESARROLLO.md se integra en README. ENTORNO_INICIAL.md se sustituye por instrucciones operativas y especificaciones vigentes; sus copias históricas de código no son instrucciones de configuración actual. El historial Git conserva versiones anteriores.

## Seguimiento de tareas e integración

Consolidación documental del 21/10/2026, [#61](https://github.com/seba615/shareverance/issues/61), base `9ea8ce4`: corregidos resúmenes obsoletos sobre vistas/callback, OCR, calendario, analítica, archivos privados y vista predictiva en README, alcance y documentos por componente. Se aclaran DECIMAL/BigDecimal en negocio y centavos enteros en vistas/contratos. La tabla inicial conserva estados de seguimiento y la evidencia histórica conserva su contexto temporal; las menciones de pendientes en entregas anteriores no sustituyen la síntesis vigente. Continúan pendientes aceptación integral, evaluación predictiva, carga CSV repetible y ampliación multihorizonte. La tarea documental está asignada a `chelofabian` y vinculada al proyecto 3, sin cambiar estados ni cerrar tareas. Revisión estática contra código/migraciones/configuración, enlaces locales y `git diff --check` comprobados; no se ejecutaron nuevas suites de aplicación ni reinicios, y no se alteraron datos o servicios.

Registrar cada tarea concreta en el [proyecto GitHub](https://github.com/users/seba615/projects/3), con título y descripción en español, reutilizando issues equivalentes y asignando a la cuenta autenticada. El agente solo crea/vincula y asigna; estados, finalización y cierre se administran manualmente. La publicación automatizada se limita a commit, push de rama de trabajo y creación de PR; todos los merges son manuales. Evitar referencias que cierren issues automáticamente. Procedimiento y límites en [AGENTS.md](../AGENTS.md).

Actualización documental 17/10/2026: incorporadas reglas de registro en tablero, idioma, identidad autenticada, cierre manual y prohibición de merges automáticos. Verificación: lectura de reglas y git diff --check; sin prueba de escritura en el proyecto ni publicación de rama.

Publicación documental 17/10/2026: preparación en rama docs/mvp1-especificaciones; enlaces locales y bloques Markdown verificados, git diff --check correcto. No se ejecutaron pruebas de aplicación porque no se modifica código. La creación del issue asignado fue rechazada por GitHub con HTTP 403; no se creó tarea ni vínculo al proyecto. La integración y el cierre quedan a cargo de una persona.

Implementación E1.1 en rama `feat/e1-1-java-ars-estados-permisos`, base `b3e6e21e9630d1655e05b3d99d27e89df363be20`: autorización Java de rutas por usuario, membresía y organización activos; restricción de moneda a ARS; rechazo de organizaciones, cuentas, membresías y categorías inactivas en operaciones de gastos. Pruebas: `mvn -f backend-java/pom.xml clean test`, 31 ejecutadas sin fallos. Son pruebas unitarias; no se ejecutaron pruebas de integración con PostgreSQL ni aceptación integral. Sin commit ni PR. La tarea permanece pendiente de revisión y aceptación manual. No se pudo identificar la cuenta autenticada de GitHub ni crear/asignar/vincular un issue: GitHub MCP devolvió error de cabecera y la CLI instalada no tiene una sesión autenticada.

Aclaración E1.1, 18/10/2026: para la base local con organizaciones previas a la restricción ARS se acordó eliminar una sola vez y recrear toda la base; no se migran ni convierten importes históricos. La limpieza elimina todos los datos PostgreSQL de ese entorno, no solo las organizaciones con otra moneda. Es una excepción de actualización: los reinicios habituales siguen conservando los datos en el volumen. El README documenta la operación y su alcance destructivo. Esta decisión no modifica el estado de E1.1 ni acredita aceptación con PostgreSQL.

Ajuste de moneda E1.1, 18/10/2026; seguimiento en [#24](https://github.com/seba615/shareverance/issues/24): el formulario de creación ya no ofrece selector de moneda y el cliente envía `ARS` como valor fijo; el tipo de la organización y de la solicitud refleja la moneda única. Verificación: `docker compose -f compose.yaml -f compose.tools.yaml build frontend` completó `tsc --noEmit` y el build Vite correctamente; no se encontraron pruebas unitarias de frontend. No altera el estado de E1.1 ni acredita aceptación integral.

### Evidencia E1.2 — individual e iguales, 18/10/2026

Relacionado con [#27](https://github.com/seba615/shareverance/issues/27), asignado a la cuenta autenticada `chelofabian`. El alcance de esta entrega es individual e iguales; no incorpora CUSTOM al MVP, migración para esa modalidad ni recálculo proporcional. El issue conserva una descripción anterior más amplia; no se modificaron su estado ni asignación. Implementación en `codex/e1-2-individual-iguales`, desde `origin/main` `7733612`, sin depender de una rama de frontend ni de migraciones pendientes. El PR #26 está cerrado sin integración; su rama publicada se conserva y no es la base de esta entrega.

Implementado: validación exacta de importes y límites en alta y edición, reparto de centavos por ID ascendente, rechazo de IDs nulos/repetidos, partes cero y selección inválida, conservación de participantes históricos en edición y transacción gasto/partes. CUSTOM permanece como código preexistente sin aceptación dentro de esta tarea; su presencia no amplía el alcance del MVP. No hay migraciones, cambios de DTO/rutas ni pantallas nuevas.

Revisión documental E1.1: PR #25 integrado en `7733612` (implementación `67cff98`), relacionado con #24. Sus cambios incluyeron README y documentos, pero el registro inicial aún decía «sin commit ni PR»; esta evidencia actualiza esa referencia histórica sin alterar estados ni acreditar aceptación integral.

Verificado E1.2: suite `mvn --batch-mode --no-transfer-progress clean test`, ejecutada en Docker con Maven 3.9.11/Java 21 y `E12_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/shareverance_e12_equal_test`: 41 pruebas aprobadas, sin fallos, errores ni omisiones (35 unitarias y 6 con PostgreSQL 17.11 temporal). Casos: centavos por ID ante órdenes distintos, límite monetario y precisión, IDs inválidos, cuentas/membresías inactivas, rollback tras segunda inserción/actualización de parte y tras fallo del gasto final, edición individual, rechazo de importe insuficiente y conservación de miembros históricos inactivos sin incorporar un miembro nuevo. No se ejecutó aceptación HTTP/UI ni concurrencia E1.3. Base de desarrollo y sus volúmenes sin utilizar. `git diff --check` y enlaces Markdown locales correctos. Consulta remota final: origin/main sigue en 7733612, sin PR abiertos. La entrega se identifica mediante el commit de la rama `codex/e1-2-individual-iguales`; no se modificaron estados del tablero ni se cerró #27. Aceptación y cierre manuales.

### Evidencia E1.3 — versión optimista y último ADMIN, 18/10/2026

Implementación en rama `feat/e1-3-concurrencia-version-admin`, base `2119011` (`origin/main`). Relacionado con [#29](https://github.com/seba615/shareverance/issues/29), asignado a la cuenta autenticada `seba615` y vinculado al proyecto de GitHub #3. Sin PRs remotos abiertos en colisión.

Implementado:
- Migración Flyway V6 (`V6__version_optimista_gastos.sql`) agregando columna `version INT NOT NULL DEFAULT 1` a `expenses`.
- Campo `version` en `Expense` y `ExpenseUpdateRequest`.
- Actualización atómica y condicional en `ExpenseRepository` / `JdbcExpenseRepository` (`updateWithOptimisticLocking`).
- Validación en `ExpenseServiceImpl.updateExpense`: si la versión provista no coincide con la actual o falla la actualización optimista, se lanza `ResponseStatusException(HttpStatus.CONFLICT, ...)` devolviendo HTTP 409 Conflict sin aplicar cambios parciales.
- Protección del último ADMIN activo en `MembershipServiceImpl` (`updateMemberRole` y `deactivateMembership`): operaciones transaccionales con bloqueo de fila `FOR UPDATE` y validación de cantidad de administradores activos (`countActiveAdminsByOrganizationId`).

Verificado E1.3:
- Suite `mvn --batch-mode --no-transfer-progress clean test` en contenedor Maven 3.9.11/Java 21 contra PostgreSQL 17.11 aislado (`shareverance_e12_equal_test`): 45 pruebas aprobadas, 0 fallos, 0 errores, 0 skipped.
- Casos E1.3 validados: conflicto optimista 409 Conflict y preservación de datos ante colisión, incremento correlativo de versión, y rechazo de degradación o desactivación del último ADMIN tanto en mocks unitarios como con PostgreSQL real y Flyway V1-V6.
- Verificación de build de frontend con Vite/Docker Compose completado sin errores.

### Corrección E1.3 — 19/10/2026

Relacionado con [#29](https://github.com/seba615/shareverance/issues/29), que conserva su asignación a seba615. Cuenta autenticada para esta entrega: chelofabian. Base `02ff308` (PR #30 integrado); rama `codex/corregir-e1-3-concurrencia`. Se reutiliza el seguimiento existente sin reasignar, cambiar estados ni cerrar la tarea. Revisión previa: sin PR abiertos ni cambios posteriores al PR #30 en su rama remota.

Implementado: versión positiva obligatoria para edición (400 si falta/null/no positiva), sin alternativa de guardado sin control; actualización condicional del gasto antes de las partes y 409 para versiones desactualizadas; anulación atómica que sólo modifica estado y versión; bloqueo compartido de organización antes de membresía y conteo en desactivación/revocación de ADMIN. Se conserva V6 sin cambios y no se agregan migraciones. CUSTOM permanece fuera de esta entrega.

Contrato y consumidores: contratos_api.md detalla version de solicitud/respuesta, errores y transición desde el PUT sin versión. El frontend integrado no edita gastos y Python no consume esas rutas; DELETE mantiene su formato. Se conservan rutas y permisos existentes. Documentos afectados: README, alcance, Java, PostgreSQL, frontend y contratos.

Verificado: `mvn --batch-mode --no-transfer-progress clean test` en Docker, Maven 3.9.11/Java 21, PostgreSQL 17.11 temporal con `E12_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/shareverance_e12_equal_test`. Resultado: 54 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas; 37 unitarias y 17 con PostgreSQL (8 transaccionales existentes y 9 adicionales). Los casos nuevos verifican dos editores con versión idéntica (un éxito y un 409), anulación con lectura anterior a una edición ya confirmada, anulación anterior al UPDATE de edición (409 sin cambios parciales), anulación esperando el commit de edición, dos desactivaciones de ADMIN, dos degradaciones y combinación de ambas. Se comprobó la espera real mediante pg_blocking_pids en las carreras con bloqueo. Las pruebas HTTP standalone de controlador cubren versión omitida/null/no positiva (400), incremento de versión y versión desactualizada (409); no sustituyen aceptación HTTP con autenticación completa ni pruebas de UI.

### Evidencia E2.1 — miembros, categorías y carga manual de gastos, 19/10/2026

Relacionado con [#21](https://github.com/seba615/shareverance/issues/21), asignado a la cuenta autenticada `chelofabian` y vinculado al proyecto de GitHub #3 (item `PVTI_lAHOAlJuzs4BlIz_zhB21OQ`). Se reutiliza el seguimiento existente sin cerrar la tarea ni alterar estados del tablero. Implementación en rama `feat/e2-1-miembros-categorias-carga-gastos` a partir de `main` (`2c3bc23`).

Implementado:
- Backend Java:
  - Soporte de alta de miembro por email exacto de usuario existente (`MembershipRequest` con campo opcional `email`, validación en `MembershipServiceImpl.addMemberToOrganization` buscando cuenta activa y verificando no duplicidad en la organización).
  - Pruebas unitarias en `MembershipServiceTest` verificando incorporación por email y rechazo ante email inexistente.
  - Ajuste de idempotencia en fixtures de pruebas PostgreSQL (`ExpenseConcurrencyPostgresTest` y `ExpenseTransactionPostgresTest`) usando `ON CONFLICT (id) DO NOTHING` para permitir ejecuciones de suites sobre esquemas compartidos.
- Frontend React:
  - `frontend/src/api/java.ts`: interfaces y clientes de API para miembros (`membershipApi`), categorías (`categoryApi`) y gastos (`expenseApi`).
  - `frontend/src/App.tsx`: navegación de pestañas dentro de la organización activa (`Gastos`, `Miembros`, `Categorías`).
  - Formulario y listado de gastos: validación de fecha no futura, importe positivo en ARS (con hasta dos decimales), descripción de hasta 500 caracteres, selección de categoría activa, selección de titular (con restricción: solo ADMIN puede asignar otro titular miembro activo), modalidad de reparto (`INDIVIDUAL`, `ALL_MEMBERS`, `SPECIFIC_GROUP` con selector de miembros activos) asegurando al menos $0.01 por participante.
  - Administración de miembros: incorporación por email exacto, cambio de rol (ADMIN/USER con protección contra degradación) y desactivación (con confirmación y control de último ADMIN del lado servidor).
  - Administración de categorías: alta de categoría y desactivación de categorías existentes.
  - Estilos en `frontend/src/style.css` adaptables para cabeceras, pestañas, selectores de miembros, tablas y badges de rol y estado.

Verificado:
- Suite de backend Java: `mvn --batch-mode --no-transfer-progress package` ejecutado en contenedor Docker con Maven 3.9.11/Java 21: 55 pruebas aprobadas, 0 fallos, 0 errores (38 unitarias limpias y 17 PostgreSQL preparadas con fixtures idempotentes).
- Build de frontend: `docker compose -f compose.yaml -f compose.tools.yaml build frontend` completó `tsc --noEmit` y el build de Vite limpiamente sin errores de tipado ni advertencias de compilación.
- Verificación de formato y sintaxis con `git diff --check`.
- La entrega se identifica mediante el commit en la rama `feat/e2-1-miembros-categorias-carga-gastos`. Aceptación, merge y cierre de issue/tablero permanecen exclusivamente manuales conforme a `AGENTS.md`.

### Ajustes del PR #32 — selección de organización y contratos, 19/10/2026

Relacionado con #21 y PR #32. Rama publicada `feat/e2-1-miembros-categorias-carga-gastos`, base de estos ajustes `bed3405`. La cuenta autenticada es chelofabian; #21 conserva su asignación observada a seba615, sin cambios de estados ni reasignaciones. No se crea tarea duplicada ni se modifica historial publicado.

Implementado: invalidación inmediata de solicitudes al cambiar de organización, datos etiquetados por organización, descarte de datos/errores/loading tardíos y protección de recargas posteriores a una escritura de una selección anterior. Se reinician pestaña, formularios y reparto; cierre de sesión/desmontaje invalida las respuestas. Se preservan las funciones existentes de E2.1 y no se modifican rutas ni código Java. README, alcance, frontend, Java y contratos documentan las rutas de miembros/reactivación, campos de presentación, compatibilidad por userId, email y roles, y límites de la entrega.

Verificado: `npm test` en Node 24.21.0 dentro de Docker, siete pruebas aprobadas, cero fallos ni omisiones; casos de respuesta de A antes/después de B, errores tardíos, A→B→A, última recarga de la misma organización, logout/desmontaje y descarte de una recarga posterior a escritura en una selección antigua. Se ejecuta el cargador utilizado por App mediante promesas controladas, sin instalar dependencias de pruebas adicionales. `docker compose -f compose.yaml -f compose.tools.yaml build frontend` completó TypeScript y Vite correctamente. `git diff --check` y enlaces Markdown locales correctos. La evidencia de backend de la implementación anterior se mantiene separada: no se volvió a ejecutar Maven ni se probaron flujos completos en navegador en estos ajustes. No se alteraron base de datos ni migraciones. Aceptación e integración manuales.

### Implementación E2.2 — historial y operaciones de gastos, 19/10/2026

Base b424179 (PR #32 integrado); rama feat/e2-2-historial-gastos. Cuenta autenticada chelofabian. Revisión inicial sin PR abiertos ni issue equivalente específico E2.2; #21 corresponde a la entrega anterior y conserva su asignación. GitHub rechazó con HTTP 403 crear el issue de E2.2: no quedó creado, asignado ni vinculado al proyecto. La lectura pública del proyecto devolvió HTTP 404; no pudo verificarse su contenido. No se modificaron estados ni cerraron tareas.

Implementado: consulta paginada adicional, filtros inclusivos por fechas/categoría/estado, orden estable por fecha e ID, totales con los mismos filtros y snapshot por respuesta, detalle de partes, edición con categoría histórica y versión obligatoria/409 y anulación confirmada. El frontend conserva funciones previas y descarta respuestas de organizaciones y consultas anteriores. La carga de organización no descarga ya todos los gastos. Se validan descripción y fecha de Buenos Aires en Java. Se conservan rutas previas y contrato Python. No hay migraciones, calendario, OCR, analítica ni CUSTOM en esta entrega.

Verificado E2.2: `mvn --batch-mode --no-transfer-progress clean test` en Docker con Java 21/Maven 3.9.11 y PostgreSQL 17.11 temporal, URL `jdbc:postgresql://localhost:5432/shareverance_e12_equal_test_final`: 65 pruebas aprobadas, cero fallos, errores u omisiones; nueve nuevas en ExpenseHistoryPostgresTest (esquema aislado e22_history). Se verificaron orden y navegación sin duplicados con datos estables, total completo aun en página fuera de rango, filtros inclusivos, categorías inactivas, exclusión de otra organización, total sin duplicar por partes, estados vigente/anulado/todos, parámetros inválidos 400 y acceso sin sesión 401/otra organización 403. Solicitudes MockMvc con filtro de autenticación real verifican permisos de autor/ADMIN, versión obligatoria, 409 sin cambio de partes, detalle de anulados y conservación histórica. Se alinearon fechas de pruebas con Buenos Aires.

Frontend: siete pruebas del cargador aprobadas con `npm test` en Node 24.21.0; build Docker de frontend completó TypeScript/Vite. `git diff --check` y enlaces Markdown locales correctos. Se levantó un entorno temporal Java/React sobre datos sintéticos; la automatización del navegador integrado expiró al abrirlo, por lo que no se acredita recorrido visual ni aceptación de UI. No se modificaron base/volúmenes de desarrollo. Última consulta remota: main b424179 sin PR abiertos. La entrega se identifica mediante el commit de la rama `feat/e2-2-historial-gastos`. Limitaciones: paginación offset sin snapshot entre solicitudes; no se acredita rendimiento de producción. Aceptación y cierre manuales.

### Implementación E2.3 — calendario con detalle por fecha, 19/10/2026

Relacionado con [#34](https://github.com/seba615/shareverance/issues/34), creado en español, asignado a la cuenta autenticada `seba615` y vinculado al proyecto de GitHub #3 (`PVTI_lAHOAlJuzs4BlIz_zhB56fg`). No se modificaron estados del tablero ni se cerró la tarea. Implementación en rama `feat/e2-3-calendario-gastos` desde `origin/main` (`a7f9a65`), sin PR abiertos ni ramas remotas en conflicto.

Parámetros de calendario definidos para E2.3:
- Ubicación en UI: subvista alternable (`Historial` / `Calendario`) dentro de la pestaña `Gastos` de la organización activa.
- Contrato y granularidad: endpoint `GET /api/v1/expenses/organization/{organizationId}/calendar?month=YYYY-MM` con resumen mensual y totales diarios de gastos vigentes en huso `America/Argentina/Buenos_Aires` (sin recurrencias ni meses futuros), combinado con la consulta paginada existente (`/page` con `fromDate=toDate` y `state=ACTIVE`) al seleccionar una fecha y apertura de detalle con edición optimista y anulación.

Implementado:
- Backend Java: `ExpenseCalendarDayResponse`, `ExpenseCalendarResponse`, `ExpenseRepository.findCalendarDays` (`JdbcExpenseRepository` y `MockExpenseRepository`), `ExpenseServiceImpl.getExpenseCalendar` bajo `REPEATABLE_READ` de sólo lectura, endpoint `GET /api/v1/expenses/organization/{organizationId}/calendar` en `ExpenseController` y autorización de membresía activa en `AuthServiceImpl`. Sin migraciones Flyway nuevas (opera sobre V1–V6).
- Frontend React: cliente `expenseHistoryApi.calendar` en `frontend/src/api/java.ts`, modelo y utilidades de calendario en `frontend/src/calendarModel.ts`, vista `ExpenseCalendarView` en `frontend/src/ExpenseCalendar.tsx`, selector `Historial` / `Calendario` en `frontend/src/App.tsx` y estilos adaptables en `frontend/src/style.css`. Se descartan respuestas tardías al cambiar de organización, mes, fecha o vista, y las ediciones/anulaciones refrescan tanto el calendario mensual como el listado diario.

Verificado E2.3:
- Backend Java y PostgreSQL: `mvn test` en contenedor Docker (`maven:3.9-eclipse-temurin-21`) contra PostgreSQL 17.11 aislado (`shareverance_e12_equal_test`): 67 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas (incluyendo las nuevas pruebas unitarias en `ExpenseServiceTest` e integración JDBC/MockMvc en `ExpenseHistoryPostgresTest` para agregación diaria sin duplicar por partes, exclusión de anulados y de otras organizaciones, actualización tras cambio de fecha/importe, rechazo 400 de formato inválido o mes futuro y autorización 401/403/200).
- Frontend: `npm test` en Node 24.15.0 dentro de Docker con 11 pruebas aprobadas, 0 fallos (`organizationLoader.test.mjs` y `calendarModel.test.mjs`); build completo con `docker compose -f compose.yaml -f compose.tools.yaml build frontend` (`tsc --noEmit && vite build`) finalizado sin errores.
- No se utilizaron ni alteraron la base ni los volúmenes de desarrollo. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Ajustes del PR #35 — operaciones pendientes del calendario, 19/10/2026

Relacionado con #34 y PR #35. Base de los ajustes `40f8096`, rama publicada `feat/e2-3-calendario-gastos`. Cuenta autenticada chelofabian; se conserva la asignación de #34 a seba615 y no se cambian estados ni se cierra el seguimiento. La corrección se realiza en el PR existente.

Implementado: control común de operaciones de detalle/edición/anulación mediante `calendarOperations.ts`. Navegar de mes o fecha invalida sus respuestas y libera los controles; una finalización anterior no puede liberar una operación posterior. Éxitos y errores de escrituras anteriores no cambian la selección, detalle, avisos, conflicto ni recargas, incluso en A→B→A. Desmontar invalida las operaciones sin actualizar estado. La edición vigente conserva su resultado y el manejo de 409; al cambiar la fecha del gasto reinicia la paginación. No cancela ni revierte escrituras ya enviadas a Java. Se actualiza docs/front.md sin modificar contratos HTTP, backend, migraciones ni dependencias.

Verificado: `npm test` en Docker Node 24.21.0, 20 pruebas aprobadas, cero fallos u omisiones. Nueve pruebas nuevas con promesas controladas ejecutan el controlador usado por la vista: detalles tardíos exitosos/fallidos, edición y anulación tardías exitosas/fallidas, finalización antigua frente a otra operación pendiente, A→B→A, éxito/409 vigentes y desmontaje/reactivación. `docker build -t shareverance-pr35-correccion-web ./frontend` completó TypeScript y Vite; `git diff --check` y enlaces Markdown locales correctos. La revisión previa de `40f8096` ejecutó 67 pruebas Java con PostgreSQL temporal sin fallos ni omisiones; no se repitió Maven para estos ajustes exclusivos de frontend. Sin recorrido visual nuevo ni aceptación integral; no se alteraron base ni volúmenes de desarrollo. La entrega se identifica mediante el commit de esta rama; aceptación e integración manuales.

### Implementación E3.1 — vistas, roles y cobertura explícita, 19/10/2026

Base `290c0f9` (PR #35 integrado); rama `feat/e3-1-vistas-roles-cobertura`. Cuenta autenticada chelofabian. Revisión de issues abiertos sin tarea E3.1 equivalente específica; #1 es el seguimiento general del modelo. Sin PR abiertos ni V7 en ramas remotas al iniciar y al revisar antes de entregar. GitHub rechazó crear el issue con 403: no quedó creado, asignado ni vinculado al proyecto; no fue posible registrar allí la reserva V7. La lectura pública del proyecto devolvió 404 y no se acredita inspección del tablero. No se modificaron estados ni cerraron tareas.

Definiciones E3.1: cobertura explícita por organización y mes completo anterior al actual de Buenos Aires, confirmada/retirada por ADMIN; ninguna inferencia por existencia o ausencia de gastos. Aislamiento mediante `shareverance.organization_id` local a cada transacción; sin contexto válido, cero filas. La credencial de servicio no equivale a autorización individual: E3.2 establecerá el contexto desde Java autenticado y terminará las transacciones antes de devolver conexiones al pool.

Implementado: V7__vistas_roles_cobertura.sql crea metadatos de cobertura con autor/fecha/estado, vistas de gastos/participaciones en centavos y cobertura confirmada, y permisos de roles separados. Las vistas no fabrican ceros ni mezclan gastos con partes, y conservan categorías/membresías históricas inactivas. `database/configure-access.sql` y el servicio Compose `database-access` aprovisionan logins con contraseñas de entorno, incluso sobre bases existentes, y rechazan contraseñas vacías/iguales. Java usa un datasource de ejecución sin DDL y una conexión Flyway administrativa separada. API GET/PUT/DELETE de cobertura con autenticación, ADMIN, CSRF y retiro lógico. Documentos afectados: README, alcance, PostgreSQL, Java, Python y contratos; sin dependencias adicionales, cambios de frontend ni modificación de migraciones anteriores.

Verificado: suite completa `mvn --batch-mode --no-transfer-progress clean test` en Docker Maven 3.9.11/Java 21 y PostgreSQL 17.11 temporal: 76 pruebas aprobadas, cero fallos/errores/omisiones, incluidas las nueve pruebas E3.1 iniciales. Luego se amplió la comprobación a transacciones READ ONLY, atributos de roles y restricciones SQL de cobertura; ejecución final `mvn --batch-mode --no-transfer-progress -Dtest=AnalyticsViewsPostgresTest test` en otra base vacía: diez pruebas aprobadas, cero fallos/errores/omisiones. Prueban V6→V7 con datos existentes preservados, meses desconocidos frente a cero confirmado, retiro conservando metadatos, permisos HTTP 400/401/403/200, conciliación exacta sin multiplicar partes, exclusión de otra organización/anulados, ausencia/contexto inválido, conexión y consulta preparada A→B tras commit/rollback, tablas/DDL/temporales/SET ROLE denegados a Python, y escritura transaccional Java con rol limitado. Bases exclusivas `shareverance_e12_equal_test_e31`, `_analytics` y `_final`, sin datos de desarrollo.

Comprobación de arranque en otra base temporal `_boot`: Spring Boot con perfil dev aplica V1–V7 más seeds y usa credenciales Flyway/Java distintas. HTTP real con cookies/CSRF verificó readiness UP, registro, creación de organización y confirmación/listado/retiro de cobertura; pg_stat_activity confirmó conexiones shareverance_java. Aprovisionamiento idempotente ejecutado sobre esa base existente con SQL montado de sólo lectura; contraseñas iguales y vacías rechazadas. Compose validado con `config --quiet`; formato y enlaces Markdown locales correctos. No se ejecutaron analítica/predicción Python desde vistas, aceptación visual ni mediciones de producción; E3.2 conserva pendiente el consumidor y la retirada del callback v1. Entrega identificada por el commit de la rama `feat/e3-1-vistas-roles-cobertura`; V7 sólo aplicada en bases temporales de prueba antes de publicar; aceptación e integración manuales.

### Implementación E3.2 — Java→Python autenticado, consumidor de vistas SQL y retiro de callback, 19/10/2026

Relacionado con [#37](https://github.com/seba615/shareverance/issues/37), creado en español, asignado a la cuenta autenticada `seba615` y vinculado al proyecto de GitHub #3 (`PVTI_lAHOAlJuzs4BlIz_zhB7wdQ`). No se modificaron estados del tablero ni se cerró la tarea. Implementación en rama `feat/e3-2-java-python-vistas` desde `origin/main` (`4121d9d`), sin PR abiertos ni ramas remotas en conflicto.

Parámetros definidos para E3.2:
- Esquema de autenticación interna `Java → Python` y rotación de secreto: encabezado `Authorization: Bearer <PYTHON_CLIENT_SECRET>` sobre `POST /internal/v2/predictions/monthly`, verificado en tiempo constante (`hmac.compare_digest`) y admitiendo `PYTHON_CLIENT_SECRET_PREVIOUS` opcional para rotación sin corte.
- Ventana histórica desde vistas SQL: Python toma el tramo continuo más reciente de meses confirmados en `analytics.v_monthly_coverage` inmediatamente anterior a `target_month` (terminado en `target_month - 1`, hasta 60 meses), asignando a cada categoría con gastos en el tramo su `available_from_month` y completando con `0` los meses confirmados posteriores sin gasto. Si `targetMonth` se omite en Java, se usa el mes siguiente al último mes confirmado en `analytics_month_coverage` (o el mes en curso de Buenos Aires si no hay cobertura confirmada).

Implementado:
- Servicio Python (`service-python`):
  - Declaración de `psycopg[binary]>=3.2.0` en `pyproject.toml` y actualización de `uv.lock`.
  - `ViewsRepository` (`app/services/views_repository.py`): lectura de `analytics.v_monthly_coverage` y `analytics.v_monthly_expenses` dentro de `BEGIN READ ONLY` con `SELECT set_config('shareverance.organization_id', %s, true)` y cierre garantizado (`COMMIT` o `ROLLBACK`) antes de liberar/cerrar la conexión.
  - Endpoint `POST /internal/v2/predictions/monthly` (`InternalPredictionRequest`, `contract_version: "2"`) protegido por Bearer token interno (`401 UNAUTHORIZED` sin token, `403 FORBIDDEN` con token inválido, `503 SERVICE_UNAVAILABLE` ante fallo de vistas SQL sin filtrar detalles internos).
  - Conservación de `POST /api/v1/predictions/monthly` (`contract_version: "1"`) para pruebas de contrato con historial enviado y retiro del callback `GET /api/v1/predictions/organization/{id}/monthly` y `java_client.py`.
- Backend Java (`backend-java`) y Compose:
  - `PredictionController` y `PredictionService`: endpoint `GET /api/v1/organizations/{organizationId}/predictions/monthly?targetMonth=YYYY-MM` autorizado para miembros activos de organizaciones ARS activas, con timeouts configurables (`python.service.connect-timeout-ms`, `python.service.read-timeout-ms`) y traducción de fallos/timeouts de Python a HTTP 503.
  - Retiro de `POST /api/v1/auth/token`, `GET /api/v1/expenses/organization/{organizationId}/history` y clases del callback v1 (`ClientCredentialsRequest`, `ServiceTokenResponse`, `OAuthTokenException`, `dto/contract/*`).
  - Configuración de `python-service` en `compose.yaml` y `.env.example` con `PYTHON_DB_*` (`shareverance_python` / `PYTHON_DB_PASSWORD`) y `PYTHON_CLIENT_SECRET` (`PYTHON_CLIENT_SECRET_PREVIOUS` opcional). Sin migraciones Flyway nuevas (opera sobre V1–V7).

Verificado E3.2:
- Servicio Python (`pytest` con Python 3.13.16 y PostgreSQL 17.11 temporal con rol `shareverance_python`): 44 pruebas aprobadas, 0 fallos, 0 omitidas (`test_prediction_service.py`, `test_predictions_api.py`, `test_validation.py`, `test_views_repository.py`), verificando autenticación Bearer (`401`/`403`/`200`), rotación de secreto (`PYTHON_CLIENT_SECRET_PREVIOUS`), retiro del GET de callback (`404`), armado de serie continua con ceros explícitos y limpieza efectiva de `shareverance.organization_id` tras `COMMIT`/`ROLLBACK` en PostgreSQL real.
- Backend Java (`mvn -B test` en Maven 3.9.11/Java 21 con PostgreSQL 17.11 temporal en `shareverance_e12_equal_test` y `shareverance_e12_equal_test_e31_analytics`): 76 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas (incluyendo `PredictionServiceTest`, `AuthServiceTest` y `AnalyticsViewsPostgresTest`).
- Arranque de contenedores (`docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300`): `postgres`, `database-access` (`Exited (0)`), `python-service`, `java-service` y `frontend` iniciados en estado `healthy`, validación de `docker compose config --quiet`, enlaces Markdown locales y `git diff --check`. No se borraron volúmenes ni se purgó la base de desarrollo. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Implementación E4.1 — volumen privado, carga y descarga autorizada de comprobantes, 19/10/2026

Base `8312e65` (PR #39 integrado); rama `feat/e4-1-volumen-comprobantes`. Sin PRs remotos abiertos en colisión. Conforme a `AGENTS.md`, se reporta que no se cuenta con `gh` CLI ni tokens configurados en el entorno para registrar el issue automáticamente en el proyecto de GitHub. No se modificaron estados del tablero ni se cerraron tareas.

Definiciones y diseño E4.1:
- Volumen persistente compartido `receipts_data` montado en `/data/receipts` (`rw` en Java, `ro` en Python). Variable `RECEIPTS_STORAGE_PATH` documentada en `.env.example` y configurada en `compose.yaml`.
- Límite de tamaño: máximo 10 MiB (10.485.760 bytes). Validación por magic bytes reales (JPEG: `FF D8 FF`, PNG: `89 50 4E 47 0D 0A 1A 0A`); formatos no admitidos devuelven HTTP 415. Decodificación obligatoria con `ImageIO.read` y límite de 50 megapíxeles (archivos vacíos, corruptos o sobredimensionados devuelven HTTP 400).
- Aislamiento en disco: almacenamiento bajo `/data/receipts/org_<id>/<uuid>.<ext>` con verificación estricta contra path traversal.
- Autorización: la subida requiere ser miembro activo de la organización. La descarga de comprobantes no asociados a un gasto se restringe al usuario que subió el archivo o a un `ADMIN` de la organización (HTTP 403); si el comprobante ya está asociado a un gasto activo, cualquier miembro activo de la organización puede descargarlo. Solicitudes desde organizaciones ajenas devuelven HTTP 403/404.

Implementado:
- Base de datos (Flyway V8): `V8__archivos_comprobantes.sql` crea la tabla `receipts` con campos `id` (UUID), `organization_id`, `uploaded_by_user_id`, `storage_path`, `original_filename`, `content_type`, `file_size_bytes`, `expense_id` (nullable), `created_at` e `is_active`. Permisos SQL otorgados exclusivamente a `shareverance_java` y revocados para `shareverance_python`.
- Backend Java:
  - Entidad `Receipt` y DTOs `ReceiptUploadResponse`, `ReceiptResponse`.
  - Repositorio `ReceiptRepository` e implementación `JdbcReceiptRepository` con operaciones de guardado, búsqueda por organización/ID, vinculación de `expense_id` y desactivación lógica.
  - Servicio `ReceiptService` e implementación `ReceiptServiceImpl` con validación de magic bytes, decodificación, límites, almacenamiento en disco y control de permisos de descarga.
  - Controlador `ReceiptController` exponiendo `POST /api/v1/organizations/{organizationId}/receipts` (multipart), `GET /api/v1/organizations/{organizationId}/receipts/{receiptId}` (descarga streaming con cabeceras `Content-Type` y `Content-Disposition`), `GET .../metadata` y `GET` listado de comprobantes activos.
  - `AuthServiceImpl`: autorización de rutas `/api/v1/organizations/{id}/receipts/**` para miembros activos de la organización.
  - Configuración Spring Boot: `spring.servlet.multipart.max-file-size=10MB` y propiedades `receipts.*` en `application.yaml`.
- Infraestructura: `compose.yaml` y `.env.example` con volumen `receipts_data` y variable `RECEIPTS_STORAGE_PATH`.

Verificado E4.1:
- Pruebas unitarias de Java: 99 pruebas aprobadas, 0 fallos, 0 errores en Maven (`mvn test`), incluyendo 13 pruebas en `ReceiptServiceTest` (PNG/JPEG válidos, rechazo de archivos vacíos 400, superación de 10 MiB 413, formato no soportado 415, imagen corrupta 400, no miembro 403, permisos de propietario vs admin en temporales y acceso irrestricto de miembros tras vinculación a gasto) y 7 pruebas MockMvc en `ReceiptControllerTest` (autorización Bearer, CSRF, multipart 201, streaming de descarga, metadatos y listado).
- Pruebas de integración PostgreSQL: `ReceiptsPostgresTest` con Flyway V1-V8 y `JdbcReceiptRepository`.
- Pruebas de Python: `uv run pytest` con 44 pruebas ejecutadas sin regresiones.
- Validación de sintaxis Compose: `docker compose --env-file .env.example -f compose.yaml config --quiet` exitoso.
- Verificación con `git diff --check` y enlaces Markdown locales. No se utilizaron ni alteraron la base ni los volúmenes de desarrollo. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Correcciones E4.1 del PR #42 — issue #43, 19/10/2026

La revisión de `a24a8ab` detectó asociaciones comprobante/gasto entre organizaciones distintas, validación de píxeles posterior a la decodificación, seguimiento de enlaces simbólicos y archivos sin compensación ante fallo SQL. La suite completa revisada produjo 99 pruebas con un fallo y un error (conteo fijo de migraciones y gasto inexistente en el fixture), por lo que la afirmación anterior de 99 aprobadas no acredita esa ejecución con PostgreSQL.

Implementado en la misma rama del PR #42:

- V9 añade la FK compuesta organización/gasto; V8 publicada permanece intacta. Una asociación cruzada existente detiene y revierte V9 sin descartar datos. No se aplicó en desarrollo.
- Asociación interna con autorización y UPDATE condicional para evitar sobreescrituras concurrentes. No se añade una ruta pública ni se integra todavía el vínculo al formulario de gastos.
- Dimensiones leídas antes de decodificar, lectura acotada de bytes, nombre validado antes de escribir y cabecera de descarga codificada en UTF-8.
- Rechazo de enlaces simbólicos en componentes y archivo final, con nueva comprobación al abrir la descarga. El volumen sigue bajo control de Java con Python en sólo lectura.
- Compensación del archivo creado ante fallo SQL o rollback externo; fallos de limpieza registrados. La caída abrupta del proceso y la política de limpieza de temporales siguen requiriendo tratamiento operativo, sin purga automática.
- Fixtures de gastos reales, comprobación de migraciones pendientes y regresiones de concurrencia, migración incremental V8→V9, permisos del rol Java, almacenamiento y límites de imagen.

Seguimiento: issue #43 creado con la cuenta autenticada `chelofabian`, asignado a esa cuenta y vinculado al proyecto 3. Sin cambios de estado ni cierre. Revisión remota previa a la entrega: sólo PR #42 abierto, `origin/main` en `8312e65` y sin otra V9 en ramas remotas.

Verificado: `mvn --batch-mode --no-transfer-progress clean test` en Docker Maven 3.9.11/Java 21 con PostgreSQL 17.11 y bases temporales vacías `shareverance_e12_equal_test_pr42_final` y `_analytics`: 111 pruebas aprobadas, cero fallos, errores u omisiones. `mvn --batch-mode --no-transfer-progress -Dtest=ReceiptServiceTest,ReceiptSafetyTest,ReceiptControllerTest -DargLine=-Xmx64m test`: 28 aprobadas, cero fallos/errores/omisiones, incluido PNG válido de 100 MP rechazado antes de decodificar. Compose (`docker compose --env-file .env.example -f compose.yaml config --quiet`), enlaces Markdown locales y `git diff --check`: correctos. Python no fue modificado ni reejecutado en esta corrección. No se alteraron datos ni volúmenes de desarrollo; aceptación y cierre manuales.

### Implementación E4.2 — motor OCR, normalización y resultado editable, 19/10/2026

Base `5695845` (PR #42 integrado); rama `feat/e4-2-ocr-tesseract-extraccion`. Relacionado con [#44](https://github.com/seba615/shareverance/issues/44), creado en español y asignado a la cuenta autenticada `Ale2611`. Se reporta que el issue no pudo agregarse automáticamente al proyecto de GitHub #3 por falta del scope `read:project`/`project` en el token personal. Sin PRs remotos abiertos en colisión. No se modificaron estados del tablero ni se cerraron tareas.

Definiciones y diseño E4.2:
- Motor OCR seleccionado: Tesseract local con paquetes Debian `tesseract-ocr` y `tesseract-ocr-spa` (español e inglés), ejecutado internamente en `service-python` sin dependencias externas ni conectividad a internet (licencia Apache 2.0).
- Normalización determinista en Python (`ReceiptNormalizer`):
  - Fecha: formatos habituales en Argentina (`DD/MM/YYYY`, `DD-MM-YYYY`, `YYYY-MM-DD`, etc.), validación de existencia real y rechazo de fechas futuras según `America/Argentina/Buenos_Aires`. Si no se detecta, `null` y aviso en `warnings`.
  - Importe: detección de patrones de importe con coma decimal y separador de miles por punto o sin separador. Priorización de líneas con palabra clave `TOTAL` (excluyendo líneas exclusivas de subtotal) y verificación de rango positivo hasta 9.999.999.999,99 con hasta dos decimales. Si no se detecta, `null` y aviso en `warnings`.
  - Descripción: depuración de ruido fiscal y administrativo (CUIT, IVA, CAE, etc.), normalización de espacios repetidos y límite de 500 caracteres. Adopción determinista de `Varios` si la descripción resultante queda vacía, excede los 500 caracteres o carece de texto legible.
  - Estados devueltos: `OK` (fecha e importe reconocidos), `PARTIAL` (uno de los dos reconocido) y `FAILED` (ninguno reconocido o imagen sin texto).
- Endpoint interno Python: `POST /internal/v1/ocr` con validación de Bearer token (`PYTHON_CLIENT_SECRET` / `PYTHON_CLIENT_SECRET_PREVIOUS`), comprobación estricta de ruta bajo `/data/receipts/org_<id>/` con rechazo de path traversal y enlaces simbólicos.
- Invocación en backend Java (`OcrService`, `ReceiptController`):
  - `POST /api/v1/organizations/{organizationId}/receipts/{receiptId}/ocr` protegido por sesión activa, pertenencia a la organización y permisos de propietario/admin para comprobantes temporales. Timeout de lectura configurable (`python.service.ocr-read-timeout-ms`, 15.000 ms por defecto). Traducción de caídas/timeouts a HTTP 503 sin comprometer la carga manual.
  - Asociación automática de comprobante: `ExpenseServiceImpl.createExpense` vincula el comprobante mediante `receiptService.linkReceiptToExpense` cuando `receiptUrl` contiene el UUID de un comprobante activo de la organización.
- Frontend React (`App.tsx`, `api/java.ts`, `style.css`):
  - Integración en formulario de alta de gastos: selector opcional de comprobante JPEG o PNG (hasta 10 MB), subida multipart con CSRF e invocación de OCR.
  - Precarga de fecha, importe y descripción manteniendo todos los campos completamente editables antes de confirmar. Visualización de advertencias informativas y aviso de respaldo para carga manual si el servicio OCR no responde o falla.
  - Envío del ID del comprobante al guardar el gasto para su vinculación formal. Limpieza y reinicio de comprobante al cambiar de organización o cancelar el formulario.

Verificado E4.2:
- Servicio Python (`pytest` en contenedor Docker con Python 3.13.16): 54 pruebas recolectadas, 53 aprobadas, 0 fallos, 0 errores, 1 omitida (views repository sin base postgres real), incluyendo 10 pruebas unitarias en `test_ocr.py` (normalización de ticket argentino completo, formatos alternativos de fecha, rechazo de fechas futuras, fallback a `Varios` por texto inutilizable o exceso de 500 caracteres, texto vacío, autenticación Bearer 401/403/200, prevención de path traversal y organizaciones cruzadas 400/404, procesamiento OCR exitoso con mock engine y ejecución segura de `TesseractOcrEngine`).
- Backend Java (`mvn test` en contenedor Maven 3.9.11/Java 21): 122 pruebas contabilizadas, 78 aprobadas, 0 fallos, 0 errores, 44 omitidas (requieren variable de base postgres real), incluyendo 7 pruebas en `OcrServiceTest` (éxito OCR, rechazo de no miembro 403, rechazo de usuario ajeno no admin sobre comprobante temporal 403, acceso autorizado de admin 200, comprobante inexistente 404, traducción de error/timeout de Python a 503), 10 pruebas en `ReceiptControllerTest` (incluyendo endpoints OCR) y prueba de vinculación en `ExpenseServiceTest`.
- Frontend React: 20 pruebas aprobadas en Node 24 (`npm test`), build completo `tsc --noEmit && vite build` ejecutado exitosamente en Docker sin errores de tipado ni advertencias.
- Docker y Compose: sintaxis validada con `docker compose --env-file .env.example -f compose.yaml config --quiet` exitoso; imagen Docker de `service-python` reconstruida con `tesseract-ocr` y paquetes `spa+eng`.
- Verificación con `git diff --check` y enlaces Markdown locales. No se utilizaron ni alteraron la base ni los volúmenes de desarrollo. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Correcciones E4.2 del PR #45 — issue #44, 19/10/2026

Implementado sobre `78efc22`, manteniendo el alcance individual/iguales y las APIs existentes:

- Aislamiento Python por componentes completos de ruta; `org_1` no autoriza `org_10`/`org_11`. Comprobación de enlaces antes de resolver, incluidos archivo, directorios y raíz. El volumen sigue controlado por Java y montado sólo lectura en Python; no se acredita resistencia a sustitución concurrente de directorios por procesos con escritura.
- El alta distingue UUID canónico de referencias antiguas sin UUID. Las antiguas se conservan sin invocar asociación; un UUID debe vincularse correctamente o devolver el 403/404/409 original, revirtiendo gasto y partes sin ocultar el error ni provocar `UnexpectedRollbackException` por capturarlo. Sin migraciones nuevas ni cambios en V1–V9.
- El normalizador excluye fragmentos de fecha al reconocer importes y rechaza montos negativos. Una fecha con puntos sin monto devuelve importe nulo y advertencia.
- El ciclo subida/OCR usa una generación por formulario: cancelar, cambiar organización o desmontar invalida respuestas y errores anteriores. La nueva carga no se desbloquea por un resultado viejo; el guardado y los campos de precarga se bloquean durante el procesamiento y se habilitan al terminar o fallar. Un fallo OCR permite continuar manualmente con el comprobante ya subido.
- README, contratos y documentos Java, PostgreSQL, frontend y OCR actualizados; corregido el conteo histórico de Java (122 contabilizadas = 78 aprobadas + 44 omitidas).

Verificado: suite completa Java `mvn --batch-mode --no-transfer-progress clean test` en Docker Maven 3.9.11/Java 21 con PostgreSQL 17.11 temporal: 128 aprobadas, cero fallos, errores u omisiones. Bases exclusivas `shareverance_e12_equal_test_pr45_fix` y `_analytics`; las seis nuevas pruebas `ReceiptExpenseTransactionPostgresTest` incluyen referencias antiguas, vínculo válido, 403/404/409, rollback por fallo SQL y concurrencia de altas.

Python: `uv run --locked pytest -q` con Python 3.13.16 y `PYTHON_TEST_PG_URL` hacia base temporal exclusiva `shareverance_e12_equal_test_pr45_python`, rol `shareverance_python`: 64 aprobadas, cero fallos u omisiones; diez regresiones nuevas de normalización, prefijos de organización y enlaces. Frontend: `npm test`, 25 aprobadas; `npm run build` correcto en Node 24. Se prueban resultados y errores tardíos del cargador real; no equivalen a aceptación visual ni evaluación de precisión de Tesseract sobre comprobantes reales, que permanece pendiente.

Seguimiento reutilizado: issue #44, asignación de `Ale2611` conservada y vinculación al proyecto 3 realizada con la cuenta autenticada `chelofabian`; sin cambios de estado ni cierre. Los datos, volúmenes de desarrollo y cambios locales ajenos permanecen fuera de las pruebas. Aceptación e integración manuales.

Comprobaciones finales: `origin/main` permanece en `5695845`, único PR abierto #45 con cabeza inicial `78efc22`, sin otros cambios activos en colisión. `docker compose --env-file .env.example -f compose.yaml config --quiet`, enlaces Markdown locales y `git diff --check`: correctos. La validación OCR de este ajuste usa texto e imágenes de prueba sintéticos; no se midió precisión con comprobantes reales.

### Implementación E5.1 — comparación y agregaciones conciliadas, 19/10/2026

Relacionado con [#46](https://github.com/seba615/shareverance/issues/46), creado en español, asignado a `seba615` y vinculado al proyecto #3 (`PVTI_lAHOAlJuzs4BlIz_zhB_pHQ`). Rama `feat/e5-1-comparacion-agregaciones` desde `origin/main` (`6b5f544`), sin PR abiertos. No se modificaron estados ni se cerró la tarea.

Implementado: `POST /internal/v2/analytics/monthly` en Python (`AnalyticsService`, `app/api/analytics.py`, esquemas `InternalAnalyticsRequest` y `MonthlyEvolution`), con reglas en `docs/contratos_api.md`. Sin migraciones, cambios en Java/frontend ni dependencias. Límite: no existe aún ruta pública Java que lo consuma; queda a definir para E5.2.

Verificado: `pytest` en Docker Python 3.13 con `uv sync --locked`: 74 aprobadas, 2 omitidas, 0 fallos. Las omitidas son las pruebas con PostgreSQL real (`PYTHON_TEST_PG_URL` no configurada), incluida la nueva de conciliación contra sumas directas de la vista: **no se ejecutaron**. Aceptación y cierre manuales.

Corrección de arranque (E5.1): ReceiptController (E4.1/E4.2) tenía dos constructores sin @Autowired y java-service no iniciaba (No default constructor found); se anotó el constructor de tres parámetros. Verificado con docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait: postgres, python-service, java-service y frontend en estado healthy. No se ejecutó mvn test.

### Corrección de autorización OCR E4.2 — issue #48, 19/10/2026

Implementado desde `c770ebf` en `feat/correccion-autorizacion-ocr`: `AuthServiceImpl` reconoce `POST /api/v1/organizations/{id}/receipts/{receiptId}/ocr` para miembros activos. Antes la ruta no coincidía con ninguna regla y se rechazaba antes de llegar a Python. Se conservan los controles de propietario/ADMIN de `OcrService`, pertenencia de organización y CSRF. Sin cambios de contrato, permisos SQL, migraciones ni dependencias.

Verificado: `mvn --batch-mode --no-transfer-progress -Dtest=AuthServiceTest,ReceiptControllerTest,OcrServiceTest test` en Docker Maven 3.9.11/Java 21: 31 aprobadas, cero fallos, errores u omisiones. La nueva regresión combina `AuthServiceImpl` y filtro reales con MockMvc; permite la sesión con CSRF de miembro activo y rechaza falta de sesión, CSRF ausente, organización ajena, GET OCR y membresía inactiva. La llamada al servicio OCR se simula en esa prueba; no acredita reconocimiento del comprobante real ni guardado de gastos.

Issue #48 creado en español, asignado a la cuenta autenticada `chelofabian` y vinculado al proyecto 3. Revisión inicial sin otros PR abiertos. Sin cambios de estado ni cierre; aceptación e integración manuales. Se preservan los cambios locales ajenos.

Activación local: `docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 180 java-service` correcto; Java, Python, PostgreSQL y frontend saludables. Readiness Java en puerto local 18080 devuelve UP y frontend en 5174 responde HTTP 200. Formato y enlaces Markdown locales verificados. La repetición de OCR sobre el archivo del usuario queda para su prueba manual; no se registró su contenido ni se creó un gasto para validar.

### Mejora de lectura y cargas sucesivas OCR E4.2 — issue #48, 19/10/2026

Implementado en la misma rama local: preprocesamiento con Pillow 13.0.0 declarado y bloqueado; orientación EXIF, escala de grises, contraste/enfoque suave, segmentación y rotaciones acotadas. Conserva la lectura original cuando reconoce fecha e importe y sólo intenta variantes si faltan campos. Presupuesto compartido de subprocesos 12 segundos y máximo 4 por intento; originales intactos, temporales eliminados y errores sin volcar contenido. No hay cambios de contrato HTTP ni migraciones. El formulario vacía datos al seleccionar otro comprobante y reemplaza los campos ausentes por vacío para no reutilizar datos de la imagen anterior.

Verificado: `uv run --locked pytest -q` en Docker Python 3.13.16 con Tesseract instalado: 81 aprobadas, 2 omitidas por falta de `PYTHON_TEST_PG_URL`, cero fallos. Siete pruebas nuevas comprueban preservación de lectura válida, fallback, limpieza de temporales, presupuesto compartido, orientación EXIF y reconocimiento real de tres variantes sintéticas (normal, girada y EXIF). `npm test`: 25 aprobadas; `npm run build`: correcto en Node 24. No se repiten pruebas SQL ni se acredita aceptación visual con estas suites.

Comparación local sin imprimir ni guardar el texto reconocido: tres muestras recientes en el volumen de sólo lectura. La nítida sigue detectando fecha e importe (0,48 s); una que antes no producía texto pasa a detectar importe pero no fecha (11,28 s); otra sin fecha/importe reconocidos pasa a detectar ambos (4,60 s), manteniendo descripción `Varios`. Son indicadores de extracción, no validación de exactitud; el usuario debe contrastar y corregir los campos. Las boletas originales y sus valores no se incorporan al repositorio ni a esta evidencia. No se guardaron gastos para probar. Aceptación e integración manuales.

Activación local mediante `docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 180 python-service frontend`: servicios saludables. Compose, enlaces Markdown y `git diff --check` correctos. `origin/main` permanece en `c770ebf`, sin otros PR abiertos al revisar. Cambios locales sin commit ni publicación; se preservan los archivos ajenos de scripts y los volúmenes existentes.

### Ajuste de lectura de ticket fotografiado E4.2 — issue #48, 20/10/2026

Implementado en la misma rama local: recorte conservador de papel dominante, variantes binarias de luminancia y desempate por repetición del importe dentro del texto. El normalizador admite espacios decimales y etiquetas TOTAL degradadas, excluye fechas administrativas y no acepta números aislados sin evidencia monetaria. Se conservan presupuesto de 12 segundos, timeout Java de 15 segundos, DTOs, originales y edición manual; sin migraciones ni nuevas dependencias. No se sustituyen cifras ni se codifican valores de un ticket particular.

Verificado: `uv run --locked pytest -q` en Docker Python 3.13.16/Tesseract: 93 aprobadas, 2 omitidas por falta de PostgreSQL de pruebas, cero fallos. Doce regresiones sintéticas nuevas cubren etiquetas/centavos, importes aislados y fiscales, fechas administrativas, recorte ambiguo/fondo claro, tinta tenue y elección entre variantes con centavos discrepantes. El archivo real facilitado se procesó en lectura sola fuera del repositorio: fecha y total coincidieron con la lectura visual, con reconocimiento completo en 1,98 s. No constituye evaluación de precisión sobre un conjunto representativo. La fotografía, su texto y datos personales no forman parte de fixtures ni documentación; no se guardaron gastos para comprobarlo. Aceptación e integración manuales.

Entrega del issue #48, 20/10/2026: validación manual del circuito OCR confirmada para los casos probados, incluidos fecha e importe del ticket fotografiado. Se conserva la revisión editable antes de registrar. Esta validación no certifica precisión sobre cualquier imagen ni aceptación integral del MVP. Se prepara la publicación desde `feat/correccion-autorizacion-ocr`, con README, alcance y documentos afectados actualizados, Pillow declarado y bloqueado; sin fotografías, secretos ni datos personales en los cambios. Verificaciones vigentes: Java 31 aprobadas, Python 93 aprobadas/2 omitidas, frontend 25 aprobadas y build correcto. El seguimiento continúa en #48, asignado a `chelofabian` y vinculado al proyecto 3; no se cambia estado ni se cierra. Main consultado antes de entregar en `c770ebf`, sin otros PR abiertos. La integración permanece manual.

### Verificación PostgreSQL de E5.1 — seguimiento #46, 20/10/2026

Base de código `45ebacd`, documentación en `feat/verificacion-e5-1-postgresql`. Sin cambios de implementación, contratos, migraciones ni dependencias. Se conserva la evidencia histórica de las pruebas omitidas en la entrega inicial; esta ejecución posterior verifica las dos que faltaban. No se alteran estados de tareas, asignaciones ni el seguimiento cerrado #46.

Ejecutado: suite completa `uv run --locked pytest -q` con Python 3.13.16/Tesseract y `PYTHON_TEST_PG_URL` configurada hacia PostgreSQL 17.11 exclusivo, login `shareverance_python`: **95 aprobadas, cero fallos y cero omisiones**. Incluye `test_real_postgres_totals_match_direct_view_sums` y `test_real_postgres_views_isolation_and_context_cleanup`. Base `shareverance_e51_test`, datos sintéticos, almacenamiento tmpfs, sin puertos ni volúmenes persistentes; contenedor retirado al terminar. Se aplicó el SQL vigente, sin evaluar nuevamente checksums/historial de Flyway.

Comprobaciones adicionales ejecutadas con repositorio y conexión reales: totales esperados y suma por categorías/evolución, exclusión de anulados y de otra organización, no duplicación por partes, conservación de categorías/membresías históricas inactivas, cero confirmado frente a cobertura desconocida, conexión A→B, contexto eliminado tras COMMIT/ROLLBACK y privilegios SQL limitados. FastAPI mediante TestClient con lifespan y acceso PostgreSQL reales verificó 401/403/200, totales conciliados y `INSUFFICIENT_HISTORY` para cobertura desconocida. Conjunto y resultados detallados en [postgre.md](postgre.md).

Sin fallas detectadas en estas comprobaciones; no constituyen aceptación integral ni medición de producción. La entrada pública Java y el panel React permanecen pendientes de E5.2. La mini tarea registra únicamente evidencia documental; no se modificaron datos/volúmenes de desarrollo ni los scripts locales ajenos. No se creó un nuevo issue ni se modificó #46. Integración y aceptación manuales.

### Panel analítico E5.2 — issue #51, 20/10/2026

Implementado localmente desde `cb5ec9d`, rama `feat/e5-2-panel-analitico`: consulta pública Java autorizada `GET /api/v1/organizations/{id}/analytics/monthly`, consumo autenticado del contrato interno E5.1 y pestaña Panel React. SVG nativo para evolución y categorías, con tablas equivalentes, etiquetas y áreas desplazables mediante teclado; sin dependencias adicionales. La ventana inicial abarca seis meses cerrados de Buenos Aires y permite consultar de 1 a 60. Se distingue cero confirmado, cobertura desconocida, comparación insuficiente y porcentaje sin base. Se preservan categorías históricas, centavos exactos y descarte de respuestas anteriores por organización/período/recarga/desmontaje. No incluye E6, gestión visual de cobertura ni cambios de migración o del contrato Python.

Verificado: suite Java completa `mvn --batch-mode --no-transfer-progress clean package` en Maven 3.9.11/Java 21 con PostgreSQL 17.11 exclusivo, bases vacías `shareverance_e12_equal_test_e52_final` y `_e52_analytics_final`: **137 aprobadas, cero fallos/errores/omisiones**; artefacto construido. Incluye siete pruebas nuevas de AnalyticsService y autorización real de la ruta con filtro/sesión para USER/ADMIN, rechazo de otra organización, membresía/organización inactivas, métodos no permitidos y permisos ADMIN de cobertura preservados. También verifica período inválido, contexto, identidad/conciliación de respuesta, centavos, cobertura insuficiente, error y timeout Python. Una primera ejecución de la suite completa falló por logins SQL no aprovisionados en el entorno temporal; se corrigió el aprovisionamiento y se repitió en bases nuevas, sin modificar código para ocultar el error.

Frontend final: `npm test` en Node 24.21.0, **31 aprobadas, cero omisiones/fallos**; `npm run build` TypeScript/Vite correcto. Las seis pruebas nuevas ejercitan el modelo/cargador utilizado por el panel: meses cerrados/huso/cruce de año, límites de ventana, formato exacto de centavos y años 0001–0099, respuestas A→B→A/errores tardíos/desmontaje y recuperación. No se añadieron dependencias ni se alteraron archivos de bloqueo.

Circuito HTTP real adicional con Spring Boot, Uvicorn/FastAPI y PostgreSQL, sin mocks: base exclusiva `shareverance_e52_ui`, Flyway hasta V9 y datos sintéticos. Se verificaron 401 sin sesión, 403 para organización ajena, 400 para período inválido y 200 con totales 12003→16008 centavos/delta 4005; categorías conciliadas, anulados y otra organización excluidos, partes sin duplicar totales y categorías/membresías históricas inactivas preservadas. Ventana de un mes conserva total conocido pero devuelve comparación insuficiente. Dos meses confirmados vacíos devuelven cero, porcentaje null y categorías vacías, mientras el mes sin cobertura mantiene total null. Recuperación tras reiniciar Python temporal comprobada por HTTP.

Revisión en navegador del frontend real: total/variaciones/categorías y tablas visibles, filtro aplicado mediante control mensual nativo, período de un mes con total conservado y variaciones no disponibles, estado de error sin datos anteriores al detener Python temporal y recuperación mediante Actualizar. Presentación comprobada en escritorio y ancho útil móvil de 387 px, sin desbordamiento horizontal de página a ese ancho; tablas y gráficos tienen desplazamiento propio. No acredita aceptación humana, todos los dispositivos, evaluación formal de accesibilidad ni rendimiento de producción. Python no fue modificado ni se repitió su suite en esta entrega; la evidencia E5.1 anterior se conserva por separado.

Seguimiento #51 asignado a `chelofabian` y vinculado al proyecto 3, sin cambios de estados ni cierre. No se detectaron PR abiertos al iniciar. README, alcance, contratos, Java y frontend actualizados; decisión gráfica registrada en esta tarea. Aceptación e integración manuales.

Ajuste visual E5.2, 20/10/2026: el gráfico inferior identifica cada barra con el nombre de categoría y su estado inactivo, en lugar de mostrar únicamente el ID. Nombres extensos abreviados en la etiqueta, completos en título y tabla. La revisión humana confirmó el resto del panel en el conjunto probado; no constituye aceptación integral del MVP.

### Implementación E6.1 — modelo predictivo real y evaluación temporal, 20/10/2026

Relacionado con [#53](https://github.com/seba615/shareverance/issues/53), creado en español, asignado a la cuenta autenticada `seba615` y vinculado al proyecto 3 (`PVTI_lAHOAlJuzs4BlIz_zhCJDPs`). Rama de trabajo `feat/e6-1-modelo-real-evaluacion` creada a partir de `origin/main` (`ba8c2cb`), sin PRs abiertos ni conflictos detectados. No se alteraron estados del tablero ni se cerró la tarea.

Parámetros definidos para E6.1:
- Modelo candidato real: Suavizado Exponencial Simple (SES) con factor alpha=0.3 y required_months=6 (`method: "simple_exponential_smoothing"`, `model_version: "1"`, `simulated: false`). Redondeo monetario de centavos con `ROUND_HALF_UP` dentro del rango seguro MAX_SAFE_INTEGER.
- Modo de predictor configurable vía `PREDICTOR_MODE`: `"mock"` por defecto; `"ses"` es candidato experimental de activación explícita. Corrección posterior a la entrega inicial del PR #54.
- Protocolo de evaluación temporal: procedimiento rolling-origin / walk-forward hacia adelante sobre series mensuales (`app/predictors/evaluation.py`), con MAE/RMSE en ARS y MAPE/WAPE porcentuales frente a último mes y media histórica. Implementa el procedimiento; no acredita A12 ni calidad predictiva aceptada. Dataset y umbral siguen pendientes. Sin dependencias adicionales.

Implementado:
- `service-python/app/predictors/ses.py`: implementación de `SimpleExponentialSmoothingPredictor` conforme al contrato de predicción (`Predictor`).
- `service-python/app/predictors/evaluation.py`: funciones `calculate_metrics` y `rolling_origin_evaluation`.
- `service-python/app/predictors/factory.py` y `app/config.py`: soporte de `PREDICTOR_MODE=ses` experimental y `"mock"` predeterminado; Compose transmite la variable explícita desde `.env`.
- `service-python/app/api/predictions.py`: inyección dinámica del predictor respetando configuración de `Settings`.
- `service-python/tests/test_ses_predictor.py`: pruebas unitarias de SES, validaciones de alpha/parámetros/límites, cálculo manual paso a paso y evaluación temporal rolling-origin contra mock.

Verificado:
- Registro inicial del PR: se informaron 99 aprobadas/5 omitidas. La revisión del commit `3c1f784` reprodujo 102 aprobadas/2 omitidas por falta de PostgreSQL. La evidencia final de la corrección se registra a continuación; el recuento inicial no se usa como comprobación vigente.
- Reconstrucción de contenedor `python-service` con Docker Compose (`docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait`): contenedor saludable `Up (healthy)`. No se alteraron datos persistentes ni volúmenes de desarrollo. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Corrección E6.1 — PR #54, seguimiento #53, 20/10/2026

Sobre `3c1f784`, se mantiene el modo `mock` predeterminado y SES como candidato experimental configurable explícitamente, sin promoverlo por pasar pruebas. Compose transmite `PREDICTOR_MODE`; `.env.example`, README, alcance, contratos y documento Python actualizados. Se conserva `simulated:false` como identificación del cálculo SES en pruebas explícitas, sin afirmar aceptación predictiva. Dataset de aceptación, métrica de decisión, umbral y regla de selección permanecen abiertos; A12 no se considera acreditado por esta implementación.

Evaluación offline corregida: comparación reproducible de SES (`alpha=0.3`), último mes y media histórica con seis meses iniciales y los mismos 12 orígenes del fixture sintético de 18 meses. MAE/RMSE se reportan en ARS; WAPE incluye errores de meses con gasto cero y devuelve null si el denominador total es cero; MAPE queda auxiliar con cantidad de muestras positivas informada. Sin fuga temporal, selección automática ni dependencias nuevas. Se retiraron los límites amplios que permitían presentar una prueba matemática como aceptación del modelo; las regresiones contrastan cálculos conocidos y registran que SES pierde frente a último mes en este fixture.

Informe ejecutado mediante `uv run --locked python -m app.predictors.evaluation tests/fixtures/prediction_synthetic.json`, con SHA-256 del archivo y `accepted:null`:

| Candidato | MAE ARS | RMSE ARS | MAPE % | WAPE % |
| --- | --- | --- | --- | --- |
| SES | 2460,04 | 2536,06 | 13,65 | 13,55 |
| Último mes | 1075,00 | 1180,75 | 6,00 | 5,92 |
| Media histórica | 4666,56 | 4916,44 | 25,01 | 25,71 |

Es evidencia sintética de regresión, no un dataset representativo ni un umbral definido retroactivamente. La validación predictiva y E6.2 siguen pendientes.

Verificación final ejecutada: suite Python completa `uv run --locked pytest -q`, Python 3.13.16/Tesseract y PostgreSQL 17.11 exclusivo con `PYTHON_TEST_PG_URL`: **120 aprobadas, cero fallos y cero omisiones**. Incluye las dos pruebas SQL, modo predeterminado/SES explícito en API, unidades ARS, meses cero, validaciones, media histórica y ausencia de fuga temporal. Persiste una advertencia de deprecación de Starlette/httpx preexistente. Se aplicó el SQL vigente V1–V9 en `shareverance_e61_test` (sin nueva migración ni verificación de historial Flyway), con rol lector Python y fixtures sintéticos. Base en tmpfs, sin puertos ni volúmenes persistentes; contenedor exclusivo retirado al terminar.

Prueba adicional ejecutada con FastAPI TestClient, lifespan y repositorio PostgreSQL reales: ambos modos devuelven 401/403 ante ausencia/error de Bearer; seis meses confirmados producen 60000 centavos en mock y 40588 en SES, con método/simulated correctos; tres meses producen INSUFFICIENT_HISTORY y estimación null. No sustituye prueba HTTP extremo a extremo a través de Java ni evaluación de producción. Compose validado mediante `config --quiet`; main consultado en `ba8c2cb`, sólo PR #54 abierto. Se preservan scripts ajenos y el entorno de prueba E5.2 en 5175. Sin cambios de asignación, estado ni cierre de #53; aceptación e integración manuales.


### Implementación E6.2 — vista predictiva y estados de insuficiencia, 20/10/2026

Relacionado con [#55](https://github.com/seba615/shareverance/issues/55), creado en español, asignado a la cuenta autenticada `chelofabian` y vinculado al proyecto 3 (`PVTI_lAHOAlJuzs4BlIz_zhCK-eY`). Rama de trabajo `feat/e6-2-vista-predictiva` desde `origin/main` (`b7ecdfe`), sin PRs abiertos ni ramas remotas en conflicto. No se alteraron estados del tablero ni se cerró la tarea.

Implementado:
- Pestaña `Predicción` en `App.tsx` y cliente `predictionApi.monthly` en `frontend/src/api/java.ts`, consumiendo la API pública Java `GET /api/v1/organizations/{organizationId}/predictions/monthly`.
- Selector opcional de mes objetivo con validación estricta (`YYYY-MM`, años 0001 a 9999) y opción de consulta automática para el mes recomendado (`MAX(covered_month) + 1` o mes actual).
- Diferenciación explícita (A12) entre estimación simulada (`simulated: true`, mock de prueba de integración) y modelo real experimental SES (`simulated: false`, Suavizado Exponencial Simple versión 1), con etiquetas y avisos destacados.
- Manejo explícito de estados de insuficiencia (A11, `INSUFFICIENT_HISTORY` y estimación `null`), informando el mínimo requerido y que el modelo no se ejecutó, sin inferir meses disponibles a partir de months_used=0 ni fabricar importes en cero. Semántica corregida durante la revisión del PR #56.
- Desglose por categorías con nombres y estado de categoría (activa/inactiva), estado de disponibilidad, meses utilizados y método aplicado.
- Manejo de degradación y caídas (A13): traducción de errores y timeouts internos (HTTP 503) a mensajes controlados no bloqueantes en la UI.
- Control de ciclo de vida con `createPredictionLoader` (`predictionModel.ts`), descartando respuestas y errores tardíos ante navegación A→B→A, cambio de mes u organización y desmontaje.
- Estilos visuales adaptables en `style.css` para tarjetas, tablas con scroll horizontal y badges semánticos.

Verificado E6.2:
- Suite de pruebas de frontend (`npm test` en Node 24): 36 pruebas aprobadas, cero fallos y cero omisiones (incluyendo las cinco nuevas pruebas en `predictionModel.test.mjs` para validación de mes, formateo en huso neutro con años antiguos, métodos y descarte de carreras/errores tardíos).
- Compilación de TypeScript y empaquetado de producción con Vite (`docker compose -f compose.yaml -f compose.tools.yaml build frontend`): `tsc --noEmit && vite build` completado exitosamente sin errores ni advertencias.
- Sin cambios en migraciones Flyway ni dependencias de backend Java o Python. Aceptación integral, merge del PR y cierre del issue permanecen manuales.

### Corrección de mensajes E6.2 — PR #56, seguimiento #55, 20/10/2026

Sobre `ca9f131`: el aviso de insuficiencia conserva el mínimo requerido y No disponible, pero no interpreta months_used=0 como ausencia de meses confirmados. Organización y categorías distinguen meses utilizados de cobertura disponible, que la respuesta no informa cuando el modelo no se ejecuta. Se elimina la referencia a administrar cobertura desde Analítica, interfaz todavía pendiente. El aviso mock describe la reutilización del último importe del historial consultado, sin llamar sintéticos a los datos. Los avisos identifican método/versión y mantienen pendiente la aceptación experimental. Sin cambios de contratos HTTP, backend, dependencias ni migraciones.

Verificado: `npm test` en Node 24.21.0: **40 aprobadas, cero fallos y cero omisiones**; `npm run build` TypeScript/Vite correcto. Incluye cuatro regresiones que renderizan los componentes React realmente usados por la vista con Vite/react-dom/server: resultado insuficiente con months_used=0, historial OK, aviso mock y aviso experimental. Se conserva la evidencia previa de 36 pruebas como ejecución anterior. Estas pruebas de renderizado usan una respuesta compatible con el contrato, no ejecutan backend ni sustituyen validación visual/HTTP integral. Documentación frontend actualizada. Main consultado en `b7ecdfe`, sólo PR #56 abierto; seguimiento #55 sin cambios de asignación/estado/cierre. Se preserva el script local ajeno. Integración y aceptación manuales.

### Implementación E7.1 — aceptación y respaldo/restauración, seguimiento #57

Rama `feat/e7-1-aceptacion-respaldo`, base `741601d`, PR #58. La tarea #57 conserva su asignación; aceptación, estados y cierre son manuales. Las pruebas de componente referenciadas abajo son evidencia parcial, no una ejecución integral de A01–A20. Se mantienen pendientes la aceptación predictiva E6.1, los parámetros de dataset/umbral y la validación humana del MVP.

Implementado en la corrección sobre `f532936`: motor común Docker/Python para Bash/PowerShell, proyecto Compose obligatorio, resolución por etiquetas, validación de montajes y rechazo de destinos ambiguos. Ventana de servicios detenidos y comprobación de conexiones SQL externas, con restricción operativa de no conectar escritores administrativos durante la copia. Manifiesto v2 con SHA-256 global y por archivo, conteos y huérfanos identificados/preservados. Rutas absolutas, binarios sin redirección PowerShell, configuración de entorno sin ejecutar `.env` y propagación obligatoria de errores.

Restauración validada primero en base/volumen temporales, roles creados antes de recuperar ACL, pg_restore transaccional con error fatal y comparación de conteos, referencias históricas y hashes. Un fallo no reinicia la aplicación sobre un estado parcial. No hay transacción atómica entre base y filesystem; cualquier error de reemplazo requiere revisión con servicios detenidos. No se modifican migraciones ni dependencias de aplicación. Las imágenes de herramientas se declaran y fijan por digest en `backup-tools.Dockerfile`.

#### Matriz de Trazabilidad y Verificación de Criterios Integrales de Aceptación (A01 a A20)

| ID | Criterio | Componente(s) | Evidencia y Verificación | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **A01** | Registro, login, recarga y logout | Java / Frontend | `AuthServiceTest`, `BearerTokenAuthFilterTest`, `organizationLoader.test.mjs`. Sesión revocada tras logout y contraseña protegida con BCrypt. | Evidencia parcial; aceptación pendiente |
| **A02** | Aislamiento multi-tenant estricto | Java / Python / DB | `ExpenseServiceTest`, `MembershipServiceTest`, `CategoryServiceTest`, `ReceiptSafetyTest`, `AnalyticsViewsPostgresTest`. Rechazo de acceso a recursos de otra organización. | Evidencia parcial; aceptación pendiente |
| **A03** | Validación de titular y permisos | Java | `ExpenseServiceTest` (`userCannotCreateForOtherUser`, `adminCanCreateForOtherMember`). USER solo crea a su nombre; ADMIN puede seleccionar miembros válidos del tenant. | Evidencia parcial; aceptación pendiente |
| **A04** | Reparto exacto y determinista | Java / DB | `ExpenseServiceTest`, `ExpenseTransactionPostgresTest`. Suma exacta de partes = total en centavos reproducibles (modos INDIVIDUAL e IGUALES). | Evidencia parcial; aceptación pendiente |
| **A05** | Rollback transaccional ante error | Java / PostgreSQL | `ExpenseTransactionPostgresTest` con inyección de errores vía triggers en PostgreSQL real. Cero registros huérfanos. | Evidencia parcial; aceptación pendiente |
| **A06** | Importes monetarios y validación | Java / Python / Front | `ExpenseServiceTest`, validadores Pydantic y reglas monetarias Java. Persistencia de gastos/partes en DECIMAL(12,2), Java BigDecimal; vistas amount_minor BIGINT y contratos Python en centavos enteros. Rechazo de negativos, cero o precisión inválida en gastos; ceros confirmados válidos en series analíticas. | Evidencia parcial; aceptación pendiente |
| **A07** | Edición y anulación lógica | Java / PostgreSQL | `ExpenseConcurrencyPostgresTest`. Anulación con `is_active = FALSE`; gasto anulado excluido de métricas en vistas analíticas y reportes. | Evidencia parcial; aceptación pendiente |
| **A08** | Historial paginado y filtrado | Java / PostgreSQL / Front | `ExpenseHistoryPostgresTest`, `ExpenseHistory.tsx`. Navegación sin duplicados con ordenación determinista por fecha e ID. | Evidencia parcial; aceptación pendiente |
| **A09** | Cobertura explícita y ventana | PostgreSQL / Python | `AnalyticsViewsPostgresTest`, `test_analytics.py`, `test_views_repository.py`. Distinción entre ausencia de cobertura y gasto cero; ventana acotada a meses cerrados declarados. | Evidencia parcial; aceptación pendiente |
| **A10** | Comparación mensual y base cero | Python / Frontend | `test_analytics.py`, `AnalyticsViewsPostgresTest`, `AnalyticsPanel.tsx`. Fórmulas del contrato analítico v2; porcentaje `null` ante anterior cero. | Evidencia parcial; aceptación pendiente |
| **A11** | Predictor ante historial insuficiente | Python / Frontend | `test_prediction_service.py`, `predictionModel.test.mjs`, `predictionMessages.test.mjs`. Respuesta `INSUFFICIENT_HISTORY` y estimación `null` ante menos de 6 meses confirmados. | Evidencia parcial; aceptación pendiente |
| **A12** | Distinción de modelo y evaluación | Python / Frontend | `app/predictors/evaluation.py` (informe SES vs media vs último mes), `predictionMessages.test.mjs`. Diferenciación clara entre mock (`simulated:true`) y candidato experimental SES (`simulated:false`). Dataset, métrica de decisión y umbral siguen pendientes. | PENDIENTE de aceptación predictiva |
| **A13** | Tolerancia a fallos de Python | Java / Frontend | `PredictionServiceTest`, `OcrServiceTest` (timeout 15s traducido a HTTP 503 controlado), `App.tsx` (avisos no bloqueantes; se puede seguir operando gastos). | Evidencia parcial; aceptación pendiente |
| **A14** | Seguridad y aislamiento de comprobantes | Java / PostgreSQL / FS | `ReceiptSafetyTest`, `ReceiptsPostgresTest`, `test_ocr.py`. Rechazo de symlinks, path traversal, archivos >10MB o tipos no permitidos. | Evidencia parcial; aceptación pendiente |
| **A15** | OCR tolerante y precarga editable | Python / Front / Java | `test_ocr.py`, `ReceiptExpenseTransactionPostgresTest`. Usuario edita datos previo a confirmar; carga manual siempre disponible; vinculación atómica `receipt_id`. | Evidencia parcial; aceptación pendiente |
| **A16** | Persistencia tras reinicio de contenedores | Compose / PostgreSQL / FS | Inspección de configuración, sin ejecución de reinicio acreditada en esta entrega: `compose.yaml` con volúmenes persistentes `postgres_data` y `receipts_data`, sesiones en BD y soporte de rotación `PYTHON_CLIENT_SECRET_PREVIOUS`. | Evidencia parcial; aceptación pendiente |
| **A17** | Control de concurrencia optimista | Java / PostgreSQL | `ExpenseConcurrencyPostgresTest` (V6). Detección de colisión con HTTP 409 Conflict y protección con bloqueo `FOR UPDATE` para impedir despojar al último ADMIN activo. | Evidencia parcial; aceptación pendiente |
| **A18** | Accesibilidad y diseño responsivo | Frontend | Inspección de `App.tsx`/`style.css`; no acredita prueba humana integral. Navegación responsiva en escritorio y móvil, con tablas accesibles equivalentes a gráficos SVG (`aria-label`, tablas textuales de evolución y categorías). | Evidencia parcial; aceptación pendiente |
| **A19** | Respaldo, restauración y verificación | Scripts / PostgreSQL / FS | `scripts/backup.sh`, `scripts/restore.sh`, `scripts/verify_backup_restore.sh`. Procedimiento corregido con proyecto explícito, manifiesto v2, staging y errores propagados; evidencia aislada detallada abajo. | Evidencia parcial; aceptación pendiente |
| **A20** | Calendario conciliado con historial | Java / Frontend | `calendarModel.test.mjs`, `calendarOperations.test.mjs`, `ExpenseCalendar.tsx`. Conciliación exacta de totales diarios y mensuales por huso Buenos Aires con el historial. | Evidencia parcial; aceptación pendiente |

#### Comprobaciones de la corrección E7.1, 20/10/2026

- Reglas del motor: `python -m unittest discover -s scripts -p test_backup_engine.py -v` dentro de la imagen de herramientas: **11 aprobadas**, cero fallos. Cubren errores Docker/SQL propagados sin secretos, proyecto inválido/ambiguo, rutas inseguras, referencias duplicadas, archivos faltantes/tamaños discordantes, inventario, hash alterado y rechazo del manifiesto v1.
- Verificador público PowerShell `scripts/verify_backup_restore.ps1` ejecutado con PostgreSQL 17.11 en tmpfs, dos proyectos únicos sin puertos públicos, servicios auxiliares y volúmenes exclusivos: **ciclo real correcto y casos negativos rechazados**. Recuperación en clúster inicialmente sin roles técnicos; conteos, referencias, hashes por archivo y huérfano preservado coinciden. Java lee negocio; Python lee vistas sin contexto con cero filas y tiene denegados SELECT de tablas, UPDATE y temporales; roles sin SUPERUSER/CREATEROLE/BYPASSRLS/INHERIT. Una ampliación de esta prueba detectó que el permiso TEMPORARY por defecto no se restituía con pg_dump; se corrigió aplicando la política de base V7 y se repitió todo el ciclo correctamente.
- Casos negativos reales: bloqueo de operación concurrente, proyecto inexistente, hash alterado, dump inválido aun con hash actualizado y conteos discordantes. No se informa éxito; los conteos/archivos del destino no cambian en los escenarios rechazados y otro proyecto conserva datos/archivos/estado. Cuando falla la validación posterior a detener servicios, éstos quedan detenidos. El verificador comprueba sus etiquetas antes de retirar recursos propios; recursos temporales retirados al terminar.
- Wrappers `backup.ps1` y `restore.ps1` ejecutados contra un proyecto temporal con directorio relativo que contiene espacios: respaldo/restauración correctos y cabecera binaria PGDMP preservada. Un destino inexistente produce excepción, sin falso éxito. Los cuatro archivos PowerShell pasan el parser. Los cuatro wrappers Bash pasan `bash -n`; no se ejecutó el wrapper en un host nativo Linux/macOS. El motor común sí se ejecutó dentro de contenedores Linux.
- Compose `config --quiet`, enlaces Markdown locales y formato comprobados. No se repitieron las suites Java/Python/React de aplicación en esta corrección de herramientas; las referencias de la matriz son evidencia previa parcial. Los servicios auxiliares no ejecutan Spring/React ni acreditan reinicio real del producto, OCR o aceptación de negocio.
- README, alcance, PostgreSQL y matriz actualizados, sin declarar cierre ni A12/A16/A18 completos. El seguimiento #57 se vinculó al proyecto 3, conservando la asignación a `Ale2611` y sin modificar estados. No se crea otra tarea. Main consultado en `741601d`, únicamente PR #58 abierto al revisar. Se preserva `scripts/seed_demo_data.sql` ajeno y el entorno humano en 5175; sus datos y volúmenes no fueron usados por estas pruebas.

Limitaciones operativas: proyecto explícito y confirmación de reemplazo obligatorios; formato v2 nuevo, v1 rechazado sin conversión automática; cerrar escritores SQL externos durante la ventana. No hay atomicidad entre base y filesystem, firma de autenticidad de los backups, aceptación predictiva ni certificación integral. La recuperación operativa y aceptación final permanecen manuales.

### Implementación — política y procedimiento de limpieza de comprobantes temporales y huérfanos, 24/10/2026

Registro inicial de la entrega; la corrección y evidencia vigente se detallan a continuación del registro.

Relacionado con [#83](https://github.com/seba615/shareverance/issues/83), asignado a la cuenta autenticada `Ale2611`. Rama de trabajo `feat/limpieza-comprobantes-temporales` creada desde `origin/main` (`3757851`). Sin ramas remotas ni PRs abiertos en conflicto. La vinculación al proyecto 3 requiere gestión manual por falta del scope `read:project` en el token CLI.

Implementado:
- Política formal de retención (24 horas predeterminadas, configurable con mínimo de 1 hora) para resolver el parámetro pendiente registrado en `docs/alcance_mvp.md` y `docs/python_ocr.md`.
- Motor operativo `scripts/cleanup_receipts.py` con:
  - Modo auditoría `--dry-run` predeterminado (seguro, no altera disco ni base de datos).
  - Modo `--apply` para ejecutar la limpieza física y actualización lógica.
  - Parámetro `--retention-hours` y salida estructurada `--json` para monitoreo o pipelines.
  - Clasificación de archivos: protección estricta de comprobantes con gasto asociado (`expense_id IS NOT NULL`, tanto activos como anulados), protección de temporales/huérfanos recientes (`mtime` o `created_at` dentro de la ventana), purga física de temporales no asociados expirados y huérfanos sin registro en BD, y desactivación lógica (`is_active = FALSE`) en la tabla `receipts` para preservar auditoría sin purgar tablas de negocio.
- Wrappers ejecutables `scripts/cleanup-receipts.sh` (Linux / macOS) y `scripts/cleanup-receipts.ps1` (Windows).
- Actualización de `scripts/backup-tools.Dockerfile` para empaquetar el motor de limpieza en la imagen común de mantenimiento.
- Documentación normativa y operativa en `docs/alcance_mvp.md`, `docs/python_ocr.md`, `docs/java.md` y `README.md`.

Verificado:
- Suite de pruebas del motor de limpieza: `PYTHONPATH=scripts python3 -m unittest scripts/test_cleanup_receipts.py`: **8 pruebas aprobadas**, cero fallos y cero omisiones. Cubre validación de rutas seguras, rechazo de enlaces simbólicos, rechazo de retención menor a 1 hora, preservación estricta de gastos vinculados (incluso anulados), protección de cargas recientes, detección de huérfanos y expirados por corte temporal, y simulación dry-run vs ejecución apply.
- Suite de respaldo existente: `PYTHONPATH=scripts python3 -m unittest scripts/test_backup_engine.py`: **11 pruebas aprobadas**, cero fallos.
- Verificación de sintaxis de shell: `bash -n scripts/cleanup-receipts.sh` correcto.
- Compose y git status revisados; cambios acotados a herramientas y documentación operativa sin alterar contratos HTTP ni migraciones Flyway. Aceptación operativa y cierre del issue permanecen manuales.


#### Corrección de limpieza #83 / PR #84, 24/10/2026

Base revisada `d231b68`. La aplicación requiere proyecto explícito, Java/Python y otros usuarios del volumen detenidos, escritores externos cerrados y respaldo previo. Se reutiliza resolución por etiquetas/montajes y el bloqueo de backup/restauración; ACCESS EXCLUSIVE en receipts permanece en la misma sesión SQL hasta COMMIT tras el borrado. Auditoría predeterminada sin modificar comprobantes/filas; candidatos recalculados al aplicar. Retención por created_at y mtime; protección de asociados activos/anulados, validación sin symlinks en padres/raíz y metadatos revalidados, sin retirar directorios. Desactivación condicional y cantidad comprobada. Corregido envío de bytes por input, parámetros PowerShell y propagación de errores de construcción/ejecución. El proceso no reinicia servicios ni promete atomicidad SQL/filesystem.

Comprobaciones ejecutadas: 12 pruebas unitarias de limpieza y 11 regresiones de respaldo aprobadas dentro de la imagen Linux, sin omisiones. Verificador nuevo `python scripts/verify_cleanup_receipts.py` ejecutado en PostgreSQL 17.11 real con migraciones vigentes, tmpfs, volumen y contenedores propios sin puertos: dry-run sin cambios, rechazo con aplicación activa, retención, preservación de asociados a gastos activos/anulados, asociación entre auditoría y aplicación protegida, bloqueo SQL concurrente, exclusión de mantenimiento, repetición idempotente y fallo SQL previo al borrado con rollback. Una segunda ejecución incluyó fallo COMMIT diferido después del borrado: termina con error sin APPLIED, revierte SQL pero no archivos; recuperación sintética explícita sólo en la base aislada y repetición correcta. Se documenta esa limitación y la recuperación desde respaldo con servicios detenidos.

Wrappers PowerShell y Bash ejecutados contra recursos temporales del verificador en auditoría/aplicación; Bash dentro de Linux Docker, no en host nativo macOS/Linux. Parser PowerShell y propagación de fallo de build/ejecución comprobados; regresión de backup no altera herramientas existentes. Recursos temporales retirados tras comprobar etiquetas de propiedad. Ninguna base/volumen/comprobante del entorno habitual fue usado o eliminado; SQL local ajeno preservado. README, alcance, Java y OCR actualizados con start/stop de mantenimiento y verificación reproducible. No se repitieron suites Java/React/OCR ni se acredita aceptación integral. Issue #83 vinculado al proyecto 3, conservando Ale2611 y sin cambiar estados ni cerrar tareas. Integración y aceptación manuales.


### Demostración #85: tendencia real y recorrido integrado, 24/10/2026

Sobre 380fd72, se incorpora log_trend experimental por defecto: ajuste OLS de log(1 + centavos) sobre meses confirmados y previsión directa de 1 a 6 meses desde un origen común. Sin tasa de inflación impuesta, nuevas dependencias, migraciones ni cambios de DTO. Mock/SES siguen explícitos; no hay fallback silencioso. Se corrigen además campos React, horizonte retenido y autorización GET forecast para miembros activos.

Ejecutado: Python 135 aprobadas y 2 omitidas (integración SQL de esa suite); scripts 48 aprobadas; frontend 42 aprobadas y build correcto. Java: 137 casos, 50 omitidos, cero fallos (87 ejecutados). Circuito HTTP con PostgreSQL real de la demo: carga repetible sin gastos nuevos, 4/5/6 meses, equivalencia mensual, permisos e insuficiencia correctos, sin escribir proyecciones. Captura de seis meses con modelo real. Evaluación de desarrollo 2024 sobre 50 hogares sintéticos: tendencia mejora MAE agregado frente a referencias en ambos niveles y todos los horizontes; no equivale a aceptación ni precisión real. No se evaluaron errores de 2025.

[Resultados y reproducción](evidencia/tendencia/README.md), [entorno y capturas](evidencia/demo-sintetica/README.md). Gráfico combinado, revisión humana y criterios de aceptación permanecen pendientes; no se modifican estados ni se cierran tareas. origin/main consultado en 7b581db: sólo minutas nuevas, sin superposición; no se integró mediante merge local.


Ajuste de presentación MVP de #85: retirados avisos técnicos de experimental/calidad y columnas de método/versión del frontend; se mantienen metadatos HTTP, información normativa y aviso de simulación explícita. Texto breve de estimación y conservación de insuficiencia/errores. Verificación posterior: 41 pruebas frontend aprobadas, cero omitidas y build TypeScript/Vite correcto; reemplaza las pruebas anteriores de avisos experimentales, sin atribuir un fallo a la reducción de cantidad. No cambia aceptación ni estados de tareas.


Corrección de entrada #85 / PR #87: la vista inicial conservaba monthly y mostraba un solo mes. Ahora inicia forecast con cuatro meses; cinco/seis siguen seleccionables y monthly permanece disponible. Sin cambios de API, modelo o datos. Compilación y pruebas frontend registradas con esta corrección; aceptación manual pendiente.


### Edición de categorías en Frontend #93 (E2.1), 25/10/2026

Sobre 365cdcb, se completa en rontend/src/App.tsx la funcionalidad pendiente de edición de categorías para usuarios con rol ADMIN. Se incorpora en la tabla de categorías la acción 'Editar', abriendo el formulario de modificación en línea con precarga de nombre (máximo 100 caracteres) y descripción opcional (máximo 500 caracteres), consumiendo el endpoint PUT /api/v1/categories/{id} vía categoryApi.update. El estado de edición se descarta y reinicia limpiamente ante cambios de organización, cancelación o finalización exitosa. Verificadas suites unitarias en Python (3 pruebas de servicio y 7 de evaluador multihorizonte aprobadas). Sin cambios en contratos ni en el modelo de base de datos. Aceptación y cierre manuales.


### Gráfico combinado histórico + previsión #88 (P5-H), 25/10/2026

Sobre 9f40768, se incorpora en rontend/src/PredictionPanel.tsx el componente SVG accesible PredictionCombinedChart.tsx, que consulta el historial reciente de hasta seis meses confirmados previos al corte y los conecta en una escala continua con las proyecciones multimes (4 a 6 meses). Se diferencian visualmente las barras históricas (azul continuo) de las proyecciones estimadas (verde/turquesa con trazo punteado e indicador de horizonte h). Incluye roles accesibles, etiquetas descriptivas y navegación. Pruebas de servicio Python (3 aprobadas) y de evaluación multihorizonte (7 aprobadas) verificadas. Sin alteraciones a contratos ni bases de datos. Aceptación y cierre manuales.


### Consolidación documental #91, 25/10/2026

Base inspeccionada 1486354: PR #87 predictor/avisos, #89 gráfico y #90 justificación integrados. Síntesis vigente unificada y antecedentes identificados; sin cambios de estados del tablero, cierres, criterios de aceptación, API, migraciones ni código. Se revisan enlaces y coherencia estática; no se atribuye ejecución nueva de suites a esta entrega.
