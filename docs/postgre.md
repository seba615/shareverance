# PostgreSQL: modelo, vistas y permisos

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado persistido

PostgreSQL 17.11 en Compose. Migraciones en `backend-java/src/main/resources/db/migration/`: V1 crea infra_demo; V2 seis tablas de negocio; V3 elimina timezone y agrega paid_by_membership_id; V5 crea auth_sessions; V6 agrega columna `version` en expenses; V7 agrega cobertura mensual explícita, roles de ejecución y vistas analíticas. No renumerar versiones ni modificar SQL aplicado.

| Tabla | Responsabilidad |
| --- | --- |
| users | Cuenta global, email único, hash de contraseña y estado |
| organizations | Nombre, tipo, moneda y estado |
| user_organization | Membresía única usuario/organización, rol ADMIN/USER y estado |
| categories | Catálogo por organización y estado |
| expenses | Autor, titular, fecha, importe, categoría, descripción, comprobante y estado |
| expense_splits | Participación monetaria de cada membresía en el gasto |
| auth_sessions | Sesiones técnicas web, hash de token y expiración |
| infra_demo | Diagnóstico de infraestructura, sin significado financiero |
| analytics_month_coverage | Declaración explícita de mes completo por organización, autor, fecha y confirmación lógica |

Las claves compuestas existentes protegen pertenencia de categorías, titulares y partes. amounts son DECIMAL(12,2); amount de partes positivo; par gasto/membresía único. No hay tablas de caja, pagos ni liquidaciones. La columna receipt_url existe pero no acredita servicio de archivos. El DER del [README](../README.md) representa el modelo vigente.

## Evolución requerida

E4.1 incorpora V8 (`receipts`, metadatos privados de comprobantes) y V9 (FK compuesta de organización/gasto). Java tiene permisos sobre `receipts`; Python no tiene SELECT sobre esta tabla. V8 se conserva con su checksum publicado. V9 valida también filas existentes: si encuentra una asociación entre organizaciones distintas, la migración falla y revierte sin borrar ni corregir datos automáticamente. Se debe investigar y corregir ese vínculo antes de reintentar.

La asociación JDBC comprueba organización, estados activos y vínculo previo en un único UPDATE condicional. Dos asociaciones diferentes concurrentes no pueden sobrescribirse. E4.2 integra ese flujo al alta: UUID en `receipt_url` exige vinculación dentro de la transacción del gasto y sus partes; referencias anteriores sin UUID se conservan como texto. Las pruebas PostgreSQL cubren migración incremental V8→V9 con datos válidos e inválidos, restricciones, rol Java, asociación concurrente y compensación de archivos ante fallo SQL/rollback. `ReceiptExpenseTransactionPostgresTest` verifica además el alta atómica con comprobante, rechazos 403/404/409, fallo SQL y dos altas concurrentes con un único ganador. No se agregan ni modifican migraciones en esta corrección E4.2.

Nuevas migraciones para restricciones adicionales necesarias, vistas, roles y metadatos de cobertura/adjuntos cuando se concrete su diseño. Comprobar migración desde base existente con datos, no únicamente desde una base vacía. La suma de partes requiere operación transaccional; una FK no garantiza esa igualdad.

ARS única y calendario en configuración de servidor, sin reintroducir timezone por organización. Anulación/desactivación lógica; no purgar datos como mantenimiento. Evitar que cambios en membresías alteren distribuciones históricas.

## Contrato de vistas analíticas E3.1

V7 implementa las siguientes vistas, propiedad del migrador, con `security_barrier=true` y permisos de tablas comprobados con el propietario de la vista (no `security_invoker`). Python no recibe acceso directo a las tablas:

| Vista | Grano | Columnas mínimas |
| --- | --- | --- |
| analytics.v_monthly_expenses | organización, mes, categoría | organization_id, month, category_id, amount_minor |
| analytics.v_monthly_participations | organización, mes, categoría, membresía | organization_id, month, category_id, membership_id, amount_minor |
| analytics.v_monthly_coverage | organización, mes declarado completo | organization_id, month |

