# Python: analítica y predicción

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

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

E6.1 incorpora un candidato SES (`alpha=0.3`, seis meses mínimos, versión 1) y herramientas offline de evaluación. El modo operativo predeterminado continúa siendo `mock`; `PREDICTOR_MODE=ses` es una opción explícita experimental, transmitida por Compose desde `.env`, para comprobar el candidato. En este modo `simulated:false` identifica el cálculo SES, sin certificar calidad predictiva ni aceptación de E6.1. No se activa automáticamente por obtener métricas ni por pasar pruebas. Dataset temporal de aceptación, métrica de decisión, umbral y regla de selección siguen abiertos y deben definirse antes de evaluar para aceptación. La UI predictiva E6.2 sigue pendiente.

Desde `service-python`, el informe reproducible de regresión sintética se obtiene con:

```sh
uv run --locked python -m app.predictors.evaluation tests/fixtures/prediction_synthetic.json
```

El informe identifica SHA-256 del archivo, 18 meses sintéticos, seis meses iniciales, 12 orígenes temporales, `alpha=0.3` y `accepted:null`. Cada objetivo recibe únicamente meses anteriores; no hay ajuste con meses futuros. Compara SES, último mes y media histórica sobre los mismos orígenes. El CLI recibe valores mensuales en centavos; la fuente debe garantizar previamente meses consecutivos y cobertura confiable, porque una lista numérica no acredita por sí sola esas condiciones. El fixture no es representativo ni reemplaza el dataset de aceptación.

Métricas internas con unidades explícitas: `mae_ars` y `rmse_ars` convierten centavos a ARS; `mape_percentage` usa sólo meses reales positivos y declara `mape_samples`; `wape_percentage` suma todos los errores absolutos, incluidos meses reales cero, y divide por la suma de importes reales. Si la suma real es cero, WAPE es null: se conserva MAE/RMSE y no se inventa error relativo cero. Redondeo del informe a dos decimales con ROUND_HALF_UP. La media histórica sólo utiliza la ventana anterior y redondea su estimación a centavos con la misma regla. Estos campos pertenecen al informe offline y no cambian los DTO HTTP.

Resultado del fixture sintético: MAE SES 2460,04 ARS, último mes 1075,00 ARS y media histórica 4666,56 ARS. SES pierde ante último mes en esta serie; no se concluye superioridad general ni se promueve un método operativo. Los resultados no fijan retroactivamente un umbral de aceptación.

Mínimo del contrato actual: seis meses válidos por serie. Insuficiencia devuelve estimación null y explica meses requeridos/utilizados. Categorías con historia insuficiente pueden producir estado PARTIAL; no rellenar con un valor inventado.

Implementar suavizado exponencial simple y compararlo con último mes y media histórica como referencias. Elegir parámetros usando únicamente datos anteriores a cada fecha de evaluación. Backtesting con ventana temporal progresiva; reportar MAE en ARS y una medida relativa robusta a ceros cuando sea aplicable. No dividir aleatoriamente meses ni usar el futuro en entrenamiento.

Umbral cuantitativo de aceptación y dataset deben fijarse antes de evaluar. No se exige que un modelo complejo reemplace una referencia que funciona mejor. Mostrar método, versión y simulated=false solo cuando la implementación real esté validada. Predicción del siguiente mes y proyección del cierre del actual son capacidades diferentes; esta última no entra automáticamente.

## Pruebas y operación

Conservar pruebas del contrato actual mientras se migra. Cubrir tipos estrictos, campos desconocidos, meses duplicados/discontinuos, recorte, categorías nuevas/inactivas, cero frente a falta de datos, seis meses, límites de enteros y totales. Integración real de vistas y permisos con dos organizaciones; caída SQL, pool y limpieza de contexto entre conexiones. Evaluación reproducible del modelo y circuito Java→Python→PostgreSQL. Logs sin datos de comprobantes ni credenciales.

El POST histórico con historial puede servir para pruebas durante transición; no debe confundirse con el contrato operativo nuevo ni quedar expuesto sin autenticación. Retirar la dependencia inversa Python→Java al completar migración.
