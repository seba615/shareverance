# Backend Java

Fecha de revisión: 25/10/2026. Base inspeccionada: `1486354` (main; PR #87, #89 y #90 integrados).

Revisión documental estática: distingue implementación observada, evidencia de ejecuciones anteriores y aceptación pendiente. No se ejecutaron pruebas funcionales nuevas en esta consolidación.

## Síntesis vigente

Java implementa las consultas públicas monthly y forecast (4/5/6 meses), analítica mensual, cobertura, comprobantes y OCR; autoriza sesión y pertenencia antes de llamar a Python. Forecast conserva monthly y exige GET con membresía activa. La carga CSV de la demo usa APIs Java en PostgreSQL aislado y es repetible. No se planifican esas rutas como trabajo futuro; aceptación integral y parámetros operativos siguen sujetos a revisión.


## Responsabilidad y código actual

Spring Boot con JDBC, sin JPA. Código en `backend-java/src/main/java/com/shareverance/`. Controladores de autenticación, usuarios, organizaciones, membresías, categorías y gastos; repositorios JDBC; migraciones Flyway. Las APIs existentes no equivalen a cumplimiento integral de permisos y validaciones.

Sesión web mediante cookie; frontend obtiene CSRF antes de mutaciones. E3.2 retiró el callback Python→Java y sus rutas técnicas de token/historial. Java autoriza y solicita predicción a Python con Bearer interno; Python consulta vistas SQL con credencial propia de lectura.

Implementación E1.1 integrada mediante PR #25, commit `7733612`: la autorización de rutas exige cuenta, membresía y organización activas; las organizaciones con moneda distinta de ARS no son operables. Java también rechaza moneda no ARS y estados inactivos al crear organizaciones, miembros, categorías y gastos. Esto no certifica aislamiento integral ni aceptación con PostgreSQL.

## Reglas y permisos

E3.1 añade `AnalyticsCoverageController` y `AnalyticsCoverageService` para listar, confirmar o retirar cobertura de meses completos, siempre como ADMIN activo de la organización. La identidad proviene de la sesión; no se acepta un autor en el cuerpo. Las escrituras JDBC son transaccionales, bloquean la organización antes de comprobar ADMIN y conservan metadatos al retirar una confirmación. No hay JPA ni interfaz de usuario nueva. Rutas y respuestas en [contratos_api.md](contratos_api.md).

En Compose, Java usa `shareverance_java` para operaciones de aplicación y una conexión Flyway separada con `SPRING_FLYWAY_URL/USER/PASSWORD`. El servicio previo `database-access` aprovisiona los logins; V7 aplica permisos y crea vistas/cobertura. Fuera de Compose deben configurarse explícitamente ambas credenciales. La cuenta de ejecución no puede crear tablas ni consultar el historial Flyway. E3.2 implementa la consulta predictiva Java→Python autenticada y retira el callback. E5.2 incorpora la consulta pública de analítica mensual; Python obtiene los datos desde vistas SQL.

Aplicar [alcance_mvp.md](alcance_mvp.md) en servidor, sin confiar en IDs o roles enviados por React. Resolver usuario desde sesión y verificar organización activa, membresía activa y operación permitida.

| Operación | USER | ADMIN de la organización |
| --- | --- | --- |
| Consultar gastos, calendario y panel | Sí, dentro de su organización | Sí |
| Alta a su nombre | Sí | Sí |
| Alta a nombre de otro miembro | No | Sí |
| Editar/anular gasto | Si es autor | Sí |
| Administrar miembros, categorías y configuración | No | Sí |
| Leer comprobante | Si puede consultar el gasto o es propietario de la carga temporal | Mismo ámbito autorizado |

No exponer listados globales de usuarios. Incorporación por email exacto requiere validar ADMIN y devolver datos mínimos. Comprobar último ADMIN también ante solicitudes simultáneas, con bloqueo/transacción que impida desactivar ambos.

## Persistencia y concurrencia

Validar fecha, moneda ARS, descripción, precisión y límites; pertenencia de titular, categoría y participantes. Alta de gasto y partes en una transacción. Edición conserva composición, recalcula reparto y actualiza versión condicionalmente. Si la versión enviada no coincide, devolver 409 y no aplicar cambios parciales. Anulación es lógica, sin eliminación física.

No modificar migraciones aplicadas. Las restricciones existentes de PostgreSQL complementan las validaciones; la suma de partes requiere control transaccional explícito.

## Orquestación

- OCR: multipart del navegador, validación y almacenamiento privado; llamada HTTP interna con referencia controlada al archivo; respuesta al formulario. El alta se produce después de confirmación.
- Analítica/predicción: validar pertenencia, construir contexto organización/período y llamar Python; Python obtiene datos de vistas PostgreSQL. No reenviar un historial como arquitectura definitiva.
- Python caído no bloquea alta manual ni consulta de gastos. Separar timeout de conexión y lectura por operación; no reintentar altas sin idempotencia.
- Contratos en [contratos_api.md](contratos_api.md). Las rutas implementadas y la referencia histórica se identifican por separado. La previsión forecast de 4/5/6 meses está implementada de forma aditiva respecto de monthly.

## Archivos, errores y operación

Nombre generado por servidor; nunca ruta absoluta del cliente. Registrar asociación organización/propietario/archivo para autorizar temporales. Validar tipo por contenido, tamaño y dimensiones. Al asociar a gasto, verificar misma organización y propiedad/autorización. Descarga mediante Java con permiso, sin servir directamente una carpeta pública.

Mensajes de error controlados y request_id. Errores inesperados no exponen SQL ni stack traces. Credencial de aplicación separada de migraciones; secretos por entorno fuera de Git.

## Pruebas requeridas

Pruebas de reglas con casos límite; integración JDBC con PostgreSQL real para rollback, restricciones, reparto y concurrencia; autorización cruzada en todas las rutas; alta por ADMIN frente a USER; CSRF, logout, expiración y cuentas inactivas; dos ediciones con igual versión; caída/timeout de Python; archivos inválidos y lectura entre organizaciones. Nunca ejecutar pruebas destructivas sobre la base de desarrollo.

## Brechas

Pendiente: aceptación integral de permisos, historial, concurrencia y archivos, conservando la evidencia automatizada registrada en el roadmap. Los contratos públicos analíticos, el cliente Java→Python autenticado y el servicio privado de comprobantes están implementados. Para la base local con registros previos a la regla ARS, se acordó una eliminación única de toda la base antes de iniciar esta versión; no se migran ni convierten esos datos. Los reinicios ordinarios conservan los datos en el volumen. E1.2 admite individual/iguales y rechaza CUSTOM; la presencia del enum no habilita ese modo. La carga CSV repetible de la demo y la previsión de 4/5/6 meses están implementadas; su aceptación integral sigue pendiente.

## Implementación E1.2: individual e iguales

Alta y edición validan importe positivo hasta 9.999.999.999,99 ARS, con hasta dos decimales sin redondeo silencioso. El reparto igualitario usa centavos y asigna sobrantes por ID de membresía ascendente; rechaza IDs nulos o repetidos, selección inválida y partes cero. El modo individual asigna todo al titular. Las membresías y cuentas deben estar activas para participar en altas; ALL_MEMBERS excluye las inactivas.

Editar conserva los participantes históricos aunque luego se inactiven y no agrega miembros nuevos. Recalcula mediante el mismo algoritmo determinista; rechaza repartos vacíos o importes insuficientes. Se conservan las transacciones Spring de alta y edición y los repositorios JDBC; no cambia el esquema ni se incorpora versión optimista (E1.3).

CUSTOM permanece como código preexistente del backend, fuera del alcance documentado del MVP y sin aceptación en esta entrega. No se agrega soporte, persistencia diferenciada ni recálculo proporcional para esa modalidad. Su presencia sigue siendo una brecha respecto del alcance; no se retira unilateralmente un modo del DTO compartido.

## Implementación E1.3: versión optimista y último ADMIN

Edición de gastos valida concurrencia mediante versión optimista. `expenses` cuenta con columna `version` (V6 Flyway, por defecto 1). La versión debe ser positiva y obligatoria en PUT (400 si falta o no es positiva). Si la versión esperada provista en la solicitud no coincide con la almacenada, se interrumpe la transacción y se retorna HTTP 409 Conflict sin aplicar cambios parciales. El incremento de versión se ejecuta de forma condicional y atómica en el repositorio.

Desactivación y revocación de rol ADMIN (`updateMemberRole` y `deactivateMembership`) se ejecutan transaccionalmente con bloqueo pesimista común (`FOR UPDATE`) sobre la organización y después sobre la membresía consultada y validan que la organización activa no quede sin al menos un ADMIN activo, impidiendo estados inconsistentes ante peticiones concurrentes.

### Corrección E1.3

La validación y actualización condicional del gasto ocurren antes de escribir sus partes; un fallo posterior revierte ambos. No se permite el fallback a `save` cuando falta versión. Anular usa una actualización de estado y versión, sin volver a persistir los demás campos de la copia leída. En cambios de rol/desactivación de membresías, el orden de bloqueo es organización → membresía → conteo → escritura: dos ADMIN distintos no pueden superar simultáneamente el conteo. Se mantiene JDBC, sin reorganizar servicios ni modificar migraciones.

La aceptación integral continúa pendiente. Las nuevas pruebas usan servicios Spring transaccionales y repositorios JDBC reales, con dos conexiones y sincronización en puntos concretos. La prueba preexistente del último ADMIN ahora obtiene el servicio del contexto Spring en lugar de instanciarlo con new, de modo que se aplica `@Transactional`.

## Miembros E2.1

`MembershipRequest` añade email opcional y conserva userId por compatibilidad. Si email no está vacío se usa su valor sin espacios exteriores para buscar la cuenta existente; la comparación JDBC es exacta sin distinguir mayúsculas/minúsculas, no una búsqueda parcial ni un directorio. Si se envían email y userId, prevalece email. Sin ambos se devuelve 404. ADMIN de la organización es requisito para el alta; usuario inexistente devuelve 404, cuenta inactiva y membresía activa duplicada devuelven 400.

Alta de membresía nueva: rol solicitado o USER por defecto. Reincorporación mediante POST de una membresía inactiva: conserva ID y joinedAt, activa la membresía y usa el rol solicitado si existe; de lo contrario mantiene el anterior. La nueva ruta PATCH /api/v1/memberships/{id}/reactivate conserva el rol, requiere ADMIN de la misma organización y cuenta activa, y bloquea organización y membresía en ese orden. No se agregan migraciones ni se modifican los repartos históricos.

Las respuestas de membresías incluyen userName y userEmail como datos de presentación de la cuenta vinculada. Las consultas con JOIN los completan; las respuestas de operaciones que usan consultas sin JOIN pueden devolverlos como null. No contienen hashes ni credenciales. La UI recarga los datos autorizados de la organización al terminar las operaciones. Contrato detallado en contratos_api.md. La aceptación de concurrencia en la reincorporación mediante POST no queda acreditada por el build o por las pruebas del cargador de frontend.

## Historial E2.2

Se conserva el listado anterior para compatibilidad y se añade consulta paginada mediante ExpenseFilter/ExpensePageResponse. Validación del período, categoría perteneciente a la organización, estado y límites de página; errores de conversión de parámetros devuelven 400. El repositorio aplica filtros y límites SQL parametrizados y orden expense_date DESC/id DESC. La transacción REPEATABLE_READ de sólo lectura mantiene coherencia entre count/sum, filas y partes de cada respuesta. No se introduce JPA ni se modifica el contrato mensual Python.

La UI usa los servicios existentes de detalle, edición optimista y anulación atómica de E1.3. Alta/edición validan ahora explícitamente descripción no vacía hasta 500 caracteres y fecha usando America/Argentina/Buenos_Aires. Se conserva la composición histórica y se permiten categorías inactivas sólo si ya eran la categoría del gasto. Sin migraciones nuevas. Pruebas E2.2 en ExpenseHistoryPostgresTest, incluidas solicitudes HTTP con filtro real de autenticación y sesión de usuario/administrador.

## Calendario E2.3

Se añade `GET /api/v1/expenses/organization/{organizationId}/calendar?month=YYYY-MM` protegido por membresía activa en la organización activa (ARS). Si `month` se omite o está en blanco, se utiliza el mes en curso según `America/Argentina/Buenos_Aires`. Se rechazan con HTTP 400 los formatos inválidos y los meses posteriores al mes en curso en Buenos Aires. La consulta opera bajo `REPEATABLE_READ` de sólo lectura y agrupa directamente `expenses` (`is_active = TRUE`) por `expense_date` en orden ascendente sin unir `expense_splits`, devolviendo `ExpenseCalendarResponse` (`month`, `totalAmount`, `totalExpenses`, `days`). El listado de gastos del día seleccionado y las operaciones de detalle, edición optimista y anulación reutilizan las rutas de E2.2 y E1.3.

## Orquestación predictiva y retiro de callback E3.2

Se incorporan `PredictionController` y `PredictionService` para exponer `GET /api/v1/organizations/{organizationId}/predictions/monthly?targetMonth=YYYY-MM` a cualquier miembro activo (`USER` o `ADMIN`) de una organización ARS activa. Si `targetMonth` se omite, se toma el mes siguiente al último mes confirmado en `analytics_month_coverage` para esa organización (o el mes en curso en `America/Argentina/Buenos_Aires` cuando aún no existen meses confirmados). Java invoca `POST /internal/v2/predictions/monthly` en Python autenticándose mediante `Authorization: Bearer <PYTHON_CLIENT_SECRET>` y aplicando timeouts explícitos de conexión (`python.service.connect-timeout-ms`, 2000 ms por defecto) y lectura (`python.service.read-timeout-ms`, 5000 ms por defecto). Si Python no está disponible, agota el tiempo de espera o devuelve un estado de error, Java responde HTTP 503 (`SERVICE_UNAVAILABLE`) sin exponer detalles internos y sin afectar la carga o consulta de gastos.

Quedan retirados de Java el endpoint OAuth de callback `POST /api/v1/auth/token`, el endpoint `GET /api/v1/expenses/organization/{organizationId}/history` y los DTOs asociados al circuito inverso `Python → Java`.

## Consulta analítica pública E5.2

`AnalyticsController` y `AnalyticsService` exponen `GET /api/v1/organizations/{organizationId}/analytics/monthly?fromMonth=YYYY-MM&toMonth=YYYY-MM`. El filtro de sesión y el servicio exigen usuario, membresía y organización ARS activos; USER y ADMIN pueden consultar, pero las rutas de cobertura siguen reservadas a ADMIN. Sólo se permite GET. La identidad de usuario procede de la sesión y la organización autorizada se envía a Python; no se acepta un historial del navegador.

La ventana predeterminada abarca seis meses hasta el último mes cerrado de Buenos Aires. Parámetros explícitos deben ser estrictos YYYY-MM, años 0001–9999, con inicio no posterior al fin, máximo 60 meses inclusivos y fin anterior al mes actual. Si sólo falta el inicio se calculan hasta seis meses terminados en el fin solicitado, sin descender de 0001-01; si falta el fin se usa el último cerrado. Parámetros vacíos son inválidos. Se devuelve 400 antes de llamar Python si el período no es válido.

Java llama `POST /internal/v2/analytics/monthly`, contrato 2, con UUID nuevo, contexto y Bearer interno. Reutiliza la configuración de conexión 2000 ms/lectura 5000 ms; no mantiene una transacción JDBC durante la llamada. Error, timeout, secreto ausente o respuesta incompatible producen 503 con mensaje controlado. Valida identidad de organización/solicitud, moneda ARS, resultado no simulado, ventana ordenada, cobertura, centavos enteros seguros, categorías sin duplicar y conciliación de totales. Devuelve DTOs explícitos, sin reenviar credenciales ni errores internos. No cambia Python, vistas, migraciones ni las rutas anteriores.

## Volumen privado y gestión de comprobantes E4.1

Se incorpora la infraestructura y los endpoints para el almacenamiento privado y seguro de comprobantes asociados a organizaciones:
- Volumen persistente compartido `receipts_data` montado en `/data/receipts` (lectura/escritura en Java, sólo lectura en Python).
- Tabla `receipts` (migración Flyway V8) con identificador UUID, organización, usuario que subió el archivo, ruta relativa en disco, nombre original, tipo MIME, tamaño en bytes, vínculo opcional a gasto (`expense_id`) y estado activo. Permisos SQL otorgados a `shareverance_java` y revocados para `shareverance_python`.
- `ReceiptController`, `ReceiptService` y `ReceiptRepository`:
  - `POST /api/v1/organizations/{organizationId}/receipts`: subida multipart de archivos JPEG o PNG. Valida tamaño (hasta 10 MiB, HTTP 413 si supera), tipo por magic bytes (HTTP 415 si no es JPEG/PNG) y decodificación real con límite de 50 megapíxeles (HTTP 400 si está corrupto o excede dimensiones). Almacena en `/data/receipts/org_<id>/<uuid>.<ext>` previniendo path traversal y devuelve `201 CREATED` (`ReceiptUploadResponse`).
  - `GET /api/v1/organizations/{organizationId}/receipts/{receiptId}`: descarga autorizada en streaming (`ResponseEntity<Resource>`) con `Content-Type` detectado y `Content-Disposition: inline`. Un comprobante temporal no vinculado a un gasto solo es accesible por el usuario que lo subió o por un `ADMIN` de la organización (HTTP 403 si otro miembro intenta acceder); una vez vinculado a un gasto, cualquier miembro activo de la organización puede descargarlo. Rechaza accesos desde otras organizaciones con HTTP 403/404.
  - `GET /api/v1/organizations/{organizationId}/receipts/{receiptId}/metadata`: metadatos del comprobante.
  - `GET /api/v1/organizations/{organizationId}/receipts`: listado de comprobantes activos visibles para el usuario autenticado.
- `AuthServiceImpl`: autorización de rutas `/api/v1/organizations/{id}/receipts/**` para miembros activos de la organización.

Correcciones de aislamiento E4.1: V9 añade la FK `(organization_id, expense_id)` hacia `expenses` sin modificar V8. La asociación interna exige membresía activa, gasto activo de la misma organización y permiso del propietario de la carga o ADMIN; un UPDATE condicional impide sobrescribir otra asociación concurrente. E4.2 la integra al alta de gastos; la edición no cambia asociaciones y no hay una ruta pública separada de vinculación.

La carga limita los bytes realmente leídos, consulta dimensiones con `ImageReader` antes de decodificar y valida el nombre original (hasta 255 caracteres, sin controles) antes de escribir. Se rechazan enlaces simbólicos en los componentes del almacenamiento y se vuelve a validar al abrir la descarga, con `NOFOLLOW_LINKS` sobre el archivo final. El volumen debe permanecer bajo control de Java; Python conserva sólo lectura.

El archivo UUID creado por una carga se elimina si falla el guardado SQL o si la transacción termina en rollback. Esto es compensación entre disco y SQL, no una transacción distribuida: una caída del proceso o un fallo al eliminar requiere revisión operativa. Los fallos de limpieza se registran sin exponer rutas al cliente; no se habilita una purga automática en el proceso web. La purga y retención de comprobantes temporales y huérfanos se gestiona de forma desacoplada y auditable mediante las herramientas operativas `scripts/cleanup_receipts.py` (`scripts/cleanup-receipts.sh` / `scripts/cleanup-receipts.ps1`), con aplicación sólo en mantenimiento: Java y Python detenidos, escritores externos cerrados y bloqueo SQL de receipts durante la operación. No hay atomicidad distribuida entre SQL y archivos; un fallo requiere revisión antes de reiniciar. Procedimiento en [OCR](python_ocr.md#limpieza-operativa). `Content-Disposition` se construye con codificación UTF-8 del nombre.

## Invocación OCR y vinculación de comprobantes E4.2

Se incorporan `OcrService` y su implementación `OcrServiceImpl`:
- Endpoint público `POST /api/v1/organizations/{organizationId}/receipts/{receiptId}/ocr`:
  - El autorizador de rutas admite exclusivamente POST OCR para miembros activos. Luego `OcrService` comprueba organización, comprobante y permisos de propietario/ADMIN; reconocer la ruta no sustituye esas comprobaciones. Sesión por cookie exige CSRF en esta mutación.
  - Requiere membresía activa en la organización y que el usuario sea el propietario del comprobante o `ADMIN` de la organización (si el comprobante ya está vinculado a un gasto, cualquier miembro activo puede procesarlo).
  - Invoca internamente a Python en `POST /internal/v1/ocr` enviando cabecera `Authorization: Bearer <PYTHON_CLIENT_SECRET>` y cuerpo JSON con `storage_key` (`org_<id>/<uuid>.<ext>`).
  - Aplica timeout de lectura configurable (`python.service.ocr-read-timeout-ms`, por defecto 15.000 ms). Si Python no está disponible, agota el tiempo de espera o retorna error, Java responde HTTP 503 (`SERVICE_UNAVAILABLE`) sin comprometer el alta ni consulta de gastos.
- Vinculación en `createExpense`:
  - Si `request.receiptUrl()` es un UUID canónico (o su último segmento es un UUID), `ExpenseServiceImpl` invoca `receiptService.linkReceiptToExpense` dentro de la transacción de gasto y partes. Referencias anteriores sin UUID se conservan como texto sin intentar vincularlas. Un UUID inexistente, inactivo o de otra organización devuelve 404; falta de permiso devuelve 403 y una asociación previa incompatible devuelve 409. Esos errores se propagan sin capturarlos y revierten toda el alta, conservando su estado HTTP en lugar de terminar en `UnexpectedRollbackException`. Una falla SQL también revierte la operación completa.


## Validación de la demostración multihorizonte

Relacionado con #85/#81. El autorizador admite sólo GET en predictions/forecast para miembros activos, usando las mismas comprobaciones de pertenencia de monthly. No habilita POST ni analytics/forecast. La [prueba integrada sintética](evidencia/demo-sintetica/README.md) comprueba horizontes 4/5/6, equivalencia mensual, insuficiencia y 400/403/503; no equivale a validación exhaustiva de respuestas internas malformadas ni aceptación integral. Sin nuevas rutas, DTO o migraciones en esta corrección.
