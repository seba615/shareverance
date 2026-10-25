# Python: OCR de comprobantes

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado y alcance

En E4.2 se incorpora el motor OCR local Tesseract (`tesseract-ocr` y `tesseract-ocr-spa` en Debian Bookworm) en el microservicio FastAPI Python, compartiendo el servicio sin agregar contenedores adicionales.

Extrae únicamente fecha, importe ARS y descripción. No proveedor, razón social, CUIT ni moneda. La descripción utilizable se normaliza eliminando saltos de línea y espacios redundantes (hasta 500 caracteres); se utiliza `Varios` como valor alternativo determinista si la descripción queda vacía, supera los 500 caracteres o carece de texto legible.

## Flujo

1. React sube JPEG/PNG a Java con organización mediante multipart (E4.1).
2. Java valida sesión, permisos, contenido, tamaño y dimensiones; asigna nombre y guarda en volumen persistente (E4.1).
3. Java solicita OCR por HTTP interno a `POST /internal/v1/ocr` con `request_id`, `organization_id`, `receipt_id` y `storage_key` controlada.
4. Python valida autenticación Bearer (`PYTHON_CLIENT_SECRET`), resuelve el archivo dentro de `/data/receipts/org_<id>/` y rechaza path traversal y enlaces simbólicos.
5. Python abre el archivo en modo lectura, ejecuta Tesseract OCR y normaliza fecha, importe y descripción.
6. Devuelve campos normalizados, advertencias y estado a Java; Java los entrega a React mediante `POST /api/v1/organizations/{id}/receipts/{receiptId}/ocr`.
7. React precarga los campos en el formulario de carga de gastos, permitiendo que el usuario los revise, edite o complete manualmente. Al enviar el alta de gasto, se vincula el comprobante mediante `receiptService.linkReceiptToExpense`.

## Volumen y validación

Un volumen persistente compartido de Compose (`receipts_data`) montado en `/data/receipts`: Java lectura/escritura, Python solo lectura (incorporado en E4.1). Una imagen hasta 10 MiB; JPEG/PNG válidos por decodificación y contenido, no solo extensión. Java comprueba el límite de 50 megapíxeles leyendo dimensiones antes de decodificar y limita los bytes realmente leídos. Rechaza enlaces simbólicos en los componentes de la ruta y referencias fuera del directorio de la organización; revalida al abrir la descarga. No acepta nombres de archivo aportados como rutas.

El servidor conserva referencia y organización; la ruta relativa incluye un identificador generado y no datos personales. No permitir que el front invoque Python o elija rutas. Un contenedor recreado no debe borrar imágenes.

Python compara componentes de ruta: `org_1` no autoriza `org_10` ni `org_11`. Comprueba enlaces simbólicos en el archivo, directorios, raíz y padres antes de resolver la ruta; después comprueba nuevamente su pertenencia al directorio de la organización. El volumen debe permanecer bajo control de Java, con Python en sólo lectura; no se acredita protección frente a sustituciones concurrentes de directorios por otro proceso con permisos de escritura.

## Resultado

Contrato en [contratos_api.md](contratos_api.md). Campos no reconocidos son null y llevan advertencia en `warnings`; no inventar fecha ni importe. La moneda se conoce como ARS. No emitir puntuaciones de confianza ficticias. OCR no confirma gastos ni decide categoría/reparto. Un timeout, archivo ilegible o error del motor permite continuar con carga manual. Estados de resultado:
- `OK`: fecha e importe detectados.
- `PARTIAL`: solo uno de los dos campos (fecha o importe) detectado.
- `FAILED`: no se pudo reconocer fecha ni importe, o el archivo es ilegible / vacío.

El reconocimiento de importes excluye fragmentos de fecha antes de buscar números decimales y no convierte valores negativos en positivos. Una fecha con puntos sin monto no produce un importe; se devuelve `amount: null` y su advertencia.

## Motor y parámetros

