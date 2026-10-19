# Contratos de integración

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Principios

React consume exclusivamente Java. Java autoriza usuario/organización y llama a Python internamente. Python consulta vistas PostgreSQL o lee la imagen del volumen según operación. Nuevos contratos descritos abajo son diseño pendiente de implementación. No reutilizar silenciosamente contract_version=1 para un cuerpo incompatible.

## Superficie existente

| Servicio | Rutas observadas |
| --- | --- |
| Java, sesión | GET /api/v1/auth/csrf; POST /login, /register, /logout y GET /me bajo /api/v1/auth |
| Java, negocio | /api/v1/users, /organizations, /memberships, /categories, /expenses; métodos según controladores |
| Java, cobertura y predicción | GET/PUT/DELETE /api/v1/organizations/{organizationId}/analytics/coverage; GET /api/v1/organizations/{organizationId}/predictions/monthly |
| Python, interno y contrato | POST /internal/v2/predictions/monthly (Bearer interno, lee vistas SQL); POST /api/v1/predictions/monthly (contrato v1 con historial en cuerpo para pruebas) |
| Salud | Python /health; Java /actuator/health y readiness |

Un controlador presente no acredita autorización uniforme. Revisar DTO y controladores antes de modificar consumidores. En E3.2 se retiran el callback `GET /api/v1/predictions/organization/{organization_id}/monthly` de Python y los endpoints `/api/v1/auth/token` y `/api/v1/expenses/organization/{organizationId}/history` de Java.

## Miembros y categorías E2.1

React sigue consumiendo únicamente Java, con sesión same-origin y CSRF en mutaciones. Los permisos efectivos los aplica Java: las lecturas requieren pertenencia autorizada a la organización; altas, roles, desactivaciones y reactivaciones requieren ADMIN. Se rechazan operaciones sobre otra organización o cuentas/organizaciones inactivas según el circuito existente.

| Operación | Ruta y datos | Respuesta |
| --- | --- | --- |
| Listar miembros | GET /api/v1/memberships/organization/{organizationId} | 200, lista de membresías activas e inactivas |
| Incorporar/reincorporar | POST /api/v1/memberships; organizationId, email o userId, role opcional | 201, membresía nueva o reincorporada |
| Cambiar rol | PATCH /api/v1/memberships/{id}/role?role=ADMIN o USER | 200, membresía; protección del último ADMIN |
| Desactivar miembro | DELETE /api/v1/memberships/{id} | 200, membresía inactiva; conserva historial |
| Reactivar miembro | PATCH /api/v1/memberships/{id}/reactivate; sin cuerpo | 200, membresía activa; conserva rol e ID |
| Listar categorías | GET /api/v1/categories/organization/{organizationId} | 200, categorías activas e inactivas |
| Crear categoría | POST /api/v1/categories/organization/{organizationId}; name, description/color opcionales | 201, categoría |
| Editar categoría | PUT /api/v1/categories/{id}; CategoryRequest | 200, categoría; API existente, UI pendiente |
| Desactivar categoría | DELETE /api/v1/categories/{id} | 200, categoría inactiva; conserva asociación histórica |

Ejemplo de incorporación:

```json
{"organizationId":1,"email":"persona@example.invalid","role":"USER"}
```

El email se recorta y compara exactamente sin distinguir mayúsculas/minúsculas. El userId anterior sigue admitido; si llegan ambos identificadores prevalece email. Si faltan ambos o no existe la cuenta, el servicio devuelve 404. Cuenta inactiva o membresía activa duplicada: 400. Sin sesión: 401; sin permiso: 403. Los formatos de error existentes no se modifican.

Si existe una membresía inactiva, POST la reactiva con el mismo ID y joinedAt; actualiza el rol si se envió, o conserva el previo. Para una membresía nueva, role omitido equivale a USER. PATCH de reactivación conserva el rol y valida que la cuenta esté activa. Ninguna de estas operaciones recrea repartos ni reactiva gastos anulados.

Respuesta de membresía: id, userId, organizationId, role, joinedAt, isActive y los campos aditivos userName/userEmail (nullable si la consulta de la operación no los completa). Las lecturas con JOIN incluyen nombre y correo; los consumidores deben aceptar null y pueden recargar el listado. No se exponen credenciales. No se retiran campos anteriores.

E2.1 utiliza POST /api/v1/expenses para alta y GET del listado, sin añadir edición en React. El cliente envía amount como cadena decimal y las validaciones monetarias finales permanecen en Java. La edición futura debe respetar la versión obligatoria de E1.3. No se incorporan CUSTOM, calendario ni analítica a esta entrega.

## Gastos E1.2: individual e iguales

Se conservan POST /api/v1/expenses, PUT /api/v1/expenses/{id} y sus DTO. E1.2 cubre INDIVIDUAL, BETWEEN_TWO, SPECIFIC_GROUP y ALL_MEMBERS. INDIVIDUAL asigna todo al titular; BETWEEN_TWO requiere dos participantes distintos contando al titular; SPECIFIC_GROUP recibe una selección no vacía; el servidor agrega al titular si no está incluido. No se permiten IDs repetidos o nulos. ALL_MEMBERS selecciona membresías y cuentas activas de la organización.

Total positivo hasta 9.999.999.999,99, con hasta dos decimales; los repartos deben permitir al menos un centavo por participante. El servidor calcula partes iguales y sobrantes por ID ascendente. PUT conserva participantes históricos y aplica el mismo cálculo. Reglas inválidas devuelven HTTP 400 sin guardar cambios parciales. CUSTOM sigue presente en el DTO preexistente, fuera del alcance y de la aceptación de E1.2; no se declara soporte nuevo para esa modalidad. Reglas normativas en [alcance_mvp.md](alcance_mvp.md).

## Historial E2.2: consulta paginada y detalle

Se añade GET `/api/v1/expenses/organization/{organizationId}/page`. No cambia el GET de listado anterior ni `/history`, que conserva el contrato mensual de Python. Se requiere sesión de un miembro activo de la organización; la autorización de edición/anulación sigue limitada al autor o ADMIN.

