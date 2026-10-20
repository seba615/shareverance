# Alcance y reglas del MVP 1

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Producto

Shareverance registra y distribuye gastos de hogares, consorcios y universidades. Una cuenta puede pertenecer a varias organizaciones con rol ADMIN o USER en cada una. Cada organización constituye un ámbito independiente de información.

## Funciones y vistas

| Función | Vista | Estado de implementación |
| --- | --- | --- |
| Registro, login, restauración y cierre de sesión | Acceso | Código existente |
| Crear, listar y seleccionar organizaciones | Mis organizaciones | Java acepta solo ARS y lista organizaciones activas de membresías activas; aceptación integral pendiente |
| Administrar miembros y categorías | Miembros / Categorías | E2.1 incorpora vistas de miembros y alta/desactivación de categorías; edición de categorías y aceptación integral pendientes |
| Cargar, editar y anular gastos | Formulario / Detalle | E2.1 incorpora alta manual/listado y E2.2 detalle/edición/anulación; aceptación integral pendiente |
| Consultar por período, categoría y estado | Historial paginado | E2.2 incorpora filtros y paginación servidor; aceptación integral pendiente |
| Consultar gastos por fecha | Calendario | E2.3 incorpora calendario mensual navegable con totales diarios y detalle por fecha; aceptación integral pendiente |
| Precargar fecha, importe y descripción | Carga con comprobante | E4.1 incorpora volumen privado/almacenamiento y E4.2 motor OCR Tesseract, normalización y precarga editable; aceptación integral pendiente |
| Totales, comparación mensual, evolución y categorías | Panel analítico | Pendiente de integración real |
| Estimar próximo mes | Predicciones dentro del panel | API simulada existente; modelo real pendiente |
| Consultar participación por miembro | Detalle / resumen individual | Basado en repartos; presentación pendiente |

Quedan fuera fondos comunes, aportes de caja, pagos parciales, compensaciones, liquidación de deudas, recurrencias, exportaciones, funcionamiento offline y aplicación nativa. El calendario sí está incluido. No se presupone gestión de vencimientos ni gastos futuros por incluir una vista calendario.

## Organizaciones y miembros

- ARS es la única moneda. El calendario del servidor utiliza `America/Argentina/Buenos_Aires`; no requiere columna timezone por organización.
- El creador de una organización obtiene ADMIN. El ADMIN incorpora usuarios existentes mediante email exacto, sin habilitar un directorio global de personas. Una membresía desactivada puede reincorporarse conservando su ID e historial, o reactivarse desde la administración de miembros; requiere cuenta y organización activas. Esta reactivación de membresía no permite reactivar gastos anulados.
- Solo ADMIN administra miembros, roles, configuración y categorías. Ninguna operación puede dejar a una organización activa sin ADMIN activo.
- Usuarios, membresías y organizaciones inactivos no pueden operar. Desactivar preserva los registros históricos.
- Las categorías inactivas no se eligen en altas nuevas, pero conservan nombre y asociación histórica.

## Gastos y reparto

Fecha no futura según el calendario del servidor; importe positivo hasta 9.999.999.999,99 ARS; máximo dos decimales, rechazando precisión adicional. Descripción obligatoria de hasta 500 caracteres. Categoría y titular pertenecen a la organización.

El autor es la cuenta que registra; el titular es la membresía a cuyo nombre se carga. USER solo carga a su nombre; ADMIN puede elegir otro miembro activo de su organización. La participación asignada es la parte del gasto que corresponde a cada miembro. No equivale a deuda pendiente ni a pago efectuado.

Modalidades: individual o partes iguales. Partes iguales permite seleccionar otro miembro, un grupo o todos los miembros activos. No se admite reparto personalizado por importes o porcentajes en el MVP.

La suma de partes debe ser exactamente el total, sin participantes duplicados ni ajenos y con importes positivos. Trabajar en centavos para dividir. El algoritmo distribuye el cociente entero y asigna los centavos sobrantes por ID de membresía ascendente. Si el importe no permite al menos un centavo por participante, rechazar la distribución.

Autor o ADMIN pueden editar y anular. Editar conserva los participantes históricos; no incorpora automáticamente miembros nuevos. Cambiar el importe recalcula partes entre esos participantes, sin exigir que sigan activos: en el reparto igualitario se divide en centavos y se asignan sobrantes por ID de membresía ascendente. Si el nuevo importe no permite al menos un centavo por participante, se rechaza la edición completa. Una categoría inactiva puede conservarse en la edición sin convertirla en elegible para un cambio de categoría. Anular excluye el gasto de indicadores y predicciones; conserva gasto, partes y comprobante. No se reactiva en el MVP.

Ediciones concurrentes requieren versión optimista: la edición debe enviar la versión positiva consultada; una omisión o versión no positiva se rechaza con 400, y una versión desactualizada con 409. Gasto y partes se guardan en una transacción. La anulación incrementa versión y sólo cambia el estado, preservando los datos de una edición que haya terminado antes; una edición previa a la anulación no puede sobrescribirla.

## Calendario, analítica y OCR

El calendario debe permitir consultar gastos por fecha y abrir su detalle. El diseño mensual con total diario es la propuesta de implementación pendiente de precisar en [front.md](front.md); no agrega recurrencias.

Analítica usa fecha del gasto y solo gastos vigentes; diferencia entre gasto organizacional y participación individual. Un mes sin cobertura conocida no se transforma automáticamente en cero. Predicción del mes siguiente al último cerrado con cobertura confiable.

E3.1 registra cobertura explícita por organización y mes cerrado, declarada por ADMIN activo mediante Java y retirable lógicamente sin borrar el registro. No infiere cobertura por el primer gasto, por crear una organización ni por ausencia de filas. Las vistas SQL usan contexto de organización limitado a la transacción; sin contexto válido no devuelven datos. El consumidor Python y la retirada del callback siguen pendientes de E3.2; esta base SQL no acredita analítica ni predicción reales.

OCR opcional: fecha, importe y descripción de hasta 500 caracteres; valor alternativo `Varios` cuando la descripción no es utilizable. No extrae proveedor, razón social ni moneda. El usuario revisa y confirma antes del alta. Un fallo de OCR permite continuar manualmente.

## Seguridad y entrega

Entrega local con Docker Compose. Frontend consume Java. Python es interno y sus llamadas se autentican. Java autoriza organización y operación antes de invocarlo. Python solo consulta vistas autorizadas mediante credencial propia de lectura. Adjuntos privados en volumen persistente compartido: Java escribe, Python lee.

Logs técnicos mínimos con correlación, sin secretos, contraseñas, tokens ni contenido de comprobantes. No se exige auditoría persistida de negocio. Datos históricos no se purgan como rutina. Restauración debe recuperar base y comprobantes coherentes.

## Precisiones todavía necesarias

Las decisiones de arquitectura no resuelven por sí solas estos parámetros: semántica detallada del calendario, limpieza de archivos sin asociación, biblioteca gráfica, umbral de evaluación predictiva, retención de logs y recuperación de contraseña/verificación de email. E4.1/E4.2 concretan motor Tesseract, límite de 50 MP, reglas de `Varios` y tiempos de OCR en [python_ocr.md](python_ocr.md); la precisión en un conjunto representativo sigue pendiente de evaluación. La autenticación Java→Python se concreta en [contratos_api.md](contratos_api.md). El aislamiento SQL transaccional y la fuente de cobertura se concretan en E3.1 y [postgre.md](postgre.md). Ver tareas del [roadmap](road_map.md). Ninguno se considera implementado por estar especificado aquí.