En E4.2 se seleccionó Tesseract local (paquetes `tesseract-ocr` y `tesseract-ocr-spa`, licencia Apache 2.0). No envía comprobantes a proveedores externos y no requiere conectividad a internet para procesar imágenes. El tiempo límite de lectura interna está configurado en 15 segundos (`python.service.ocr-read-timeout-ms`).

La mejora de lectura utiliza Pillow 13.0.0, declarado en `pyproject.toml` y `uv.lock`. Primero prueba la imagen original; si reconoce fecha e importe conserva ese resultado. En caso contrario prepara una copia temporal con orientación EXIF, escala de grises, contraste y enfoque suave. Reduce dimensiones a 2400 antes de ampliar imágenes pequeñas, con lado final máximo 3200; rechaza fuentes superiores a 50 MP antes de preparar la copia. Prueba segmentación de bloque/texto disperso y rotaciones de 90/270/180 grados mientras queda presupuesto.

Los subprocesos comparten un presupuesto de 12 segundos y cada intento tiene hasta 4 segundos; no se renueva el presupuesto por variante. La preparación y codificación también consumen tiempo: no constituye una garantía de latencia total para cualquier imagen de 50 MP. Java conserva su timeout de 15 segundos. Se selecciona el texto con más campos válidos según el normalizador existente; ese criterio no es una puntuación de confianza ni asegura exactitud. Los originales se conservan intactos y las copias temporales se eliminan al terminar. Los errores del motor no registran texto del comprobante ni su stderr completo. Desenfoque fuerte, perspectiva o caracteres ilegibles pueden seguir produciendo un resultado parcial o fallido; no se inventan datos faltantes.

Para fotos con fondo oscuro se busca un componente claro dominante en una vista reducida y se recorta con margen sólo si cumple límites de área y predominio. Ante fondo claro, regiones ambiguas o falta de papel dominante se conserva el encuadre. Se prueban además copias binarias usando percentiles 85/90 de luminancia del papel, dentro del mismo presupuesto. Entre variantes con igual número de campos reconocidos se prefiere un importe repetido en el texto; la repetición sólo sirve de desempate, no certifica exactitud. Una lectura alternativa con ambos campos pero importe aislado no detiene inmediatamente la búsqueda.

El normalizador admite espacios entre separador decimal y centavos y variantes acotadas de la etiqueta TOTAL (letras separadas, cero por O y prefijos degradados). No sustituye dígitos de importes ni fechas. Ignora fechas de inicio de actividades, vencimiento y CAE; sin TOTAL requiere moneda explícita y excluye líneas fiscales para no convertir números aislados en gastos. La revisión humana sigue siendo obligatoria.

<a id="limpieza-operativa"></a>

### Limpieza operativa de temporales y huérfanos