| Parámetro | Semántica |
| --- | --- |
| fromDate / toDate | Fechas ISO YYYY-MM-DD opcionales e inclusivas; inicio no posterior al fin |
| categoryId | Categoría de la misma organización; se admiten categorías históricas inactivas |
| state | ACTIVE (predeterminado), ANNULLED o ALL |
| page | Índice desde 0, no negativo; predeterminado 0 |
| size | De 1 a 100; predeterminado 20 |

Orden fijo: expenseDate descendente e ID descendente para desempatar. Respuesta 200: `items` (detalles con expense y splits), `page`, `size`, `totalElements`, `totalPages` y `totalAmount` (ARS). Los totales corresponden a todos los resultados de los filtros, no sólo a la página; no se duplica el importe por unir partes. Una página fuera del rango devuelve items vacíos conservando los totales; sin coincidencias, totalPages es 0. La UI vuelve a una página válida si una anulación reduce el número de páginas.

Los parámetros inválidos devuelven 400, la falta de sesión 401 y otra organización 403. Java consulta totales, filas y partes bajo una transacción de sólo lectura REPEATABLE_READ: cada respuesta utiliza un único snapshot. La paginación usa offset; no se mantiene un snapshot entre solicitudes distintas, por lo que cambios externos entre páginas requieren recargar la consulta. Con datos sin cambios, el desempate por ID evita orden indeterminado y duplicados de navegación.

GET `/api/v1/expenses/{id}` conserva su respuesta de detalle y permite consultar anulados del ámbito autorizado. PUT conserva la versión positiva obligatoria de E1.3; no acepta cambio de titular ni de participantes. La categoría actual puede mantenerse si quedó inactiva, pero no puede elegirse otra categoría inactiva. Descripción no vacía de hasta 500 caracteres; fecha no futura según Buenos Aires; las validaciones monetarias se mantienen. DELETE conserva el contrato de E1.3 y no incorpora reactivación. La UI exige confirmación antes de anular y, ante 409, ofrece recarga explícita sin reintentar ni sustituir automáticamente la versión local.

## Calendario E2.3: resumen mensual y consulta por fecha

Se añade `GET /api/v1/expenses/organization/{organizationId}/calendar?month=YYYY-MM`, protegido por sesión activa y membresía activa en la organización.

| Parámetro | Semántica |
| --- | --- |
| month | Mes ISO `YYYY-MM` opcional; si se omite se usa el mes actual en `America/Argentina/Buenos_Aires`. No se admiten meses futuros |

Respuesta 200 (`ExpenseCalendarResponse`):
- `month`: cadena `YYYY-MM` consultada.
- `totalAmount`: suma total en ARS de los gastos vigentes (`is_active = TRUE`) del mes.
- `totalExpenses`: cantidad total de gastos vigentes del mes.
- `days`: lista ordenada ascendentemente por fecha con los días del mes que registran gastos vigentes (`date`, `totalAmount`, `expenseCount`). Los días sin gastos vigentes no se incluyen en el arreglo `days` (el frontend los representa con total `0`).

Formato de mes inválido o mes futuro devuelve 400; sin sesión devuelve 401; usuario sin membresía activa en la organización devuelve 403. Para listar los gastos vigentes de una fecha seleccionada en el calendario, React consume `GET /api/v1/expenses/organization/{organizationId}/page?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD&state=ACTIVE&page=0&size=20` y reutiliza `GET / PUT / DELETE /api/v1/expenses/{id}` para detalle, edición con versión optimista y anulación.

## Gastos E1.3: versión y concurrencia

`GET /api/v1/expenses/{id}` y el listado devuelven `expense.version` dentro del detalle. Cada gasto nuevo comienza en 1. `PUT /api/v1/expenses/{id}` requiere `version` como entero positivo, con el valor obtenido en la última consulta. Omitirlo, enviar null, cero o negativo devuelve HTTP 400. No existe guardado alternativo sin control optimista. Una versión desactualizada, incluso por una anulación concurrente, devuelve 409 sin cambios parciales. Un PUT exitoso devuelve el detalle con la versión incrementada.

Ejemplo de edición (el resto de campos conserva su semántica existente):

```json
{"amount":1250.50,"description":"Servicios","version":1}
```

Java verifica `WHERE id = ? AND version = ? AND is_active = TRUE` antes de guardar partes; toda la operación sigue en una transacción. Ante 409 el consumidor debe recargar el detalle y resolver el conflicto, sin reintentar con una versión nueva de manera automática.

`DELETE /api/v1/expenses/{id}` conserva ruta y formato: no exige cuerpo ni parámetro de versión y devuelve el objeto Expense con su versión actualizada. Actualiza atómicamente sólo `is_active` y `version`, preservando el último importe, descripción y partes. Si la edición obtiene primero el bloqueo, la anulación espera su commit y conserva esos valores; si se anula primero, una edición de la versión anterior devuelve 409. Una segunda anulación de un gasto ya anulado devuelve 400, como antes.

Transición y consumidores: la variante de PUT sin version, admitida accidentalmente en PR #30, deja de aceptarse. El frontend integrado sólo consume sesión y organizaciones y no tiene un cliente de edición de gastos; Python no consume PUT/DELETE de gastos. Los nuevos clientes de E2 deben consultar y enviar version. No cambian los DTO de lectura ni la estructura del campo introducido en V6. Las pruebas HTTP de controlador verifican 400/409 y la versión de respuesta; la autorización por organización continúa en el circuito existente.

## Cobertura explícita E3.1

Las rutas siguientes requieren sesión, usuario, organización ARS y membresía ADMIN activos. Las mutaciones con cookie conservan la protección CSRF. El autor se toma de la sesión; no hay cuerpo de solicitud ni cobertura inferida al crear gastos u organizaciones.

| Operación | Ruta | Respuesta 200 |
| --- | --- | --- |
| Listar declaraciones, incluidas retiradas | GET `/api/v1/organizations/{id}/analytics/coverage` | Lista ordenada por mes de `{organizationId, month, confirmed}` |
| Confirmar/reconfirmar un mes completo | PUT `/api/v1/organizations/{id}/analytics/coverage/{month}` | `{organizationId, month, confirmed:true}` |
| Retirar la confirmación lógicamente | DELETE `/api/v1/organizations/{id}/analytics/coverage/{month}` | `{organizationId, month, confirmed:false}` |

