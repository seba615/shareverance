# Python: analítica y predicción

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado

FastAPI en `service-python/app/`. Existen esquemas estrictos, validación de historial, endpoints predictivos y predictor simulado. El GET organizacional obtiene actualmente el historial mediante Java. No hay acceso de Python a PostgreSQL, predictor real evaluado ni router analítico mensual completo en la base revisada.

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

Mínimo del contrato actual: seis meses válidos por serie. Insuficiencia devuelve estimación null y explica meses requeridos/utilizados. Categorías con historia insuficiente pueden producir estado PARTIAL; no rellenar con un valor inventado.

Implementar suavizado exponencial simple y compararlo con último mes y media histórica como referencias. Elegir parámetros usando únicamente datos anteriores a cada fecha de evaluación. Backtesting con ventana temporal progresiva; reportar MAE en ARS y una medida relativa robusta a ceros cuando sea aplicable. No dividir aleatoriamente meses ni usar el futuro en entrenamiento.

Umbral cuantitativo de aceptación y dataset deben fijarse antes de evaluar. No se exige que un modelo complejo reemplace una referencia que funciona mejor. Mostrar método, versión y simulated=false solo cuando la implementación real esté validada. Predicción del siguiente mes y proyección del cierre del actual son capacidades diferentes; esta última no entra automáticamente.

## Pruebas y operación

Conservar pruebas del contrato actual mientras se migra. Cubrir tipos estrictos, campos desconocidos, meses duplicados/discontinuos, recorte, categorías nuevas/inactivas, cero frente a falta de datos, seis meses, límites de enteros y totales. Integración real de vistas y permisos con dos organizaciones; caída SQL, pool y limpieza de contexto entre conexiones. Evaluación reproducible del modelo y circuito Java→Python→PostgreSQL. Logs sin datos de comprobantes ni credenciales.

El POST histórico con historial puede servir para pruebas durante transición; no debe confundirse con el contrato operativo nuevo ni quedar expuesto sin autenticación. Retirar la dependencia inversa Python→Java al completar migración.
