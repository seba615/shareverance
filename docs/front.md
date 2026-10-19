# Frontend React

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Inventario actual

React/TypeScript/Vite en `frontend/src/App.tsx`, estilos en `style.css` y cliente en `api/java.ts`. Funciones existentes: registro con nombre/email/contraseña, login, restauración de sesión al recargar, logout, saludo del usuario, listado de organizaciones, formulario de creación, selección de organización y manejo de carga/error/vacío. Tipos actuales HOUSEHOLD, CONDO y UNIVERSITY. Conservar estas funciones. ARS es la única moneda: no se ofrece un campo de selección y el cliente envía `ARS` al crear una organización.

E2.1 agrega pestañas de gastos, miembros y categorías en la organización activa. Gastos incluye alta manual y listado; Miembros incluye nombres/correos, incorporación por email exacto, cambio de rol, desactivación y reactivación; Categorías incluye alta y desactivación. No se implementaron edición de categorías, edición/anulación de gastos, detalle completo, filtros/paginación, calendario ni analítica. El diagnóstico de infraestructura permanece disponible por script/API. Estas funciones observadas en código no acreditan aceptación integral.

## Navegación y vistas requeridas

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

Calendario incluido; diseño propuesto: mes navegable, total por día y lista al seleccionar fecha. Precisar si se requiere otra granularidad antes de construirlo. No incorpora gastos futuros ni recurrencias. Controles de ADMIN ocultos para USER, pero la seguridad efectiva pertenece al servidor.

## Formularios

Aplicar reglas de [alcance_mvp.md](alcance_mvp.md). Mostrar importe localizado sin usar coma/punto para alterar su valor; validar precisión, conservar decimales exactos al serializar. USER titular propio; ADMIN selector de miembros activos. Individual o iguales con selección de otro/grupo/todos. Mostrar distribución y ajuste de centavos por ID ascendente; un importe insuficiente para asignar al menos un centavo por participante es inválido. E1.2 implementa esas reglas en Java. E2.1 incorpora el formulario manual con INDIVIDUAL, ALL_MEMBERS y SPECIFIC_GROUP; este último permite elegir otro miembro o un grupo. El cliente envía el importe como cadena decimal para conservar su representación; Java aplica las validaciones finales. CUSTOM no está expuesto en el formulario.

Edición conserva participantes históricos y debe enviar la versión positiva de la última consulta; Java la exige en PUT. Omitirla devuelve 400. Ante 409, informar cambio concurrente y permitir recargar sin sobrescribir silenciosamente. Anulación requiere acción explícita y actualiza listas/panel. No presentar participación como deuda.

OCR: subir una imagen a Java y mostrar resultado editable. No fijar Content-Type application/json en multipart; el navegador genera el boundary. La precarga no guarda el gasto. Mantener acceso a formulario manual ante error. No exponer rutas locales ni credenciales internas.

## Sesión, accesibilidad y estados

Conservar cookies same-origin y flujo CSRF actual. No almacenar tokens técnicos en navegador. Ante expiración, pedir login y evitar mostrar datos de otra organización. Al cambiar organización se invalidan inmediatamente los datos, errores y estados de carga de solicitudes previas; no se muestran datos ni roles de A bajo B. También se reinician formularios, participantes, modalidad de reparto y pestaña. El cargador identifica la selección y la solicitud más reciente: A→B→A no acepta la respuesta de la primera visita a A, y una recarga vieja de la misma organización no reemplaza una nueva. Las operaciones de escritura de la selección anterior pueden terminar en Java, pero sus resultados no cierran formularios, muestran errores ni recargan otra selección. Logout y desmontaje invalidan las respuestas pendientes; no se abortan escrituras del servidor.

Diseño adaptable móvil/escritorio, labels de campos, teclado, foco visible y errores junto al campo. Gráficos con tabla; comparación sin base previa muestra porcentaje no disponible. Diferenciar cero, carga, error e historial insuficiente. No mostrar resultados simulados como predicciones reales.

Biblioteca gráfica pendiente de selección. No incorporar un framework adicional por defecto.

## Pruebas

Preservar registro/login/recarga/logout y creación/selección. Recorrido manual y OCR→revisión→alta→historial→calendario→panel→predicción. Verificar USER/ADMIN, sesión vencida, cambio rápido de organización, filtros/paginación, conflicto 409, OCR fallido y Python caído. Revisar móvil y escritorio; build TypeScript/Vite y pruebas existentes afectadas.

## Comprobación automatizada de selección

`frontend/tests/organizationLoader.test.mjs`, mediante `npm test` en Node 24, ejercita el cargador real utilizado por App con respuestas controladas fuera de orden, errores tardíos, recargas, A→B→A y descarte tras cierre/desmontaje. No instala bibliotecas de pruebas adicionales. El build TypeScript/Vite comprueba la integración de tipos. El recorrido visual y la autorización integral con backend quedan pendientes de aceptación.