`month` tiene formato estricto YYYY-MM (años 0001–9999) y debe ser anterior al mes actual de Buenos Aires; inválido, actual o futuro devuelve 400. Las confirmaciones repetidas no duplican filas; retirar un mes conserva una declaración no confirmada. Sin sesión, 401; USER, otra organización o estados inactivos, 403. No se interpreta cobertura retirada como cero. La vista de cobertura sólo expone confirmaciones vigentes; columnas, roles y uso transaccional se definen en [postgre.md](postgre.md). Las vistas de importes muestran observaciones, incluso fuera de meses confirmados; el consumidor debe cruzarlas con cobertura y validar continuidad antes de construir historial confiable. No hay cambios de respuesta v1, callback o consumidor Python en E3.1.

## Predicción autenticada y lectura de vistas E3.2

En E3.2 se implementa el flujo unidireccional `React → Java → Python → PostgreSQL (vistas)` y se retira el callback `Python → Java` (`/api/v1/auth/token`, `/api/v1/expenses/organization/{id}/history` y `GET /api/v1/predictions/organization/{id}/monthly`).

| Servicio | Operación | Ruta | Autenticación y permisos |
| --- | --- | --- | --- |
| Java (pública) | Obtener predicción mensual de la organización | `GET /api/v1/organizations/{organizationId}/predictions/monthly?targetMonth=YYYY-MM` | Sesión activa y membresía activa (`USER` o `ADMIN`) en organización activa con moneda `ARS` |
| Python (interna) | Calcular predicción mensual leyendo vistas SQL | `POST /internal/v2/predictions/monthly` | `Authorization: Bearer <PYTHON_CLIENT_SECRET>` (admite `PYTHON_CLIENT_SECRET_PREVIOUS` durante rotación) |
| Python (pruebas de contrato) | Calcular predicción sobre historial enviado | `POST /api/v1/predictions/monthly` | Contrato v1 con `history` explícito para pruebas aisladas del predictor y validador |

Reglas de E3.2:
- **Resolución de `targetMonth` en Java**: si `targetMonth` se informa, debe cumplir `YYYY-MM` (años `0001` a `9999`), devolviendo `400 Bad Request` si es inválido. Si se omite, Java consulta el último `covered_month` confirmado (`is_confirmed = TRUE`) en `analytics_month_coverage` para la organización y utiliza el mes siguiente (`MAX(covered_month) + 1 mes`); si la organización aún no tiene meses confirmados, utiliza el mes actual en `America/Argentina/Buenos_Aires`.
- **Autenticación técnica `Java → Python` y rotación de secreto**: Java envía `Authorization: Bearer <PYTHON_CLIENT_SECRET>` con tiempos límite configurables (`python.service.connect-timeout-ms`, por defecto `2000` ms; `python.service.read-timeout-ms`, por defecto `5000` ms). Python valida el encabezado en tiempo constante (`hmac.compare_digest`) contra `PYTHON_CLIENT_SECRET` y opcionalmente `PYTHON_CLIENT_SECRET_PREVIOUS`. Ausencia de Bearer devuelve `401 UNAUTHORIZED`; token inválido devuelve `403 FORBIDDEN`.
- **Lectura transaccional de vistas en Python (`ViewsRepository`)**: Python conecta como `shareverance_python` y ejecuta cada lectura dentro de `BEGIN READ ONLY`, `SELECT set_config('shareverance.organization_id', $1, true)`, lectura de `analytics.v_monthly_coverage` y `analytics.v_monthly_expenses`, y `COMMIT` (o `ROLLBACK` en bloque de error antes de cerrar o devolver la conexión), garantizando que ninguna conexión conserve `shareverance.organization_id` fuera de la transacción.
- **Armado de ventana histórica**: Python toma el tramo continuo más reciente de meses confirmados en `analytics.v_monthly_coverage` inmediatamente anterior a `target_month` (terminado en `target_month - 1`, recortado a un máximo de 60 meses). Para cada categoría con gastos dentro de ese tramo continuo, `available_from_month` es su primer mes con gasto en el tramo y los meses confirmados posteriores sin gasto se completan explícitamente con `amount_minor = 0`. Si `target_month - 1` no está confirmado en `v_monthly_coverage`, el historial continuo queda vacío y Python responde `200 OK` con `status: "INSUFFICIENT_HISTORY"`.

## Contrato interno objetivo (E3.2 y OCR)

Rutas internas (E3.2 implementa predicciones v2, E4.2 incorpora OCR; analítica v2 permanece pendiente):

| Operación | Ruta | Cuerpo |
| --- | --- | --- |
| Predicción con datos desde vistas (implementado en E3.2) | POST /internal/v2/predictions/monthly | contract_version (`"2"`), request_id, organization_id, target_month |
| Analítica desde vistas (pendiente E5.1) | POST /internal/v2/analytics/monthly | contract_version, request_id, organization_id, from_month, to_month |
| OCR (implementado en E4.2) | POST /internal/v1/ocr | contract_version (`"1"`), request_id, organization_id, receipt_id, storage_key |

Ejemplo predictivo v2:

```json
{"contract_version":"2","request_id":"cbad14ba-cbe2-47ca-bdf6-b9266054ef0b","organization_id":1,"target_month":"2026-11"}
```

La autenticación técnica Java→Python usa `Authorization: Bearer <PYTHON_CLIENT_SECRET>` (con soporte de rotación mediante `PYTHON_CLIENT_SECRET_PREVIOUS`), sin transmitir credenciales de usuario o de PostgreSQL en cuerpos. `organization_id` es el autorizado por Java. Python rechaza contexto ausente/inválido. `storage_key` se resuelve bajo raíz permitida y debe corresponder al comprobante y organización; no es una URL.

## API pública de comprobantes (implementado en E4.1)

