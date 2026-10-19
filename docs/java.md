# Backend Java

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Responsabilidad y código actual

Spring Boot con JDBC, sin JPA. Código en `backend-java/src/main/java/com/shareverance/`. Controladores de autenticación, usuarios, organizaciones, membresías, categorías y gastos; repositorios JDBC; migraciones Flyway. Las APIs existentes no equivalen a cumplimiento integral de permisos y validaciones.

Sesión web mediante cookie; frontend obtiene CSRF antes de mutaciones. El código contiene además autenticación técnica para el circuito histórico Python→Java. Ese circuito debe migrarse al acceso Python→vistas, sin reutilizar sus credenciales automáticamente en sentido inverso.

Implementación E1.1 integrada mediante PR #25, commit `7733612`: la autorización de rutas exige cuenta, membresía y organización activas; las organizaciones con moneda distinta de ARS no son operables. Java también rechaza moneda no ARS y estados inactivos al crear organizaciones, miembros, categorías y gastos. Esto no certifica aislamiento integral ni aceptación con PostgreSQL.

## Reglas y permisos

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
- Contratos en [contratos_api.md](contratos_api.md). Nuevas rutas allí son diseño pendiente, no endpoints existentes.

## Archivos, errores y operación

Nombre generado por servidor; nunca ruta absoluta del cliente. Registrar asociación organización/propietario/archivo para autorizar temporales. Validar tipo por contenido, tamaño y dimensiones. Al asociar a gasto, verificar misma organización y propiedad/autorización. Descarga mediante Java con permiso, sin servir directamente una carpeta pública.

Mensajes de error controlados y request_id. Errores inesperados no exponen SQL ni stack traces. Credencial de aplicación separada de migraciones; secretos por entorno fuera de Git.

## Pruebas requeridas

Pruebas de reglas con casos límite; integración JDBC con PostgreSQL real para rollback, restricciones, reparto y concurrencia; autorización cruzada en todas las rutas; alta por ADMIN frente a USER; CSRF, logout, expiración y cuentas inactivas; dos ediciones con igual versión; caída/timeout de Python; archivos inválidos y lectura entre organizaciones. Nunca ejecutar pruebas destructivas sobre la base de desarrollo.

## Brechas

Pendiente: aceptación de E1.1 con integración y datos persistidos, aceptación integral del historial y de concurrencia, contratos públicos analíticos, cliente Java→Python autenticado y servicio privado de archivos. Para la base local con registros previos a la regla ARS, se acordó una eliminación única de toda la base antes de iniciar esta versión; no se migran ni convierten esos datos. Los reinicios ordinarios conservan los datos en el volumen. Revisar CUSTOM y las modalidades existentes para alinearlas con individual/iguales sin reinterpretar datos silenciosamente.

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
