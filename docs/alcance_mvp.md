# Alcance y reglas del MVP 1

Fecha de revisión: 25/10/2026. Base inspeccionada: `1486354` (main; PR #87, #89 y #90 integrados).

Revisión documental estática: distingue implementación observada, evidencia de ejecuciones anteriores y aceptación pendiente. No se ejecutaron pruebas funcionales nuevas en esta consolidación.

## Síntesis vigente

MVP ARS, calendario Buenos Aires, reparto individual/iguales. Están implementadas las previsiones mensual y 4/5/6 meses con gráfico histórico/proyección; log_trend es el modo predeterminado, mock y SES opciones explícitas. La UI muestra estimación breve y aviso de simulación cuando corresponde; método, versión y calidad permanecen en API/documentación. Presentación con V1 sintético; aceptación humana e integral pendiente, sin exigir datos reales.


## Producto

Shareverance registra y distribuye gastos de hogares, consorcios y universidades. Una cuenta puede pertenecer a varias organizaciones con rol ADMIN o USER en cada una. Cada organización constituye un ámbito independiente de información.

## Funciones y vistas

| Función | Vista | Estado de implementación |
| --- | --- | --- |
| Registro, login, restauración y cierre de sesión | Acceso | Código existente |
| Crear, listar y seleccionar organizaciones | Mis organizaciones | Java acepta solo ARS y lista organizaciones activas de membresías activas; aceptación integral pendiente |
| Administrar miembros y categorías | Miembros / Categorías | E2.1 incorpora administración de miembros, alta, edición y desactivación de categorías; aceptación integral pendiente |
| Cargar, editar y anular gastos | Formulario / Detalle | E2.1 incorpora alta manual/listado y E2.2 detalle/edición/anulación; aceptación integral pendiente |
| Consultar por período, categoría y estado | Historial paginado | E2.2 incorpora filtros y paginación servidor; aceptación integral pendiente |
| Consultar gastos por fecha | Calendario | E2.3 incorpora calendario mensual navegable con totales diarios y detalle por fecha; aceptación integral pendiente |
| Precargar fecha, importe y descripción | Carga con comprobante | E4.1 incorpora volumen privado/almacenamiento y E4.2 motor OCR Tesseract, normalización y precarga editable; aceptación integral pendiente |
| Totales, comparación mensual, evolución y categorías | Panel analítico | E5.2 incorpora consulta Java, gráficos SVG y tablas desde analítica Python real; aceptación integral pendiente |
| Estimar próximo mes y previsión de 4/5/6 meses | Predicciones (pestaña dedicada) | E6.2/P5-H incorporan consulta Java, insuficiencia, método/simulado y tablas por categorías; gráfico combinado implementado; demostración sintética con evidencia parcial y aceptación humana pendiente |
| Consultar participación por miembro | Detalle / resumen individual | Mi resumen: participación del usuario conectado, período y categorías implementados; aceptación humana pendiente |

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

El calendario debe permitir consultar gastos por fecha y abrir su detalle. El calendario mensual con totales diarios, navegación y consulta de detalle está implementado según [front.md](front.md), con aceptación integral pendiente; no agrega recurrencias.

Analítica usa fecha del gasto y solo gastos vigentes; diferencia entre gasto organizacional y participación individual. Un mes sin cobertura conocida no se transforma automáticamente en cero. Predicción del mes siguiente al último cerrado con cobertura confiable.

Entrega demostrativa del predictor: utilizar exclusivamente el CSV V1 sintético disponible; no se exige obtener datos reales para finalizar la presentación. Se debe poder cargar y conciliar datos de demostración por Java en una base aislada con usuario y organización, consultar previsiones mensuales de 4/5/6 meses desde un mismo corte y mostrar una estimación comprensible, con aviso de simulación si se configura mock. Los límites de precisión, método y versión se documentan sin mostrarlos como avisos técnicos al usuario. No afirmar representatividad de hogares argentinos ni confianza calibrada. Las comparaciones offline sobre V1 son evidencia técnica sintética, no aceptación de precisión real.

P5-H incorpora rutas Python/Java y tablas React multimes; PR #87 corrige compilación, horizonte enviado y autorización GET. PR #89 incorpora el gráfico histórico frente a previsión. La revisión humana y aceptación integral siguen pendientes. El [recorrido reproducible y evidencia](evidencia/demo-sintetica/README.md) registra comprobaciones anteriores sin certificar todos los criterios. Obtener datos reales sería una ampliación futura, no un bloqueo de la presentación sintética.

E3.1 registra cobertura explícita por organización y mes cerrado, declarada por ADMIN activo mediante Java y retirable lógicamente sin borrar el registro. No infiere cobertura por el primer gasto, por crear una organización ni por ausencia de filas. Las vistas SQL usan contexto de organización limitado a la transacción; sin contexto válido no devuelven datos. E3.2 implementa el consumidor Python y retira el callback; E5 incorpora analítica desde las vistas. El predictor predeterminado es tendencia log-lineal experimental (log_trend), con mock y SES opcionales explícitos y aceptación predictiva pendiente. [Método, evidencia y límites](evidencia/tendencia/README.md).

OCR opcional: fecha, importe y descripción de hasta 500 caracteres; valor alternativo `Varios` cuando la descripción no es utilizable. No extrae proveedor, razón social ni moneda. El usuario revisa y confirma antes del alta. Un fallo de OCR permite continuar manualmente.

## Seguridad y entrega

Entrega local con Docker Compose. Frontend consume Java. Python es interno y sus llamadas se autentican. Java autoriza organización y operación antes de invocarlo. Python solo consulta vistas autorizadas mediante credencial propia de lectura. Adjuntos privados en volumen persistente compartido: Java escribe, Python lee.

Logs técnicos mínimos con correlación, sin secretos, contraseñas, tokens ni contenido de comprobantes. No se exige auditoría persistida de negocio. Datos históricos no se purgan como rutina. Restauración debe recuperar base y comprobantes coherentes: E7.1 implementa el procedimiento de respaldo y restauración consistente mediante `scripts/backup.*` y `scripts/restore.*`, con verificación cruzada entre registros de PostgreSQL y archivos en el volumen de comprobantes.

## Precisiones todavía necesarias

Las decisiones de arquitectura no resuelven por sí solas estos parámetros: retención de logs y recuperación de contraseña/verificación de email. Para la limpieza de archivos sin asociación, se implementa la política formal de retención (24 horas predeterminadas, mínimo 1 hora) y procedimiento seguro mediante `scripts/cleanup_receipts.py` (`scripts/cleanup-receipts.*`), con auditoría predeterminada y aplicación sólo en mantenimiento con escritores detenidos, bloqueo SQL y exclusión con respaldo/restauración. La retención exige created_at y mtime antiguos; la eliminación de archivos huérfanos/expirados y desactivación lógica en base de datos (`is_active = FALSE`) preservan comprobantes asociados a gastos (activos o anulados) y tablas de negocio ([python_ocr.md](python_ocr.md)). P5 documentó históricamente la no promoción de SES 0.3; el modo vigente es `PREDICTOR_MODE=log_trend`, con mock/SES explícitos ([Python](python_predictor.md)). La aceptación predictiva no está cerrada: P2 mantiene umbrales propuestos y faltan criterios multihorizonte y evaluación final acreditada sobre un conjunto adecuado. E7.1 incorpora herramientas de respaldo/restauración y pruebas aisladas; la aceptación operativa y el cierre permanecen manuales ([postgre.md](postgre.md)). E4.1/E4.2 concretan motor Tesseract, límite de 50 MP, reglas de `Varios` y tiempos de OCR en [python_ocr.md](python_ocr.md); la precisión en un conjunto representativo sigue pendiente de evaluación. La autenticación Java→Python se concreta en [contratos_api.md](contratos_api.md). El aislamiento SQL transaccional y la fuente de cobertura se concretan en E3.1 y [postgre.md](postgre.md). Calendario mensual y gráficos SVG con tablas ya tienen implementación, sin acreditar aceptación integral. Ver tareas y evidencia del [roadmap](road_map.md); una especificación por sí sola no demuestra implementación.


## Resumen individual (#95)

«Mi resumen» muestra al usuario conectado su total de partes asignadas en gastos vigentes de la organización, cantidad de gastos y desglose por categoría. Fechas inclusivas opcionales, todo el historial al omitirlas, categorías inactivas históricas conservadas y cero explícito sin participaciones. USER consulta su propia participación; ADMIN puede seleccionar otro miembro de la organización, incluidos inactivos históricos. No incorpora administración de cobertura, deuda, pagos ni liquidaciones. Se suma la parte asignada, independientemente del autor o titular del gasto. Implementación y ejecución se distinguen en el roadmap.


### Entorno único de presentación

Para el recorrido integrado se utiliza sólo localhost:5176 y la organización Demo presentación Shareverance, con las cuentas sintéticas ADMIN/USER de la [guía de presentación](demo_presentacion.md). scripts/presentation-demo.ps1 prepare carga/verifica datos por Java en el proyecto aislado ya existente; start/check/stop sirven para el día de presentación y conservan volúmenes. Incluye historial con fechas desplazadas de forma explícita, individuales/iguales, categoría histórica inactiva y comprobante sintético. No modifica el CSV, contratos o algoritmo, no crea otra base y no acredita aceptación integral. Los conjuntos técnicos anteriores se preservan para regresión.