Rutas públicas para la carga, consulta y descarga de comprobantes organizacionales:
* `POST /api/v1/organizations/{organizationId}/receipts`: subida multipart (`file`) de imagen JPEG o PNG hasta 10 MiB y 50 MP. Requiere sesión y membresía activa en la organización. Devuelve `201 CREATED` con metadatos del archivo persistido (`ReceiptUploadResponse`).
* `GET /api/v1/organizations/{organizationId}/receipts/{receiptId}`: descarga autorizada en streaming del archivo (`Content-Disposition: inline`, `Content-Type: image/jpeg` o `image/png`, `Content-Length`). Requiere membresía activa. Si el comprobante aún no está vinculado a un gasto, solo el propietario de la carga o un `ADMIN` pueden descargarlo. Si está vinculado, cualquier miembro activo de la organización tiene acceso.
* `GET /api/v1/organizations/{organizationId}/receipts/{receiptId}/metadata`: consulta de metadatos del comprobante (`ReceiptResponse`).
* `GET /api/v1/organizations/{organizationId}/receipts`: listado de comprobantes activos de la organización visibles para el usuario autenticado.

Ejemplo de respuesta de subida (`201 CREATED`):

```json
{
  "receiptId": "cbad14ba-cbe2-47ca-bdf6-b9266054ef0b",
  "organizationId": 1,
  "originalFilename": "ticket_gas.png",
  "contentType": "image/png",
  "fileSizeBytes": 125050,
  "createdAt": "2026-10-19T21:40:00Z",
  "status": "STORED"
}
```

Códigos de respuesta de comprobantes:
* `201 CREATED`: comprobante almacenado correctamente en el volumen privado y registrado en base de datos.
* `400 BAD REQUEST`: archivo vacío, imagen corrupta, dimensiones superiores a 50 megapíxeles o nombre original vacío, mayor a 255 caracteres o con controles.
* `401 UNAUTHORIZED`: falta sesión válida o CSRF en mutación.
* `403 FORBIDDEN`: el usuario no pertenece a la organización o no tiene permiso sobre un comprobante temporal ajeno.
* `404 NOT FOUND`: comprobante inexistente, perteneciente a otra organización o archivo no disponible por ruta insegura/enlace simbólico.
* `413 PAYLOAD TOO LARGE`: archivo supera el límite de 10 MiB.
* `415 UNSUPPORTED MEDIA TYPE`: el archivo no es JPEG ni PNG (validado por magic bytes).
* `409 CONFLICT`: el comprobante ya se encuentra vinculado a otro gasto activo.

La descarga codifica el nombre en `Content-Disposition` mediante UTF-8. Se conservan rutas y DTOs. En E4.2 el alta de gasto vincula el comprobante si `receiptUrl` es un UUID canónico o su último segmento es un UUID; las referencias anteriores sin UUID se conservan como texto. Para UUID se exige asociación válida: 404 si no existe, está inactivo o pertenece a otra organización; 403 sin permiso y 409 si está vinculado a otro gasto. Gasto, partes y asociación son atómicos; cualquier error revierte el alta sin ocultar el rechazo original.

## OCR y precarga editable de comprobantes (implementado en E4.2)

Rutas para el procesamiento OCR de comprobantes:

* `POST /api/v1/organizations/{organizationId}/receipts/{receiptId}/ocr` (Java pública):
  - Autenticación: sesión activa y membresía en la organización. El comprobante temporal debe pertenecer al usuario autenticado o este debe tener rol `ADMIN`. Si ya está vinculado a un gasto, cualquier miembro activo puede solicitarlo.
  - Comportamiento: Java invoca internamente al servicio Python (`POST /internal/v1/ocr`) pasando `storage_key` (`org_<id>/<uuid>.<ext>`) con cabecera `Authorization: Bearer <PYTHON_CLIENT_SECRET>`. Si Python no responde o se agota el timeout de 15 segundos, responde HTTP 503 permitiendo al frontend continuar con la carga manual.
  - Respuesta `200 OK` (`OcrResultResponse`):
    ```json
    {
      "receiptId": "cbad14ba-cbe2-47ca-bdf6-b9266054ef0b",
      "status": "OK",
      "currency": "ARS",
      "date": "2026-10-15",
      "amount": 7650.50,
      "description": "Supermercado compras varias",
      "warnings": []
    }
    ```

* `POST /internal/v1/ocr` (Python interna):
  - Autenticación: `Authorization: Bearer <PYTHON_CLIENT_SECRET>` (admite `PYTHON_CLIENT_SECRET_PREVIOUS` para rotación).
  - Cuerpo de solicitud (`InternalOcrRequest`):
    ```json
    {
      "contract_version": "1",
      "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
      "organization_id": 1,
      "receipt_id": "cbad14ba-cbe2-47ca-bdf6-b9266054ef0b",
      "storage_key": "org_1/cbad14ba-cbe2-47ca-bdf6-b9266054ef0b.png"
    }
    ```
  - Reglas de validación: `storage_key` debe residir bajo `/data/receipts/org_<organization_id>/`, comparando componentes completos, sin path traversal (`..`, rutas absolutas) ni enlaces simbólicos. Se comprueban los enlaces antes de resolver la ruta, incluida la raíz.
  - Motor: Tesseract OCR local con paquetes en español e inglés (`spa+eng`).
  - Normalización:
    - Fecha: formatos `DD/MM/YYYY`, `DD-MM-YYYY`, `YYYY-MM-DD`, etc. Rechaza fechas futuras en huso `America/Argentina/Buenos_Aires`. Si no se detecta, devuelve `null` y aviso en `warnings`.
    - Importe: detección de patrones monetarios con coma decimal y punto de miles. Prioriza líneas con `TOTAL` excluyendo subtotales. Excluye fragmentos de fecha y valores negativos. Límite máximo 9.999.999.999,99 ARS con hasta 2 decimales. Si no se detecta, devuelve `null` y aviso en `warnings`.
    - Descripción: eliminación de ruido impositivo y de encabezados de tickets; espacios redundantes normalizados; longitud máxima de 500 caracteres. Si no es utilizable, está vacía o excede 500 caracteres, adopta el valor de respaldo `Varios`.
  - Estados devueltos:
    - `OK`: fecha e importe detectados.
    - `PARTIAL`: solo uno de los dos campos numérico/temporal detectado.
    - `FAILED`: ningún campo detectado o archivo vacío/ilegible.

## Resultados analíticos y predictivos

