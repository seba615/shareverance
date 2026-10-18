# Backend Java

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Responsabilidad y código actual

Spring Boot con JDBC, sin JPA. Código en `backend-java/src/main/java/com/shareverance/`. Controladores de autenticación, usuarios, organizaciones, membresías, categorías y gastos; repositorios JDBC; migraciones Flyway. Las APIs existentes no equivalen a cumplimiento integral de permisos y validaciones.

Sesión web mediante cookie; frontend obtiene CSRF antes de mutaciones. El código contiene además autenticación técnica para el circuito histórico Python→Java. Ese circuito debe migrarse al acceso Python→vistas, sin reutilizar sus credenciales automáticamente en sentido inverso.

Implementación E1.1 en curso: la autorización de rutas exige cuenta, membresía y organización activas; las organizaciones con moneda distinta de ARS no son operables. Java también rechaza moneda no ARS y estados inactivos al crear organizaciones, miembros, categorías y gastos. Esto no certifica aislamiento integral ni aceptación con PostgreSQL.

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

Pendiente: aceptación de E1.1 con integración y datos persistidos, filtros/paginación, reparto determinista, versión optimista, contratos públicos analíticos, cliente Java→Python autenticado y servicio privado de archivos. Para la base local con registros previos a la regla ARS, se acordó una eliminación única de toda la base antes de iniciar esta versión; no se migran ni convierten esos datos. Los reinicios ordinarios conservan los datos en el volumen. Revisar CUSTOM y las modalidades existentes para alinearlas con individual/iguales sin reinterpretar datos silenciosamente.
