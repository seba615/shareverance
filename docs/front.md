# Frontend React

Fecha de revisión: 21/10/2026. Base revisada: `9ea8ce4` (main, incluye PR #60). Las evidencias históricas mantienen sus fechas y alcances.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Inventario actual

React/TypeScript/Vite en `frontend/src/App.tsx`, estilos en `style.css` y cliente en `api/java.ts`. Funciones existentes: registro con nombre/email/contraseña, login, restauración de sesión al recargar, logout, saludo del usuario, listado de organizaciones, formulario de creación, selección de organización y manejo de carga/error/vacío. Tipos actuales HOUSEHOLD, CONDO y UNIVERSITY. Conservar estas funciones. ARS es la única moneda: no se ofrece un campo de selección y el cliente envía `ARS` al crear una organización.

E2.1 agrega pestañas de gastos, miembros y categorías en la organización activa. Gastos incluye alta manual y listado; Miembros incluye nombres/correos, incorporación por email exacto, cambio de rol, desactivación y reactivación; Categorías incluye alta y desactivación. E2.2 incorpora filtros por período/categoría/estado, paginación servidor, total filtrado, detalle con partes y edición/anulación de gastos. E2.3 incorpora en la pestaña Gastos el selector alternable entre Historial y Calendario mensual navegable con totales diarios y detalle por fecha. E5.2 incorpora la pestaña Panel con analítica real vía Java, gráficos SVG y tablas; la edición de categorías sigue pendiente. El diagnóstico de infraestructura permanece disponible por script/API. Estas funciones observadas en código no acreditan aceptación integral.

## Navegación y vistas requeridas

E6.2 también implementa la pestaña Predicción: consulta a Java, selección del mes objetivo, resultado organizacional y por categoría, método/versión y avisos de insuficiencia/mock/SES experimental. La consulta actual estima un mes. El gráfico futuro y selector de 4–6 meses están planificados en el [roadmap complementario](road_map.md#predictor-multihorizonte), no implementados. La edición de categorías, presentación de participación individual y aceptación integral permanecen pendientes; la tabla de vistas requeridas no acredita por sí sola su implementación.

| Vista | Contenido y acciones |
| --- | --- |
| Acceso | Registro/login, errores controlados y sesión restaurada |
| Mis organizaciones | Crear, listar, seleccionar; moneda ARS |
| Inicio de organización | Contexto activo y acceso al panel, gastos, calendario y administración |
| Miembros | Listado; ADMIN agrega email de usuario existente, cambia rol o desactiva |
| Categorías | Listado; ADMIN crea/edita/desactiva; inactivas visibles en historia |
| Carga/edición | Fecha, importe ARS, descripción, categoría, titular y reparto |
| Historial | Período, categoría, estado; paginación servidor; abrir detalle |
| Detalle | Datos, autor/titular, partes y comprobante; editar/anular según permiso |
| Calendario | Consulta de gastos por fecha y acceso a detalle |
| Panel | Total mensual, comparación, evolución y categorías; tablas equivalentes |
| Predicción | Próximo mes, total/categorías, método y estado de suficiencia |

Calendario incluido: vista alternable dentro de la pestaña Gastos con mes navegable (anterior, actual, siguiente sin meses futuros en `America/Argentina/Buenos_Aires`), total diario de gastos vigentes y lista paginada al seleccionar fecha. No incorpora gastos futuros ni recurrencias. Controles de ADMIN ocultos para USER, pero la seguridad efectiva pertenece al servidor.

## Formularios

Aplicar reglas de [alcance_mvp.md](alcance_mvp.md). Mostrar importe localizado sin usar coma/punto para alterar su valor; validar precisión, conservar decimales exactos al serializar. USER titular propio; ADMIN selector de miembros activos. Individual o iguales con selección de otro/grupo/todos. Mostrar distribución y ajuste de centavos por ID ascendente; un importe insuficiente para asignar al menos un centavo por participante es inválido. E1.2 implementa esas reglas en Java. E2.1 incorpora el formulario manual con INDIVIDUAL, ALL_MEMBERS y SPECIFIC_GROUP; este último permite elegir otro miembro o un grupo. El cliente envía el importe como cadena decimal para conservar su representación; Java aplica las validaciones finales. CUSTOM no está expuesto en el formulario.

Edición conserva participantes históricos y debe enviar la versión positiva de la última consulta; Java la exige en PUT. Omitirla devuelve 400. Ante 409, informar cambio concurrente y permitir recargar sin sobrescribir silenciosamente. Anulación requiere acción explícita y actualiza listas/panel. No presentar participación como deuda.

OCR: subir una imagen a Java y mostrar resultado editable. No fijar Content-Type application/json en multipart; el navegador genera el boundary. La precarga no guarda el gasto. Mantener acceso a formulario manual ante error. No exponer rutas locales ni credenciales internas.

En E4.2 se incorpora en el formulario de alta de gastos (`App.tsx`) la subida de comprobante y precarga editable vía `receiptApi.upload` y `receiptApi.processOcr`:
- Selector de archivo JPEG o PNG (hasta 10 MB).
- Al seleccionar archivo, se envía multipart con CSRF a Java (`POST /api/v1/organizations/{id}/receipts`) y se solicita el reconocimiento OCR (`POST .../{receiptId}/ocr`).
- Los campos reconocidos (fecha, importe y descripción) se precargan en los controles del formulario y quedan plenamente editables antes del alta definitiva.
- Si el normalizador detecta advertencias (p. ej. fecha no detectada o uso del fallback `Varios`), se presentan en pantalla en un aviso de advertencia.
- Si el servicio OCR o la conexión con Python no están disponibles o fallan, se muestra aviso informativo y el usuario puede continuar cargando el gasto de forma manual.
- Al confirmar el formulario, se envía el `receiptId` como `receiptUrl` para vincular el archivo al nuevo gasto creado. Cambiar de organización o cancelar el formulario reinicia los datos y estados del comprobante.

## Sesión, accesibilidad y estados

Conservar cookies same-origin y flujo CSRF actual. No almacenar tokens técnicos en navegador. Ante expiración, pedir login y evitar mostrar datos de otra organización. Al cambiar organización se invalidan inmediatamente los datos, errores y estados de carga de solicitudes previas; no se muestran datos ni roles de A bajo B. También se reinician formularios, participantes, modalidad de reparto y pestaña. El cargador identifica la selección y la solicitud más reciente: A→B→A no acepta la respuesta de la primera visita a A, y una recarga vieja de la misma organización no reemplaza una nueva. Las operaciones de escritura de la selección anterior pueden terminar en Java, pero sus resultados no cierran formularios, muestran errores ni recargan otra selección. Logout y desmontaje invalidan las respuestas pendientes; no se abortan escrituras del servidor.

Diseño adaptable móvil/escritorio, labels de campos, teclado, foco visible y errores junto al campo. Gráficos con tabla; comparación sin base previa muestra porcentaje no disponible. Diferenciar cero, carga, error e historial insuficiente. No mostrar resultados simulados como predicciones reales.

E5.2 utiliza SVG nativo con React para gráficos simples, sin dependencias gráficas adicionales. Cada gráfico tiene título accesible y tabla equivalente con valores exactos; la información no depende del color ni del gráfico.

El gráfico de categorías muestra el nombre sobre cada barra, incluida la marca de categoría inactiva. Los nombres extensos se abrevian visualmente, conservando el texto completo en el título del gráfico y la tabla; si falta el nombre se muestra el ID.

## Panel analítico E5.2

La pestaña Panel consulta únicamente Java mediante `GET /api/v1/organizations/{id}/analytics/monthly`. Presenta gasto organizacional, variación absoluta/porcentual, evolución mensual y categorías del último mes. No representa pagos, deudas ni participación individual. No incluye predicciones simuladas ni el modelo real de E6.

La ventana inicial contiene los seis últimos meses cerrados según Buenos Aires. Los filtros Desde/Hasta permiten de 1 a 60 meses cerrados inclusivos; Consultar aplica los filtros y Actualizar repite el período aplicado. El encabezado identifica el período de los resultados, aun si se están editando los filtros. Se comparan los dos últimos meses de la ventana, cuando ambos tienen cobertura confirmada.

Un mes sin cobertura muestra total no disponible y no se dibuja como cero; un mes confirmado sin gastos muestra cero. La insuficiencia de comparación no oculta la evolución disponible ni el total confirmado del último mes. Con base anterior cero, el porcentaje dice No disponible. Las categorías históricas inactivas conservan su nombre y se identifican; si falta su nombre se usa el ID. Los importes se formatean desde centavos enteros mediante BigInt, sin redondeo por división flotante.

Los gráficos SVG tienen tablas con caption y encabezados de fila/columna. Los gráficos largos y tablas permiten desplazamiento horizontal; los controles y tarjetas se adaptan al ancho disponible. Se distinguen carga, error, falta de cobertura y mes confirmado sin gastos. La consulta se invalida al cambiar organización, período, recarga o desmontaje, incluido logout y A→B→A; resultados y errores llevan una clave de consulta que evita mostrar datos anteriores bajo otro período. Volver al panel consulta de nuevo; Actualizar permite observar cambios externos.

## Pruebas

Al seleccionar otro comprobante se vacían fecha, importe y descripción anteriores. Cada resultado OCR reemplaza esos tres campos, dejando vacíos los valores no detectados en lugar de conservarlos de la imagen anterior. Un error o resultado parcial mantiene la edición manual y sus advertencias; la fecha continúa siendo obligatoria antes de registrar.

E4.2 utiliza `receiptLoader.ts` para el ciclo subida→OCR. Cancelar, cambiar de organización o desmontar invalida respuestas pendientes; reabrir el formulario en la misma organización inicia una generación independiente. Durante la subida/OCR se bloquean el guardado y los tres campos de precarga para evitar sobrescribir ediciones manuales; al terminar o fallar OCR vuelven a ser editables. El fallo de OCR conserva el comprobante subido y permite continuar manualmente. `receiptLoader.test.mjs` verifica respuestas y errores tardíos, reapertura y liberación de controles. Cancelar descarta el resultado en la UI, sin borrar el archivo temporal del servidor ni iniciar una purga automática.

Preservar registro/login/recarga/logout y creación/selección. Recorrido manual y OCR→revisión→alta→historial→calendario→panel→predicción. Verificar USER/ADMIN, sesión vencida, cambio rápido de organización, filtros/paginación, conflicto 409, OCR fallido y Python caído. Revisar móvil y escritorio; build TypeScript/Vite y pruebas existentes afectadas.

## Comprobación automatizada de selección

`frontend/tests/organizationLoader.test.mjs` y `frontend/tests/calendarModel.test.mjs`, mediante `npm test` en Node 24, ejercitan el cargador real utilizado por App (respuestas fuera de orden, errores tardíos, recargas, A→B→A y descarte tras cierre/desmontaje) y el modelo de calendario (huso `America/Argentina/Buenos_Aires`, navegación mensual sin meses futuros, grilla con inicio en lunes y totales por fecha). No instalan bibliotecas de pruebas adicionales. El build TypeScript/Vite comprueba la integración de tipos. El recorrido visual y la autorización integral con backend quedan pendientes de aceptación.

## Historial, detalle y operaciones E2.2

`ExpenseHistory.tsx` consulta la ruta paginada; la carga inicial de la organización deja de descargar todos los gastos. Permite desde/hasta inclusivos, categoría (incluidas inactivas históricas), estado vigente/anulado/todos y tamaños 10/20/50. Cambiar filtros reinicia la página y descarta respuestas previas. El total representa todos los gastos filtrados. Una organización nueva monta una instancia independiente; respuestas de detalles, páginas y operaciones anteriores se descartan al desmontar.

El detalle muestra fecha, categoría, importe, autor, titular, estado, versión y todas las partes como participación, no deuda. No convierte receiptUrl en enlace público. Java ya ofrece descarga privada autorizada de comprobantes; el detalle actual no incorpora ese enlace de descarga. Autor o ADMIN ven edición/anulación sólo para gastos vigentes. La edición conserva titular y participantes y puede conservar su categoría inactiva; solicita importe/fecha/descripción/categoría y envía la versión consultada. Ante 409 conserva el formulario local, bloquea el reenvío y ofrece recargar explícitamente. Anulación requiere confirmación y refresca el historial, sin reactivación. Los controles de la UI no sustituyen permisos Java.

## Calendario con detalle por fecha E2.3

`App.tsx` incorpora un selector alternable (`Historial` / `Calendario`) dentro de la pestaña `Gastos`. `ExpenseCalendar.tsx` y `calendarModel.ts` operan sobre el calendario `America/Argentina/Buenos_Aires`, consultan `GET /api/v1/expenses/organization/{organizationId}/calendar?month=YYYY-MM` para obtener el total mensual, cantidad de gastos vigentes y desglose diario (`date`, `totalAmount`, `expenseCount`), y bloquean la navegación a meses o fechas futuras.

Al seleccionar un día no futuro del mes, `ExpenseCalendar.tsx` consulta `GET /api/v1/expenses/organization/{organizationId}/page` con `fromDate=YYYY-MM-DD`, `toDate=YYYY-MM-DD` y `state=ACTIVE`, mostrando los gastos vigentes de esa fecha con paginación y permitiendo abrir el detalle completo (`GET /api/v1/expenses/{id}`), editar con versión optimista (`PUT /api/v1/expenses/{id}`) o anular con confirmación (`DELETE /api/v1/expenses/{id}`) según permisos de autor o ADMIN. Las operaciones de la selección vigente recargan tanto los totales mensuales del calendario como el listado diario; una edición que cambia de fecha selecciona su destino y reinicia la página.

`calendarOperations.ts` controla las consultas de detalle y las escrituras por generación de operación. Cambiar mes o día invalida resultados, errores y finalizaciones anteriores y libera inmediatamente los controles; cerrar detalle también invalida su operación. Cambiar organización o vista desmonta la instancia e invalida sus operaciones. Una respuesta antigua no reemplaza el detalle, mueve la fecha, publica avisos/conflictos ni libera el estado ocupado de una operación nueva, incluso al navegar A→B→A. Las escrituras enviadas pueden terminar en Java; descartarlas en la UI no las cancela ni revierte. `calendarOperations.test.mjs` verifica estas carreras con promesas controladas, además de la suite de modelo y cargador; no sustituye aceptación en navegador.


## Vista predictiva E6.2

La pestaña Predicción consulta únicamente Java mediante `GET /api/v1/organizations/{id}/predictions/monthly?targetMonth=YYYY-MM` (`predictionApi.monthly` en `frontend/src/api/java.ts`). Presenta la estimación de gasto mensual proyectado para la organización y el desglose por categorías.

- **Mes objetivo**: el campo permite ingresar un mes específico (`YYYY-MM`, años 0001 a 9999) o dejarlo vacío para consultar el mes recomendado automáticamente por el backend (el mes siguiente al último mes confirmado en cobertura, o el mes actual en Buenos Aires). El botón *Mes recomendado* limpia la selección manual para volver a la recomendación automática.
- **Identificación del modelo (A12)**: se identifica con claridad si la respuesta corresponde a un cálculo simulado (`simulated: true`, mock de prueba para verificación de contratos) o al modelo real (`simulated: false`, candidato experimental Suavizado Exponencial Simple - SES versión 1). No se presentan resultados de mock como si fueran cálculos reales.
- **Estados de insuficiencia (A11)**: si la organización o una categoría no cuenta con al menos 6 meses consecutivos con cobertura confirmada inmediatamente anteriores al mes objetivo, el estado devuelto es `INSUFFICIENT_HISTORY` y la estimación es nula (`null`). La interfaz informa el mínimo requerido y que el modelo no se ejecutó, con importe *No disponible*. `months_used=0` significa que no se usaron meses para ejecutar el modelo; no permite inferir cuántos meses confirmados existen. Para categorías insuficientes se muestra historial no utilizado y el mínimo exigido; sólo los resultados OK muestran meses efectivamente utilizados. No se inventa un contador de cobertura disponible ni se dirige a una interfaz de administración de cobertura que todavía no existe.
- **Desglose por categorías**: tabla accesible con `caption`, encabezados semánticos y formato de importes exacto desde centavos enteros con `formatMinor`. Identifica nombres de categorías activas e inactivas históricas; si falta el nombre en la lista autorizada se utiliza el ID.
- **Resiliencia (A13)**: ante caídas del servicio predictivo, timeouts o indisponibilidad interna (HTTP 503), la vista presenta un mensaje de degradación controlado (*El servicio de predicciones no está disponible en este momento. Intentá nuevamente más tarde.*), sin bloquear la navegación entre pestañas ni la gestión de gastos. Respuestas 401 y 403 muestran sus avisos correspondientes.
- **Control de ciclo de vida**: `createPredictionLoader` en `predictionModel.ts` administra la clave de consulta `[organizationId, appliedTargetMonth, refreshToken, refresh]`. Al alternar de organización o de mes se descartan inmediatamente respuestas y errores tardíos (A→B→A, respuestas fuera de orden y desmontaje).
- **Pruebas automatizadas**: `frontend/tests/predictionModel.test.mjs` verifica validaciones de formato de mes, formateo en huso horario neutro con soporte de años 0001–0099, etiquetas de métodos y carreras del cargador reactivo mediante promesas controladas.

`PredictionMessages.tsx` contiene los mensajes usados por la vista y comprobados mediante renderizado React en `predictionMessages.test.mjs`. El aviso mock explica que el método reutiliza el último importe del historial consultado cuando hay historial suficiente; no describe los datos como sintéticos. Ambos modos muestran versión; el modo experimental conserva la advertencia de aceptación predictiva pendiente. Las pruebas renderizan insuficiencia con months_used=0, historial OK y avisos mock/SES usando Vite y react-dom/server existentes, sin dependencias nuevas. No sustituyen revisión visual ni integración HTTP de la vista completa.
