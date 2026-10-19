# PostgreSQL: modelo, vistas y permisos

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado persistido

PostgreSQL 17.11 en Compose. Migraciones en `backend-java/src/main/resources/db/migration/`: V1 crea infra_demo; V2 seis tablas de negocio; V3 elimina timezone y agrega paid_by_membership_id; V5 crea auth_sessions; V5.1 amplía ck_expense_split_type para admitir CUSTOM, sin modificar registros existentes. No renumerar versiones ni modificar SQL aplicado.

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

Las claves compuestas existentes protegen pertenencia de categorías, titulares y partes. amounts son DECIMAL(12,2); amount de partes positivo; par gasto/membresía único. No hay tablas de caja, pagos ni liquidaciones. La columna receipt_url existe pero no acredita servicio de archivos. El DER del [README](../README.md) representa el modelo vigente.

## Evolución requerida

Nuevas migraciones para versión optimista, restricciones adicionales necesarias, vistas, roles y metadatos de cobertura/adjuntos cuando se concrete su diseño. Comprobar migración desde base existente con datos, no únicamente desde una base vacía. La suma de partes requiere operación transaccional; una FK no garantiza esa igualdad.

ARS única y calendario en configuración de servidor, sin reintroducir timezone por organización. Anulación/desactivación lógica; no purgar datos como mantenimiento. Evitar que cambios en membresías alteren distribuciones históricas.

## Contrato de vistas analíticas — diseño a implementar

Nombres propuestos para implementar y fijar en migración:

| Vista | Grano | Columnas mínimas |
| --- | --- | --- |
| analytics.v_monthly_expenses | organización, mes, categoría | organization_id, month, category_id, amount_minor |
| analytics.v_monthly_participations | organización, mes, categoría, membresía | organization_id, month, category_id, membership_id, amount_minor |

Solo gastos vigentes, mes de expense_date, conversión exacta a centavos. No agrupar importes de gasto después de unirlos con múltiples repartos: eso duplica totales. Las vistas no fabrican cobertura; definir fuente de cobertura confiable y su fecha inicial antes de producir ceros. Datos personales y hashes no forman parte de las vistas.

## Roles y aislamiento

Separar propietario/migrador, usuario Java de ejecución y usuario Python de lectura. Python: CONNECT a base, USAGE al esquema analítico y SELECT exclusivamente sobre vistas autorizadas. Sin privilegios heredados que permitan leer tablas, crear objetos o escribir. Credenciales distintas, fuera de Git.

Toda consulta requiere organización. Una vista global con WHERE aplicado solo por Python no constituye aislamiento adicional en PostgreSQL. Pendiente elegir e implementar mecanismo SQL: por ejemplo contexto transaccional de organización y vistas filtradas, o RLS compatible con el rol y las vistas. No asumir que RLS se aplica al consumidor de cualquier vista: verificar propietario, invocador y privilegios efectivos. Contexto solicitado debe proceder de Java autenticado; limpiar conexiones del pool al terminar. Estas medidas protegen errores de consulta, no convierten una credencial de servicio comprometida en credencial individual por usuario.

Antes de darlo por terminado, probar lectura autorizada de A, rechazo/ausencia de B, acceso sin contexto, SQL directo sobre tablas y reutilización de conexión A→B. Revisar asimismo acceso técnico histórico desde Python a Java y retirarlo durante transición.

## Respaldo y restauración

Respaldar PostgreSQL y volumen de comprobantes con correspondencia consistente. Definir ventana sin escrituras o procedimiento equivalente, restaurar ambos en entorno aislado y comprobar referencias. Nunca usar down -v sobre datos que deban conservarse. Cambiar contraseña en .env no cambia por sí solo la de una base inicializada.

## Pruebas

Migraciones limpias e incrementales, FK de tenant cruzado, importes/roles inválidos, unicidad, rollback gasto/partes, versión optimista con dos conexiones y protección del último ADMIN. Validar agregaciones contra dataset manual y permisos reales de cada rol. Los tests de repositorios mock no sustituyen estas comprobaciones.

## Migración E1.2

`V5_1__reparto_personalizado.sql` se ubica después de V5 y antes de V6 (categorías iniciales de feat/web-app). No modifica migraciones aplicadas ni convierte históricos EQUAL. Verificación de numeración en main y todas las referencias remotas; registro de versión en issue no realizado porque GitHub rechazó crear la tarea con HTTP 403.

Si un entorno ya aplicó V6 desde feat/web-app antes de incorporar V5.1, Flyway no aplicará automáticamente esta migración anterior. Revisar ese historial y coordinar su aplicación; no renumerar versiones aplicadas ni activar outOfOrder global como ajuste incidental. La validación de esta entrega parte de main/V5.