`month` es DATE del primer día del mes; `amount_minor` es BIGINT calculado por SUM de decimales multiplicados por 100, sin pasar por coma flotante. Sólo gastos vigentes de organizaciones activas ARS. Categorías y membresías inactivas conservan sus importes históricos. Gastos y participaciones son agregaciones separadas: no se multiplica el gasto al unir partes. Las vistas no exponen nombres, correos, descripciones, comprobantes ni hashes. No rellenan filas cero ni excluyen por sí mismas el mes corriente: la cobertura y el corte de período deben aplicarse explícitamente al consumirlas.

La cobertura se declara por organización y mes completo anterior al actual de Buenos Aires. `analytics_month_coverage` conserva `declared_by_user_id`, `declared_at` e `is_confirmed`; la FK exige membresía del autor, y CHECK exige primer día de mes cerrado. La API Java exige ADMIN activo. PUT confirma/reconfirma y DELETE retira lógicamente sin borrar el registro; se conserva la última declaración, sin incorporar una auditoría completa de cambios. La migración no certifica automáticamente ningún mes existente. `v_monthly_coverage` publica únicamente confirmaciones vigentes del contexto. Ausencia de gastos en un mes confirmado permite representar cero; ausencia de cobertura significa desconocido. Los huecos no se certifican ni se rellenan automáticamente. La adaptación a `available_from_month`, continuidad y recorte de 60 meses del contrato Python corresponde a E3.2; E3.1 no modifica ese contrato ni declara cobertura histórica de categorías por su primer gasto.

## Roles y aislamiento

El propietario/migrador sigue siendo `POSTGRES_USER` en el entorno local y necesita CREATEROLE al instalar por primera vez. Sus credenciales se configuran separadamente en `SPRING_FLYWAY_URL/USER/PASSWORD`. El datasource de Java usa `shareverance_java`, con CRUD sobre tablas de aplicación y uso de sus secuencias, sin DDL ni lectura de `flyway_schema_history`. Python usa `shareverance_python`: CONNECT, USAGE de analytics, ejecución de la función de contexto y SELECT sólo sobre las tres vistas. Sin membresías de roles, herencia, SUPERUSER, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS ni permisos de creación de objetos, temporales o escritura. V7 retira CONNECT/TEMP públicos en la base y permisos públicos sobre tablas de negocio; no reasigna propietarios. Es una base dedicada a Shareverance: revisar roles ajenos antes de aplicarla en una base compartida.

`database/configure-access.sql`, ejecutado por el servicio efímero Compose `database-access` antes de Java, crea los roles si faltan y configura sus contraseñas desde variables de entorno (nunca desde una migración). Rechaza contraseñas vacías/iguales o roles con atributos/membresías incompatibles; no corrige privilegios administrativos ajenos silenciosamente. Funciona también con un volumen existente y no borra datos. V7 puede crear los roles como NOLOGIN cuando Flyway se ejecuta fuera de Compose; deben aprovisionarse antes de iniciar los clientes. En E3.2 el contenedor `python-service` recibe las credenciales de lectura (`shareverance_python` / `PYTHON_DB_PASSWORD`), consulta las vistas dentro de `BEGIN READ ONLY` con `set_config('shareverance.organization_id', $1, true)` y retira el callback hacia Java.

Toda consulta analítica debe usar una transacción explícita, preferentemente READ ONLY, con contexto de organización autorizado por Java:

```sql
BEGIN READ ONLY;
SELECT set_config('shareverance.organization_id', '123', true);
SELECT * FROM analytics.v_monthly_expenses;
SELECT * FROM analytics.v_monthly_coverage;
COMMIT;
```

El tercer parámetro `true` limita el valor a la transacción; el consumidor debe enviar el ID como parámetro enlazado, nunca concatenarlo. Las tres vistas filtran dentro de SQL aunque el consumidor omita WHERE. Contexto ausente, vacío, no entero, fuera de BIGINT o no positivo devuelve cero filas. Commit/rollback elimina el contexto local antes de reutilizar la conexión A→B; el pool debe terminar siempre la transacción y no usar contexto de sesión (`false`/SET persistente). No se usa RLS: la barrera y la función de contexto son parte de las vistas. Una credencial de servicio puede establecer otro contexto; esto protege errores de consulta, no sustituye autorización Java ni defiende de una credencial Python comprometida. E3.2 debe establecer el contexto sólo desde llamadas Java autenticadas.