Preservar semántica existente: request_id, organization_id, currency=ARS, status OK/PARTIAL/INSUFFICIENT_HISTORY y simulated. Predicción identifica target_month, months_used, required_months, method, model_version y estimated_amount_minor nullable; organización y categorías. Analítica devuelve importes anterior/actual, diferencia, porcentaje nullable y dirección. Versionar cambios incompatibles de respuesta.

## Errores y tiempos

| HTTP | Uso requerido |
| --- | --- |
| 400/422 | Solicitud inválida según contrato de cada API; normalizar en Java |
| 401/403 | Sesión/servicio no autenticado o permiso rechazado |
| 404 | Recurso no disponible para contexto autorizado |
| 409 | Versión desactualizada o conflicto de negocio |
| 413/415 | Tamaño o formato rechazado |
| 500 | Fallo inesperado con mensaje genérico |
| 503/504 | Dependencia indisponible o tiempo agotado |

Java traduce errores internos a respuesta pública estable con request_id. No reenviar SQL o trazas. No cambiar de golpe los errores 422 v1 existentes. Conexión y lectura deben tener límites por operación; propuesta mensual 2 s conexión/10 s lectura, pendientes de medición. OCR tendrá límite independiente. No reintentar automáticamente mutaciones sin idempotencia.

## Transición y pruebas de compatibilidad

Añadir rutas internas autenticadas y acceso de vistas; adaptar Java; verificar extremos; retirar callback Python→Java y credenciales asociadas cuando no tengan consumidores. El POST con historial puede mantenerse como interfaz de pruebas interna durante transición. No eliminar validaciones de historial porque ahora lo produce SQL.

Pruebas: ejemplos contra esquemas, versiones desconocidas, IDs/tipos, campos extra, importes, errores y correlación; dos organizaciones; tiempo agotado; cero datos; OCR parcial; cambios de versión y consumidores. Contrato v1 histórico detallado a continuación: conserva el formato con historial para código existente, no describe el acceso definitivo a datos.

## Referencia de transición: contrato mensual v1 con historial

Todo el contenido siguiente describe exclusivamente el circuito v1 existente. Las responsabilidades de obtención del historial se sustituyen por el acceso a vistas al migrar; no prevalecen sobre la arquitectura requerida de los apartados anteriores. Las rutas analíticas enumeradas en esta referencia son contrato, no prueba de router implementado.

# Shareverance — Contrato del microservicio Python

**Versión del contrato:** 1
**Alcance:** analítica mensual y predicción del próximo mes por organización y categoría.
**Formato:** HTTP con JSON UTF-8.
**Estado:** contrato en implementación; la integración OAuth Python-Java usa actualmente el predictor simulado.

## 1. Objetivo y responsabilidades

Este contrato permite desarrollar Java, Python y la base de datos de forma independiente. Java prepara el historial y Python procesa solicitudes sin consultar PostgreSQL.

| Componente | Responsabilidad |
| --- | --- |
| Base de datos | Persistencia, restricciones, migraciones y carga reproducible de datos de prueba. |
| Java | Autorización, aislamiento por organización, API de datos, emisión de tokens OAuth para el cliente Python y consulta/agrupación de gastos activos. |
| Python | Obtención del historial desde Java con OAuth, validación del contrato, analítica, predicción y respuestas de error consistentes. |
| Frontend, mediante Java | Presentación de resultados, estados y avisos de simulación. |

El nombre de una tabla o un cambio de implementación SQL no modifica este contrato si Java conserva la estructura y el significado de los datos enviados.

## 2. Operaciones

| Método y ruta | Función |
| --- | --- |
| `GET /health` | Comprobar disponibilidad del servicio; no mide precisión predictiva. |
| `POST /api/v1/analytics/monthly` | Comparar los últimos dos meses completos, globalmente y por categoría, usando el contrato enviado. |
| `POST /api/v1/predictions/monthly` | Predecir el próximo mes con el contrato enviado. |
| `GET /api/v1/predictions/organization/{organization_id}/monthly?target_month=YYYY-MM` | Obtener el historial desde Java y generar una predicción sin modificar datos. |

URL interna de Docker para Python: `http://python-service:8000`. Python obtiene los datos mediante `GET /api/v1/expenses/organization/{organization_id}/history?targetMonth=YYYY-MM` en Java.

Las solicitudes que envían un contrato usan `Content-Type: application/json`. Python solicita un token en `POST /api/v1/auth/token` mediante `application/x-www-form-urlencoded`, con `grant_type=client_credentials`, `client_id`, `client_secret` y `scope=analytics:read`. El token Bearer dura una hora y solo autoriza GET del historial de una organización. Las credenciales se configuran con `PYTHON_CLIENT_ID` y `PYTHON_CLIENT_SECRET` en el entorno de ambos servicios; no usar las credenciales de ejemplo fuera del desarrollo local.

Los tokens OAuth se guardan en memoria en Java: se invalidan al reiniciar y requieren una sola instancia Java. Para escalar horizontalmente se necesita almacenamiento compartido de tokens o una estrategia de tokens firmados. El scope identifica al servicio Python, no a un usuario ni a su membresía. El endpoint de historial todavía no aplica permisos por organización; no publicar Python o ese endpoint fuera de una red confiable hasta implementar esa autorización en Java.

## 3. Entrada compartida

| Campo | Tipo | Regla |
| --- | --- | --- |
| `contract_version` | string | Obligatorio; valor exacto `"1"`. |
| `request_id` | string UUID | Obligatorio; generado por Java y reproducido por Python. |
| `organization_id` | integer | Obligatorio; positivo y hasta `9007199254740991`. |
| `currency` | string | Obligatorio; `"ARS"` para esta versión. |
| `categories` | array | Entre 0 y 100 categorías, sin identificadores duplicados. |
| `categories[].category_id` | integer | Positivo y hasta `9007199254740991`; pertenece a la organización. |
| `categories[].available_from_month` | string | Mes desde el que la categoría tiene cobertura completa en el historial enviado; formato `YYYY-MM`. |
| `history` | array | Entre 0 y 60 meses completos, consecutivos, únicos y ordenados. |
| `history[].month` | string | Mes calendario válido, formato `YYYY-MM`, años 0001–9999. |
| `history[].category_totals` | array | Un registro por categoría disponible en ese mes. |
| `category_totals[].category_id` | integer | Debe existir en `categories`; no se repite dentro del mes. |
| `category_totals[].amount_minor` | integer | Importe en centavos, entre 0 y `9007199254740991`. |
| `target_month` | string | Obligatorio únicamente para predicción; mes calendario válido inmediatamente posterior al último mes del historial cuando exista historial. |

