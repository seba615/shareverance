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
| Vistas SQL | Fijar fuente de cobertura y mecanismo de aislamiento de organización |
| Servicio interno | Fijar esquema de autenticación Java→Python y rotación de secreto |
| Gráficos | Seleccionar biblioteca compatible con stack y tablas accesibles |
| Evaluación | Fijar dataset temporal, métrica y umbral antes de medir modelo |
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