Relacionado con [#83](https://github.com/seba615/shareverance/issues/83). Conservar comprobantes vinculados a gastos activos o anulados. No purgar tablas de negocio. `scripts/cleanup_receipts.py` y sus wrappers proporcionan auditoría predeterminada y aplicación manual explícita; no hay tarea automática en el backend web.

Retención: 24 horas predeterminadas, mínimo 1 hora. Para una fila sin gasto, `created_at` debe ser anterior al corte y el archivo, si existe, también debe tener `mtime` anterior. Una fila antigua con archivo recién modificado queda protegida. Un huérfano sin fila usa `mtime`. Ausencia de archivo no habilita eliminar una fila vinculada. Rutas o referencias duplicadas/ambiguas provocan fallo antes de borrar.

El proyecto Compose es obligatorio y se valida por etiquetas y montajes, reutilizando la resolución de backup. Todas las operaciones de limpieza comparten con backup/restauración un contenedor de bloqueo por proyecto. La auditoría crea/retira ese bloqueo temporal, pero no modifica comprobantes ni filas; sobre un entorno activo es una fotografía informativa, no una autorización ni instantánea transaccional de la próxima aplicación.

`--apply` exige Java/Python y todos los contenedores que monten comprobantes detenidos. No los detiene/reinicia silenciosamente. El operador debe mantener cerrados otros escritores SQL/host y no iniciar aplicaciones durante la ventana. Se adquiere ACCESS EXCLUSIVE en receipts en una sesión SQL mantenida hasta COMMIT: una asociación concurrente se serializa y, si ya se confirmó antes del bloqueo, queda protegida. Espera máxima de adquisición 5 segundos; timeout SQL 30 segundos e inactividad de sesión 120 segundos. Ante timeout/error no se informa APPLIED.

Los candidatos se recalculan bajo bloqueo. La desactivación SQL exige ID válido, estado activo, `expense_id IS NULL` y antigüedad; se comprueba la cantidad afectada. El borrado usa descriptores de directorio con O_NOFOLLOW en cada componente, incluidos raíz/padres, y comprueba tamaño, inode y mtime en nanosegundos antes de borrar. Rechaza symlinks, archivos especiales y cambios de inventario. No elimina directorios vacíos. El worker funciona en Linux dentro de la imagen; no se exige soporte dir_fd del Python nativo Windows. Ninguna comprobación sustituye el control de accesos al volumen: procesos externos con escritura deben permanecer cerrados.

Base y filesystem no son atómicos. Antes de aplicar, disponer de respaldo consistente. Un error SQL previo al borrado no elimina archivos; un error de filesystem, desconexión o fallo de COMMIT después de borrar puede dejar archivos ausentes y filas sin desactivar. El proceso termina con error; las aplicaciones deben seguir detenidas mientras se cotejan archivos/filas y se recupera desde respaldo. No se declara rollback distribuido. Un bloqueo residual tras caída requiere inspección manual; no se retira un bloqueo ajeno.

Comandos de operación en [README](../README.md#limpieza-de-comprobantes-temporales-y-huérfanos). Ejemplo con entorno aislado ya iniciado: `scripts/cleanup-receipts.ps1 -Project shareverance-predictor-demo -Json`. Aplicar sólo en ventana de mantenimiento con ese proyecto explícito; para iniciar/detener la demo ver [P1-D](python_predictor.md#entorno-demo-predictor), conservando volúmenes. Esta tarea no requiere iniciar el entorno habitual.

Verificación aislada reproducible, desde raíz con Docker Desktop Linux y Python 3 local (biblioteca estándar; crea sus propios recursos):

```powershell
docker build -t shareverance-backup-tools:local -f scripts/backup-tools.Dockerfile .
if ($LASTEXITCODE -ne 0) { throw "No se construyó la imagen" }
python scripts/verify_cleanup_receipts.py
```

El verificador utiliza PostgreSQL 17.11 en tmpfs, sin puertos publicados, volumen y contenedores únicos etiquetados. Sólo retira recursos propios tras verificar etiquetas; nunca selecciona la base de desarrollo. `--keep` conserva recursos propios de una ejecución satisfactoria para probar wrappers; deben retirarse después comprobando identidad y etiquetas.

## Pruebas

Comprobantes nítidos, girados, borrosos y con varias fechas/importes; total frente a subtotal/cambio; coma decimal y separadores de miles; descripción extensa/ausente; campos parciales; imagen corrupta, tipo falso, exceso de bytes/píxeles, traversal y organización ajena; timeout; revisión y confirmación sin duplicación; reinicio y persistencia. Medir acierto por campo sobre muestras con resultado esperado y registrar las limitaciones del motor.


### Entorno único de presentación

Para el recorrido integrado se utiliza sólo localhost:5176 y la organización Demo presentación Shareverance, con las cuentas sintéticas ADMIN/USER de la [guía de presentación](demo_presentacion.md). scripts/presentation-demo.ps1 prepare carga/verifica datos por Java en el proyecto aislado ya existente; start/check/stop sirven para el día de presentación y conservan volúmenes. Incluye historial con fechas desplazadas de forma explícita, individuales/iguales, categoría histórica inactiva y comprobante sintético. No modifica el CSV, contratos o algoritmo, no crea otra base y no acredita aceptación integral. Los conjuntos técnicos anteriores se preservan para regresión.