Antes de darlo por terminado, probar lectura autorizada de A, rechazo/ausencia de B, acceso sin contexto, SQL directo sobre tablas y reutilización de conexión A→B. Revisar asimismo acceso técnico histórico desde Python a Java y retirarlo durante transición.

## Respaldo y restauración

Respaldar PostgreSQL y volumen de comprobantes con correspondencia consistente. Definir ventana sin escrituras o procedimiento equivalente, restaurar ambos en entorno aislado y comprobar referencias. Nunca usar down -v sobre datos que deban conservarse. Cambiar contraseña en .env no cambia por sí solo la de una base inicializada.

## Pruebas

Migraciones limpias e incrementales, FK de tenant cruzado, importes/roles inválidos, unicidad, rollback gasto/partes, versión optimista con dos conexiones y protección del último ADMIN. Validar agregaciones contra dataset manual y permisos reales de cada rol. Los tests de repositorios mock no sustituyen estas comprobaciones.

## Verificación E1.2: gasto y partes

E1.2 individual/iguales utiliza el esquema vigente (V1, V2, V3 y V5), sin migraciones nuevas ni cambios en restricciones. Las pruebas de integración usan PostgreSQL 17.11 exclusivo y verifican rollback si falla una parte en alta o edición, y si falla el guardado final del gasto después de actualizar partes. No prueban concurrencia E1.3 ni constituyen aceptación integral. La base y el volumen de desarrollo quedan fuera de estas pruebas.

## Verificación E1.3: versión optimista y último ADMIN

E1.3 incorpora la migración Flyway V6 (`version INT NOT NULL DEFAULT 1` en `expenses`). Se valida mediante pruebas de integración con PostgreSQL 17.11 exclusivo (`ExpenseTransactionPostgresTest`), verificando la respuesta HTTP 409 Conflict ante versiones desactualizadas sin alteraciones parciales en base de datos, y el rechazo transaccional con `FOR UPDATE` ante intentos de desactivar o revocar el rol al último ADMIN activo de la organización.

### Corrección de concurrencia E1.3

Se conserva V6 sin modificarla; no hay migraciones nuevas. La fila de organizations constituye el bloqueo compartido para las operaciones de revocación de rol/desactivación de membresías de una organización. Siempre se bloquea antes de bloquear la membresía y contar ADMIN activos, evitando el caso de dos filas distintas que veían el mismo conteo.

La edición adquiere el bloqueo de expenses mediante UPDATE condicional por versión y estado activo antes de actualizar expense_splits. La anulación ejecuta UPDATE de is_active/version sobre la misma fila, conservando los demás valores. Las transacciones concurrentes conservan la igualdad entre importe y suma de partes. Las pruebas adicionales usan el esquema aislado `e13_concurrency` de la base temporal indicada por E12_TEST_JDBC_URL; no se instalan esos objetos en desarrollo.

## Consultas de historial E2.2 y calendario E2.3

Sin migraciones nuevas: utilizan V1–V6 vigentes. En el historial paginado (E2.2), los filtros de fecha, categoría, estado y organización se aplican a expenses antes de la paginación; SUM se calcula sin unir expense_splits para evitar duplicaciones. Orden estable expense_date DESC/id DESC, LIMIT/OFFSET parametrizados. En el calendario mensual (E2.3), los totales diarios y conteos (`SUM(amount)`, `COUNT(*)`) se agrupan por `expense_date ASC` directamente sobre `expenses` con `organization_id = ?`, `is_active = TRUE` y rango mensual inclusivo, sin unir `expense_splits`. Totales, filas y partes de cada respuesta se consultan bajo REPEATABLE_READ; no se conserva un snapshot entre páginas o solicitudes realizadas en momentos distintos. No se añaden índices ni se acredita rendimiento con volúmenes de producción. Las pruebas usan el esquema e22_history dentro de la base aislada de pruebas; nunca la base de desarrollo.
