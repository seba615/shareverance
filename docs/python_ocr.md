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

Conservar comprobantes asociados incluso tras anulación. No limpiar tablas de negocio. La limpieza de archivos sin asociación necesita una política explícita; 24 horas sigue siendo un valor propuesto, no una tarea automática habilitada.

## Pruebas

Comprobantes nítidos, girados, borrosos y con varias fechas/importes; total frente a subtotal/cambio; coma decimal y separadores de miles; descripción extensa/ausente; campos parciales; imagen corrupta, tipo falso, exceso de bytes/píxeles, traversal y organización ajena; timeout; revisión y confirmación sin duplicación; reinicio y persistencia. Medir acierto por campo sobre muestras con resultado esperado y registrar las limitaciones del motor.