No se aceptan campos desconocidos en solicitudes. Los enteros son estrictos: no se admiten strings numéricos, booleanos ni valores como `100.0`. El total general de cada mes también debe respetar el límite monetario. El cuerpo tiene un máximo de 1 MiB en UTF-8; superar ese límite produce HTTP 413.

Cada objeto de `categories` contiene únicamente `category_id` y `available_from_month`. Java obtiene los nombres de las categorías para su presentación; Python trabaja exclusivamente con sus identificadores.

Las listas de categorías se envían ordenadas por `category_id`. El orden de los campos de un objeto JSON no es relevante.

### 3.1. Preparación del historial en Java

1. Seleccionar una única organización autorizada.
2. Incluir gastos con `EXPENSE.is_active = true`.
3. Agrupar por año y mes de `EXPENSE.expense_date` y por `EXPENSE.category_id`.
4. Sumar `EXPENSE.amount` una sola vez por gasto. No utilizar una unión con `EXPENSE_SPLIT` que multiplique filas.
5. Convertir `BigDecimal` a centavos exactamente, sin redondear valores inválidos ni pasar por `double`.
6. Incluir solamente meses completos y con cobertura confiable; excluir el mes en curso.
7. Incluir categorías desactivadas cuando tienen gastos históricos activos.
8. Completar los ceros de los meses cubiertos sin gastos.

El total organizacional es la suma de todas las categorías incluidas en cada mes. No se envía un total general adicional. Si el modelo permite gastos sin categoría, Java deberá asignarles una categoría persistida de «Sin categoría»; no puede omitir esos gastos ni inventar identificadores reservados.

### 3.2. Cobertura del historial

`available_from_month` indica cobertura confiable, no necesariamente creación de la categoría. Con historial no vacío, debe estar dentro del período enviado. Antes de ese mes, la categoría se omite; desde ese mes, aparece en todos los meses, incluso con importe cero.

El historial global declara meses completos: no puede haber gastos existentes excluidos por falta de cobertura de una categoría. Si esto ocurre, Java debe recortar el período a meses confiables o corregir los datos antes de enviar la solicitud. Python puede validar la coherencia estructural, pero no comprobar que faltan registros en la base.

Sin historial se exige `categories: []`. Una organización sin categorías puede enviar meses con `category_totals: []`: esos meses representan gasto total cero confirmado.

La política para determinar cobertura confiable en organizaciones reales debe documentarse en la capa de datos. No se infiere automáticamente a partir de la fecha de creación de la organización ni del primer gasto.

