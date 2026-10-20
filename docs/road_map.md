# Roadmap del MVP 1

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado y dependencias

Estados: IMPLEMENTADO significa código observado; PENDIENTE requiere trabajo; VERIFICADO exige ejecución y evidencia vinculada a commit. Este relevamiento no ejecutó pruebas. No asignar PASS por contratos, mocks o inspección estática.

Registro/login/sesión/organizaciones frontend y JDBC Java: IMPLEMENTADO, sin certificación integral. Predictor Python: SIMULADO. OCR, vistas SQL para Python, calendario y panel: PENDIENTES.

## Etapas

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

## Parámetros que faltan precisar

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
| Operación | Tiempos/reintentos definitivos, retención de logs y procedimiento de respaldo |
| Cuenta | Explicitar inclusión/exclusión de recuperación de contraseña y verificación de email |

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

## Evidencia y cierre

Por tarea registrar commit/PR, archivos modificados, comprobación ejecutada, resultado, fecha y límites. Pendiente no cambia a verificado si la prueba no se ejecutó. El MVP termina al completar criterios y parámetros necesarios, no por existir endpoints.

## Trazabilidad de definiciones

D01 documentación → README/alcance; D02–D04 funciones/vistas/ARS → alcance/front; D05–D10 miembros/categorías/reparto/edición/historial → alcance/java; D11–D14 OCR/archivos → python_ocr/java; D15–D17 cobertura/panel/modelo → predictor/front; D18 participación → alcance/postgre; D19 vistas → postgre/predictor; D20–D21 contratos/tiempos → contratos_api; D22–D27 seguridad/estados/operación/logs → java/postgre/alcance; D28 aceptación → este documento; D29 concurrencia → java/postgre. Las definiciones concretas se mantienen en su archivo principal.

## Consolidación documental

REGLAS_DE_NEGOCIO.md se sustituye por alcance_mvp.md. ROAD_MAP_PYTHON.md se sustituye por este roadmap y python_predictor.md. CONTRATO_PYTHON_V1.md se conserva como referencia compatible dentro de contratos_api.md. GUIA_DESARROLLO.md se integra en README. ENTORNO_INICIAL.md se sustituye por instrucciones operativas y especificaciones vigentes; sus copias históricas de código no son instrucciones de configuración actual. El historial Git conserva versiones anteriores.

## Seguimiento de tareas e integración

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
