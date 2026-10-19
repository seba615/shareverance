# Python: OCR de comprobantes

Fecha de revisión: 17/10/2026. Base de implementación: `bc7cb4d7d0e9d2b391878f97b4565ae49101347a`.

Esta especificación define el comportamiento requerido del MVP 1. La sección de estado distingue código existente de trabajo pendiente. La revisión es estática; no acredita ejecución de pruebas.


## Estado y alcance

No existe implementación OCR en la base revisada. Comparte el servicio FastAPI con analítica/predicción; separar módulos y pruebas, sin introducir un contenedor adicional como requisito.

Extraer únicamente fecha, importe ARS y descripción. No proveedor, razón social, CUIT ni moneda. La descripción utilizable no supera 500 caracteres; usar `Varios` si no puede representarse dentro de esa regla. Precisar antes de implementar si varias líneas siempre producen ese valor o si pueden normalizarse.

## Flujo

1. React sube JPEG/PNG a Java con organización mediante multipart.
2. Java valida sesión, permisos, contenido, tamaño y dimensiones; asigna nombre y guarda en volumen persistente.
3. Java solicita OCR por HTTP interno con request_id, organization_id y referencia controlada.
4. Python valida llamada y resuelve el archivo dentro del directorio permitido; no descarga URLs ni recibe rutas libres.
5. Python abre el archivo en modo lectura, ejecuta OCR y normaliza campos.
6. Devuelve campos y advertencias a Java; Java los entrega al formulario.
7. El usuario corrige y confirma; Java guarda el gasto y asocia el comprobante.

## Volumen y validación

Un volumen persistente compartido de Compose (`receipts_data`) montado en `/data/receipts`: Java lectura/escritura, Python solo lectura (incorporado en E4.1). Una imagen hasta 10 MiB; JPEG/PNG válidos por decodificación y contenido, no solo extensión. El límite de píxeles fijado en 50 megapíxeles. Rechazar enlaces simbólicos o referencias que permitan escapar del directorio y evitar acceso a archivos de otra organización. No aceptar nombres de archivo aportados como rutas.

El servidor conserva referencia y organización; la ruta relativa incluye un identificador generado y no datos personales. No permitir que el front invoque Python o elija rutas. Un contenedor recreado no debe borrar imágenes.

## Resultado

Contrato en [contratos_api.md](contratos_api.md). Campos no reconocidos son null y llevan advertencia; no inventar fecha ni importe. La moneda se conoce como ARS. No emitir puntuaciones de confianza ficticias. OCR no confirma gastos ni decide categoría/reparto. Un timeout, archivo ilegible o error del motor permite carga manual.

## Motor y parámetros pendientes

Seleccionar motor local o servicio externo mediante prueba representativa; documentar dependencias, licencia operativa y necesidad de conexión cuando se elija. No enviar comprobantes a un proveedor externo como consecuencia implícita de instalar una biblioteca. Definir tiempos máximos y política de concurrencia según la prueba.

Conservar comprobantes asociados incluso tras anulación. No limpiar tablas de negocio. La limpieza de archivos sin asociación necesita una política explícita; 24 horas sigue siendo un valor propuesto, no una tarea automática habilitada.

## Pruebas

Comprobantes nítidos, girados, borrosos y con varias fechas/importes; total frente a subtotal/cambio; coma decimal y separadores de miles; descripción extensa/ausente; campos parciales; imagen corrupta, tipo falso, exceso de bytes/píxeles, traversal y organización ajena; timeout; revisión y confirmación sin duplicación; reinicio y persistencia. Medir acierto por campo sobre muestras con resultado esperado y registrar las limitaciones del motor.