### 3.3. Ejemplo completo de solicitud predictiva

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "target_month": "2026-10",
  "categories": [
    {"category_id": 10, "available_from_month": "2026-04"},
    {"category_id": 20, "available_from_month": "2026-04"}
  ],
  "history": [
    {"month": "2026-04", "category_totals": [{"category_id": 10, "amount_minor": 10000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-05", "category_totals": [{"category_id": 10, "amount_minor": 10500000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-06", "category_totals": [{"category_id": 10, "amount_minor": 11000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-07", "category_totals": [{"category_id": 10, "amount_minor": 11500000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-08", "category_totals": [{"category_id": 10, "amount_minor": 12000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-09", "category_totals": [{"category_id": 10, "amount_minor": 12500000}, {"category_id": 20, "amount_minor": 5000000}]}
  ]
}
```

Para solicitar analítica se utiliza el mismo objeto sin `target_month`. El historial real de pruebas podrá contener 24 meses; el ejemplo se limita a seis para facilitar su lectura.

## 4. Salida de analítica

Compara los últimos dos meses recibidos. No utiliza el primer y el último mes de todo el historial.

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "status": "OK",
  "simulated": false,
  "comparison": {"previous_month": "2026-08", "current_month": "2026-09"},
  "organization": {
    "status": "OK",
    "previous_amount_minor": 17000000,
    "current_amount_minor": 17500000,
    "change_amount_minor": 500000,
    "change_percentage": 2.94,
    "direction": "INCREASING"
  },
  "categories": [
    {"category_id": 10, "status": "OK", "previous_amount_minor": 12000000, "current_amount_minor": 12500000, "change_amount_minor": 500000, "change_percentage": 4.17, "direction": "INCREASING"},
    {"category_id": 20, "status": "OK", "previous_amount_minor": 5000000, "current_amount_minor": 5000000, "change_amount_minor": 0, "change_percentage": 0.0, "direction": "UNCHANGED"}
  ]
}
```

### 4.1. Reglas de cálculo

- `change_amount_minor = current_amount_minor - previous_amount_minor`.
- `change_percentage = change_amount_minor / previous_amount_minor × 100`.
- Porcentaje redondeado a dos decimales mediante redondeo decimal `ROUND_HALF_UP`.
- Si el importe anterior es cero, el porcentaje es `null`, incluso cuando ambos importes son cero; la variación monetaria y la dirección siguen disponibles.
- `direction`: `INCREASING` si la diferencia es positiva, `DECREASING` si es negativa y `UNCHANGED` si es cero.
- La dirección describe una comparación mensual observada; no demuestra una tendencia sostenida ni predice el futuro.
- Cada categoría requiere cobertura en los dos meses comparados.
- Una serie insuficiente utiliza `status: "INSUFFICIENT_HISTORY"` y todos sus campos de cálculo en `null`.
- Con menos de dos meses globales, `comparison` es `null` y todos los resultados son insuficientes.
- La respuesta contiene exactamente las categorías solicitadas, ordenadas por identificador. Java conserva los nombres de categoría en su propio módulo y relaciona los resultados por `category_id`. Los nombres no se envían a Python ni se devuelven en sus respuestas.

## 5. Salida de predicción

La estimación general y las estimaciones por categoría son independientes. La general se calcula sobre la serie de totales de la organización; cada categoría utiliza su propia serie desde `available_from_month`.

No existe una garantía de que la suma de las predicciones por categoría coincida con la predicción general. Java debe respetar esa diferencia y no recalcular ni reemplazar la estimación general con la suma de categorías.

Ejemplo de estructura, con importes ilustrativos que no son resultados de un modelo ejecutado:

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "target_month": "2026-10",
  "status": "OK",
  "simulated": false,
  "organization": {
    "status": "OK",
    "estimated_amount_minor": 17400000,
    "months_used": 6,
    "required_months": 6,
    "method": "simple_exponential_smoothing",
    "model_version": "1"
  },
  "categories": [
    {"category_id": 10, "status": "OK", "estimated_amount_minor": 12300000, "months_used": 6, "required_months": 6, "method": "simple_exponential_smoothing", "model_version": "1"},
    {"category_id": 20, "status": "OK", "estimated_amount_minor": 5000000, "months_used": 6, "required_months": 6, "method": "simple_exponential_smoothing", "model_version": "1"}
  ]
}
```

### 5.1. Reglas de predicción

| Aspecto | Definición |
| --- | --- |
| Horizonte | Un mes, inmediatamente posterior al último mes recibido. |
| Mínimo inicial | Seis meses completos por serie; regla operativa, no garantía de precisión. |
| Historial empleado | Todos los meses disponibles de cada serie dentro de la solicitud. |
| Resultado | Entero no negativo en centavos, redondeado con `ROUND_HALF_UP`. |
| Serie insuficiente | `estimated_amount_minor: null`, estado `INSUFFICIENT_HISTORY`; nunca cero como sustituto de falta de datos. |
| `months_used` | Meses empleados en un resultado `OK`; cero cuando no se ejecutó el modelo por historial insuficiente. |
| `required_months` | Mínimo exigido por la versión del modelo, inicialmente seis. |
| Método y versión | Strings obligatorios en cada resultado; Java los trata como metadatos, sin condicionar su deserialización a un método concreto. |

El modelo candidato inicial es el suavizado exponencial simple. Su configuración y evaluación se documentan en el módulo predictivo; este contrato no fija el algoritmo ni sus parámetros. Un cambio de método compatible con los mismos campos no exige cambiar la versión del contrato, pero sí identificar la nueva versión del modelo.

No se devuelven intervalos ni porcentajes de confianza hasta implementar y evaluar un procedimiento que los sustente. Los resultados expresan ARS nominales; no se ajustan por inflación. El incremento de gasto no identifica por sí solo mayor consumo ni su causa.

Un fallo numérico, resultado no finito o fuera del límite monetario es un fallo interno del cálculo. No debe etiquetarse como historial insuficiente ni corregirse silenciosamente.

### 5.2. Ejemplo de resultado individual insuficiente

```json
{
  "category_id": 30,
  "status": "INSUFFICIENT_HISTORY",
  "estimated_amount_minor": null,
  "months_used": 0,
  "required_months": 6,
  "method": "simple_exponential_smoothing",
  "model_version": "1"
}
```

Con historial vacío la solicitud sigue siendo válida y devuelve insuficiencia. `target_month` continúa siendo obligatorio; Java selecciona el mes siguiente al último mes cerrado según su calendario de negocio. Python no puede comprobar esa relación con un historial vacío.

## 6. Estados de resultados

Estas reglas se aplican tanto a analítica como a predicción:

| Estado general | Condición |
| --- | --- |
| `OK` | La organización y todas las categorías tienen resultado. |
| `PARTIAL` | Al menos un resultado es `OK` y al menos uno es `INSUFFICIENT_HISTORY`. |
| `INSUFFICIENT_HISTORY` | Ningún resultado tiene información suficiente. |

Los resultados individuales utilizan solamente `OK` o `INSUFFICIENT_HISTORY`. Se conserva la misma estructura de campos en resultados insuficientes; los valores no calculables se expresan como `null`.

La insuficiencia de historial es un resultado de negocio con HTTP 200. Los errores de implementación o infraestructura no se presentan como resultados parciales.

## 7. Errores HTTP

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "El historial contiene meses duplicados.",
    "details": [
      {"field": "history", "code": "DUPLICATE_MONTH"}
    ]
  }
}
```

| HTTP | `error.code` | Significado |
| --- | --- | --- |
| 413 | `PAYLOAD_TOO_LARGE` | Cuerpo superior a 1 MiB. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Tipo de contenido distinto de JSON. |
| 422 | `INVALID_REQUEST` | JSON mal formado, campo inválido o violación de reglas del historial. |
| 500 | `INTERNAL_ERROR` | Fallo inesperado o error de cálculo. |
| 503 | `SERVICE_UNAVAILABLE` | Servicio temporalmente no preparado. |

`details` es siempre una lista y puede estar vacía. Si no puede obtenerse un UUID válido del cuerpo, `request_id` es `null`. Los mensajes son informativos; Java decide mediante los códigos, no comparando textos. No se exponen trazas ni detalles internos en respuestas.

Códigos de detalle iniciales: `REQUIRED_FIELD`, `UNKNOWN_FIELD`, `INVALID_TYPE`, `INVALID_VALUE`, `UNSUPPORTED_CONTRACT_VERSION`, `DUPLICATE_MONTH`, `NON_CONSECUTIVE_MONTHS`, `UNSORTED_HISTORY`, `DUPLICATE_CATEGORY`, `UNKNOWN_CATEGORY`, `INVALID_COVERAGE`, `MISSING_CATEGORY_TOTAL`, `INVALID_TARGET_MONTH`, `AMOUNT_OUT_OF_RANGE` y `INVALID_JSON`.

Python debe adaptar los errores de validación de FastAPI al formato indicado, en lugar de exponer su formato por defecto.

## 8. Comportamiento del cliente Java

- Configurar la URL mediante `PYTHON_SERVICE_URL`.
- Usar inicialmente un tiempo máximo de conexión de 2 segundos y de respuesta de 10 segundos, configurables.
- Deserializar campos por nombre, sin depender del orden de objetos JSON.
- Comprobar coincidencia de versión, `request_id`, organización, moneda y, para predicción, mes objetivo.
- Comprobar presencia y coherencia de estados, campos monetarios y categorías solicitadas.
- Aceptar campos adicionales de respuesta que no utilice; rechazar versiones principales no soportadas.
- Diferenciar errores de validación, indisponibilidad, tiempo agotado y respuesta incompatible.
- No sustituir fallos por ceros ni por respuestas simuladas automáticamente.
- No aplicar reintentos automáticos en la implementación inicial. Las operaciones no modifican datos, pero un reintento necesita una política explícita.

Cada solicitud es independiente y no crea registros, entrena modelos persistidos ni guarda predicciones. `request_id` sirve para correlación; no es una clave de idempotencia ni de caché.

## 9. Simulación para desarrollo independiente

El simulador implementa las mismas rutas, validaciones, campos y estados. Se selecciona por configuración del servicio Python, por ejemplo `PREDICTOR_MODE=mock`; Java no envía un algoritmo ni un indicador para activar simulaciones.

Para una predicción simulada válida con historial suficiente, cada serie devuelve como estimación su último importe observado. Es una regla determinista exclusiva del simulador, no una validación de calidad predictiva. Mantiene el mínimo de seis meses y los estados de insuficiencia.

Los resultados simulados usan `simulated: true`, `method: "mock"` y `model_version: "1"`. Los resultados reales usan `simulated: false`. Si se simula analítica, se conservan sus fórmulas y se marca `simulated: true`; no se mezclan resultados reales y simulados dentro de una respuesta.

La pantalla que muestre una respuesta simulada debe identificarla como datos de prueba. `/health` continúa disponible en ambos modos.

## 10. Historial de prueba de dos años

Se dispone de un historial de 24 meses destinado a pruebas. Su existencia permite probar el contrato antes de cargarlo en PostgreSQL. Este documento no verifica su contenido ni presupone su calidad o representatividad.

La preparación del conjunto debe comprobar:

1. Veinticuatro meses consecutivos y cerrados.
2. Identificadores y categorías coherentes con el modelo.
3. Moneda única y conversión exacta a centavos.
4. Distinción entre cero confirmado y dato ausente.
5. Cobertura declarada por categoría.
6. Procedencia y transformaciones documentadas, identificando datos sintéticos cuando corresponda.

La carga posterior en la base debe ser un seed de desarrollo repetible, separado de las migraciones de estructura. Debe preservar relaciones, evitar duplicados y utilizar una organización de prueba identificable. No deben desactivarse restricciones para introducirlo «a la fuerza» ni cargarse como gastos reales de usuarios.

Si el conjunto contiene solamente totales agregados, su conversión a gastos de demostración debe documentarse como una representación sintética; no equivale a disponer de comprobantes o gastos individuales originales.

El mes objetivo será el posterior al último mes del conjunto. No se desplazan fechas históricas sin registrar la transformación. Si hay meses posteriores al calendario actual, deberán excluirse de las pruebas de integración que apliquen la regla de no admitir gastos futuros.

## 11. Pruebas y aceptación

| Caso | Resultado esperado |
| --- | --- |
| Historial válido de 24 meses | HTTP 200; resultados disponibles según cobertura. |
| Analítica del ejemplo | Total 17.000.000 → 17.500.000 centavos; variación 500.000 y 2,94 %. |
| Simulador con el ejemplo | General 17.500.000; categorías 12.500.000 y 5.000.000; `simulated: true`. |
| Categoría reciente con menos de seis meses | Predicción individual insuficiente; estado general `PARTIAL` si otras series son válidas. |
| Historial vacío | HTTP 200; insuficiencia, sin estimaciones inventadas. |
| Mes completo sin gastos | Cero aceptado como dato válido. |
| Importe anterior cero | Porcentaje `null`, diferencia y dirección disponibles. |
| Mes duplicado, desordenado o faltante | HTTP 422 con código de detalle correspondiente. |
| Categoría desconocida o repetida | HTTP 422. |
| Categoría disponible omitida en un mes | HTTP 422, `MISSING_CATEGORY_TOTAL`. |
| Importe negativo, decimal, booleano o string | HTTP 422. |
| Mes objetivo incompatible | HTTP 422, `INVALID_TARGET_MONTH`. |
| Predicciones independientes | Java acepta que su suma no coincida con la general. |
| Python detenido o demora excesiva | Java informa indisponibilidad o tiempo agotado, sin ceros sustitutivos. |
| Respuesta con correlación o estructura inválida | Java detecta fallo de integración. |

Mantener archivos JSON de entrada y respuesta esperada compartidos por los servicios. Las pruebas de contrato verifican estructura y reglas; las pruebas del algoritmo verifican cálculos; la evaluación predictiva mide calidad. Son comprobaciones distintas.

La evaluación del modelo con el historial de dos años respeta el orden temporal: cada mes se predice usando únicamente meses anteriores. Se compara con una referencia sencilla, como el promedio de los últimos tres meses. Un conjunto sintético permite validar comportamiento, pero no demuestra precisión sobre gastos reales.

## 12. Evolución y entregables

La versión 1 incluye comparación mensual y predicción a un mes. No incluye OCR, presupuestos, ajuste por inflación, horizontes múltiples, intervalos de confianza ni diagnóstico causal.

Cambiar tipos, eliminar campos, redefinir su significado o modificar incompatiblemente las reglas requiere una nueva versión principal y rutas `/api/v2/...`. Agregar campos opcionales a una respuesta puede ser compatible; los consumidores deben ignorarlos. El modelo puede evolucionar mediante `model_version` conservando el contrato.

Referencia mantenida dentro de `docs/contratos_api.md`. Entregables complementarios para implementar:

- `docs/contracts/openapi.yaml`, como definición formal coherente con este documento.
- `docs/contracts/examples/`, con solicitudes y respuestas válidas e inválidas.
- Validaciones y simulador en Python.
- Cliente HTTP y proveedor de historial de prueba en Java.
- Consulta JDBC y seed de desarrollo a cargo de Java y datos.
- Pruebas de contrato ejecutables por ambos servicios.

El contrato se considera integrado cuando ambos servicios procesan los mismos ejemplos, reproducen los estados especificados y manejan errores de comunicación sin alterar su significado.
