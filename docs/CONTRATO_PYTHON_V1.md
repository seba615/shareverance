# Shareverance — Contrato del microservicio Python

**Versión del contrato:** 1  
**Alcance:** analítica mensual y predicción del próximo mes por organización y categoría.  
**Formato:** HTTP con JSON UTF-8.  
**Estado:** especificación para implementación; no implica funcionalidad ya desarrollada.

## 1. Objetivo y responsabilidades

Este contrato permite desarrollar Java, Python y la base de datos de forma independiente. Java prepara el historial y Python procesa solicitudes sin consultar PostgreSQL.

| Componente | Responsabilidad |
| --- | --- |
| Base de datos | Persistencia, restricciones, migraciones y carga reproducible de datos de prueba. |
| Java | Autorización, aislamiento por organización, consulta de gastos activos, agregación mensual, envío del JSON y manejo de la respuesta. |
| Python | Validación del contrato, analítica, predicción y respuestas de error consistentes. |
| Frontend, mediante Java | Presentación de resultados, estados y avisos de simulación. |

El nombre de una tabla o un cambio de implementación SQL no modifica este contrato si Java conserva la estructura y el significado de los datos enviados.

## 2. Operaciones

| Método y ruta | Función |
| --- | --- |
| `GET /health` | Comprobar disponibilidad del servicio; no mide precisión predictiva. |
| `POST /api/v1/analytics/monthly` | Comparar los últimos dos meses completos, globalmente y por categoría. |
| `POST /api/v1/predictions/monthly` | Predecir el próximo mes, globalmente y por categoría. |

URL interna de Docker: `http://python-service:8000`.

Todas las solicitudes POST usan `Content-Type: application/json`. No se envían datos personales, comprobantes ni conexiones a la base. La autenticación del usuario y sus permisos corresponden a Java. La exposición externa del servicio Python y su autenticación entre servicios requieren una definición de infraestructura separada.

## 3. Entrada compartida

| Campo | Tipo | Regla |
| --- | --- | --- |
| `contract_version` | string | Obligatorio; valor exacto `"1"`. |
| `request_id` | string UUID | Obligatorio; generado por Java y reproducido por Python. |
| `organization_id` | integer | Obligatorio; positivo y hasta `9007199254740991`. |
| `currency` | string | Obligatorio; `"ARS"` para esta versión. |
| `categories` | array | Entre 0 y 100 categorías, sin identificadores duplicados. |
| `categories[].category_id` | integer | Positivo y hasta `9007199254740991`; pertenece a la organización. |
| `categories[].available_from_month` | string | Mes desde el que la categoría tiene cobertura completa en el historial enviado; formato `YYYY-MM`. |
| `history` | array | Entre 0 y 60 meses completos, consecutivos, únicos y ordenados. |
| `history[].month` | string | Mes calendario válido, formato `YYYY-MM`, años 0001–9999. |
| `history[].category_totals` | array | Un registro por categoría disponible en ese mes. |
| `category_totals[].category_id` | integer | Debe existir en `categories`; no se repite dentro del mes. |
| `category_totals[].amount_minor` | integer | Importe en centavos, entre 0 y `9007199254740991`. |
| `target_month` | string | Obligatorio únicamente para predicción; mes calendario válido inmediatamente posterior al último mes del historial cuando exista historial. |

No se aceptan campos desconocidos en solicitudes. Los enteros son estrictos: no se admiten strings numéricos, booleanos ni valores como `100.0`. El total general de cada mes también debe respetar el límite monetario. El cuerpo tiene un máximo de 1 MiB en UTF-8; superar ese límite produce HTTP 413.

Cada objeto de `categories` contiene únicamente `category_id` y `available_from_month`. Java obtiene los nombres de las categorías para su presentación; Python trabaja exclusivamente con sus identificadores.

Las listas de categorías se envían ordenadas por `category_id`. El orden de los campos de un objeto JSON no es relevante.

### 3.1. Preparación del historial en Java

1. Seleccionar una única organización autorizada.
2. Incluir gastos con `EXPENSE.is_active = true`.
3. Agrupar por año y mes de `EXPENSE.expense_date` y por `EXPENSE.category_id`.
4. Sumar `EXPENSE.amount` una sola vez por gasto. No utilizar una unión con `EXPENSE_SPLIT` que multiplique filas.
5. Convertir `BigDecimal` a centavos exactamente, sin redondear valores inválidos ni pasar por `double`.
6. Incluir solamente meses completos y con cobertura confiable; excluir el mes en curso.
7. Incluir categorías desactivadas cuando tienen gastos históricos activos.
8. Completar los ceros de los meses cubiertos sin gastos.

El total organizacional es la suma de todas las categorías incluidas en cada mes. No se envía un total general adicional. Si el modelo permite gastos sin categoría, Java deberá asignarles una categoría persistida de «Sin categoría»; no puede omitir esos gastos ni inventar identificadores reservados.

### 3.2. Cobertura del historial

`available_from_month` indica cobertura confiable, no necesariamente creación de la categoría. Con historial no vacío, debe estar dentro del período enviado. Antes de ese mes, la categoría se omite; desde ese mes, aparece en todos los meses, incluso con importe cero.

El historial global declara meses completos: no puede haber gastos existentes excluidos por falta de cobertura de una categoría. Si esto ocurre, Java debe recortar el período a meses confiables o corregir los datos antes de enviar la solicitud. Python puede validar la coherencia estructural, pero no comprobar que faltan registros en la base.

Sin historial se exige `categories: []`. Una organización sin categorías puede enviar meses con `category_totals: []`: esos meses representan gasto total cero confirmado.

La política para determinar cobertura confiable en organizaciones reales debe documentarse en la capa de datos. No se infiere automáticamente a partir de la fecha de creación de la organización ni del primer gasto.

### 3.3. Ejemplo completo de solicitud predictiva

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "target_month": "2026-10",
  "categories": [
    {"category_id": 10, "available_from_month": "2026-04"},
    {"category_id": 20, "available_from_month": "2026-04"}
  ],
  "history": [
    {"month": "2026-04", "category_totals": [{"category_id": 10, "amount_minor": 10000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-05", "category_totals": [{"category_id": 10, "amount_minor": 10500000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-06", "category_totals": [{"category_id": 10, "amount_minor": 11000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-07", "category_totals": [{"category_id": 10, "amount_minor": 11500000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-08", "category_totals": [{"category_id": 10, "amount_minor": 12000000}, {"category_id": 20, "amount_minor": 5000000}]},
    {"month": "2026-09", "category_totals": [{"category_id": 10, "amount_minor": 12500000}, {"category_id": 20, "amount_minor": 5000000}]}
  ]
}
```

Para solicitar analítica se utiliza el mismo objeto sin `target_month`. El historial real de pruebas podrá contener 24 meses; el ejemplo se limita a seis para facilitar su lectura.

## 4. Salida de analítica

Compara los últimos dos meses recibidos. No utiliza el primer y el último mes de todo el historial.

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "status": "OK",
  "simulated": false,
  "comparison": {"previous_month": "2026-08", "current_month": "2026-09"},
  "organization": {
    "status": "OK",
    "previous_amount_minor": 17000000,
    "current_amount_minor": 17500000,
    "change_amount_minor": 500000,
    "change_percentage": 2.94,
    "direction": "INCREASING"
  },
  "categories": [
    {"category_id": 10, "status": "OK", "previous_amount_minor": 12000000, "current_amount_minor": 12500000, "change_amount_minor": 500000, "change_percentage": 4.17, "direction": "INCREASING"},
    {"category_id": 20, "status": "OK", "previous_amount_minor": 5000000, "current_amount_minor": 5000000, "change_amount_minor": 0, "change_percentage": 0.0, "direction": "UNCHANGED"}
  ]
}
```

### 4.1. Reglas de cálculo

- `change_amount_minor = current_amount_minor - previous_amount_minor`.
- `change_percentage = change_amount_minor / previous_amount_minor × 100`.
- Porcentaje redondeado a dos decimales mediante redondeo decimal `ROUND_HALF_UP`.
- Si el importe anterior es cero, el porcentaje es `null`, incluso cuando ambos importes son cero; la variación monetaria y la dirección siguen disponibles.
- `direction`: `INCREASING` si la diferencia es positiva, `DECREASING` si es negativa y `UNCHANGED` si es cero.
- La dirección describe una comparación mensual observada; no demuestra una tendencia sostenida ni predice el futuro.
- Cada categoría requiere cobertura en los dos meses comparados.
- Una serie insuficiente utiliza `status: "INSUFFICIENT_HISTORY"` y todos sus campos de cálculo en `null`.
- Con menos de dos meses globales, `comparison` es `null` y todos los resultados son insuficientes.
- La respuesta contiene exactamente las categorías solicitadas, ordenadas por identificador. Java conserva los nombres de categoría en su propio módulo y relaciona los resultados por `category_id`. Los nombres no se envían a Python ni se devuelven en sus respuestas.

## 5. Salida de predicción

La estimación general y las estimaciones por categoría son independientes. La general se calcula sobre la serie de totales de la organización; cada categoría utiliza su propia serie desde `available_from_month`.

No existe una garantía de que la suma de las predicciones por categoría coincida con la predicción general. Java debe respetar esa diferencia y no recalcular ni reemplazar la estimación general con la suma de categorías.

Ejemplo de estructura, con importes ilustrativos que no son resultados de un modelo ejecutado:

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "organization_id": 1,
  "currency": "ARS",
  "target_month": "2026-10",
  "status": "OK",
  "simulated": false,
  "organization": {
    "status": "OK",
    "estimated_amount_minor": 17400000,
    "months_used": 6,
    "required_months": 6,
    "method": "simple_exponential_smoothing",
    "model_version": "1"
  },
  "categories": [
    {"category_id": 10, "status": "OK", "estimated_amount_minor": 12300000, "months_used": 6, "required_months": 6, "method": "simple_exponential_smoothing", "model_version": "1"},
    {"category_id": 20, "status": "OK", "estimated_amount_minor": 5000000, "months_used": 6, "required_months": 6, "method": "simple_exponential_smoothing", "model_version": "1"}
  ]
}
```

### 5.1. Reglas de predicción

| Aspecto | Definición |
| --- | --- |
| Horizonte | Un mes, inmediatamente posterior al último mes recibido. |
| Mínimo inicial | Seis meses completos por serie; regla operativa, no garantía de precisión. |
| Historial empleado | Todos los meses disponibles de cada serie dentro de la solicitud. |
| Resultado | Entero no negativo en centavos, redondeado con `ROUND_HALF_UP`. |
| Serie insuficiente | `estimated_amount_minor: null`, estado `INSUFFICIENT_HISTORY`; nunca cero como sustituto de falta de datos. |
| `months_used` | Meses empleados en un resultado `OK`; cero cuando no se ejecutó el modelo por historial insuficiente. |
| `required_months` | Mínimo exigido por la versión del modelo, inicialmente seis. |
| Método y versión | Strings obligatorios en cada resultado; Java los trata como metadatos, sin condicionar su deserialización a un método concreto. |

El modelo candidato inicial es el suavizado exponencial simple. Su configuración y evaluación se documentan en el módulo predictivo; este contrato no fija el algoritmo ni sus parámetros. Un cambio de método compatible con los mismos campos no exige cambiar la versión del contrato, pero sí identificar la nueva versión del modelo.

No se devuelven intervalos ni porcentajes de confianza hasta implementar y evaluar un procedimiento que los sustente. Los resultados expresan ARS nominales; no se ajustan por inflación. El incremento de gasto no identifica por sí solo mayor consumo ni su causa.

Un fallo numérico, resultado no finito o fuera del límite monetario es un fallo interno del cálculo. No debe etiquetarse como historial insuficiente ni corregirse silenciosamente.

### 5.2. Ejemplo de resultado individual insuficiente

```json
{
  "category_id": 30,
  "status": "INSUFFICIENT_HISTORY",
  "estimated_amount_minor": null,
  "months_used": 0,
  "required_months": 6,
  "method": "simple_exponential_smoothing",
  "model_version": "1"
}
```

Con historial vacío la solicitud sigue siendo válida y devuelve insuficiencia. `target_month` continúa siendo obligatorio; Java selecciona el mes siguiente al último mes cerrado según su calendario de negocio. Python no puede comprobar esa relación con un historial vacío.

## 6. Estados de resultados

Estas reglas se aplican tanto a analítica como a predicción:

| Estado general | Condición |
| --- | --- |
| `OK` | La organización y todas las categorías tienen resultado. |
| `PARTIAL` | Al menos un resultado es `OK` y al menos uno es `INSUFFICIENT_HISTORY`. |
| `INSUFFICIENT_HISTORY` | Ningún resultado tiene información suficiente. |

Los resultados individuales utilizan solamente `OK` o `INSUFFICIENT_HISTORY`. Se conserva la misma estructura de campos en resultados insuficientes; los valores no calculables se expresan como `null`.

La insuficiencia de historial es un resultado de negocio con HTTP 200. Los errores de implementación o infraestructura no se presentan como resultados parciales.

## 7. Errores HTTP

```json
{
  "contract_version": "1",
  "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "El historial contiene meses duplicados.",
    "details": [
      {"field": "history", "code": "DUPLICATE_MONTH"}
    ]
  }
}
```

| HTTP | `error.code` | Significado |
| --- | --- | --- |
| 413 | `PAYLOAD_TOO_LARGE` | Cuerpo superior a 1 MiB. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Tipo de contenido distinto de JSON. |
| 422 | `INVALID_REQUEST` | JSON mal formado, campo inválido o violación de reglas del historial. |
| 500 | `INTERNAL_ERROR` | Fallo inesperado o error de cálculo. |
| 503 | `SERVICE_UNAVAILABLE` | Servicio temporalmente no preparado. |

`details` es siempre una lista y puede estar vacía. Si no puede obtenerse un UUID válido del cuerpo, `request_id` es `null`. Los mensajes son informativos; Java decide mediante los códigos, no comparando textos. No se exponen trazas ni detalles internos en respuestas.

Códigos de detalle iniciales: `REQUIRED_FIELD`, `UNKNOWN_FIELD`, `INVALID_TYPE`, `INVALID_VALUE`, `UNSUPPORTED_CONTRACT_VERSION`, `DUPLICATE_MONTH`, `NON_CONSECUTIVE_MONTHS`, `UNSORTED_HISTORY`, `DUPLICATE_CATEGORY`, `UNKNOWN_CATEGORY`, `INVALID_COVERAGE`, `MISSING_CATEGORY_TOTAL`, `INVALID_TARGET_MONTH`, `AMOUNT_OUT_OF_RANGE` y `INVALID_JSON`.

Python debe adaptar los errores de validación de FastAPI al formato indicado, en lugar de exponer su formato por defecto.

## 8. Comportamiento del cliente Java

- Configurar la URL mediante `PYTHON_SERVICE_URL`.
- Usar inicialmente un tiempo máximo de conexión de 2 segundos y de respuesta de 10 segundos, configurables.
- Deserializar campos por nombre, sin depender del orden de objetos JSON.
- Comprobar coincidencia de versión, `request_id`, organización, moneda y, para predicción, mes objetivo.
- Comprobar presencia y coherencia de estados, campos monetarios y categorías solicitadas.
- Aceptar campos adicionales de respuesta que no utilice; rechazar versiones principales no soportadas.
- Diferenciar errores de validación, indisponibilidad, tiempo agotado y respuesta incompatible.
- No sustituir fallos por ceros ni por respuestas simuladas automáticamente.
- No aplicar reintentos automáticos en la implementación inicial. Las operaciones no modifican datos, pero un reintento necesita una política explícita.

Cada solicitud es independiente y no crea registros, entrena modelos persistidos ni guarda predicciones. `request_id` sirve para correlación; no es una clave de idempotencia ni de caché.

## 9. Simulación para desarrollo independiente

El simulador implementa las mismas rutas, validaciones, campos y estados. Se selecciona por configuración del servicio Python, por ejemplo `PREDICTOR_MODE=mock`; Java no envía un algoritmo ni un indicador para activar simulaciones.

Para una predicción simulada válida con historial suficiente, cada serie devuelve como estimación su último importe observado. Es una regla determinista exclusiva del simulador, no una validación de calidad predictiva. Mantiene el mínimo de seis meses y los estados de insuficiencia.

Los resultados simulados usan `simulated: true`, `method: "mock"` y `model_version: "1"`. Los resultados reales usan `simulated: false`. Si se simula analítica, se conservan sus fórmulas y se marca `simulated: true`; no se mezclan resultados reales y simulados dentro de una respuesta.

La pantalla que muestre una respuesta simulada debe identificarla como datos de prueba. `/health` continúa disponible en ambos modos.

## 10. Historial de prueba de dos años

Se dispone de un historial de 24 meses destinado a pruebas. Su existencia permite probar el contrato antes de cargarlo en PostgreSQL. Este documento no verifica su contenido ni presupone su calidad o representatividad.

La preparación del conjunto debe comprobar:

1. Veinticuatro meses consecutivos y cerrados.
2. Identificadores y categorías coherentes con el modelo.
3. Moneda única y conversión exacta a centavos.
4. Distinción entre cero confirmado y dato ausente.
5. Cobertura declarada por categoría.
6. Procedencia y transformaciones documentadas, identificando datos sintéticos cuando corresponda.

La carga posterior en la base debe ser un seed de desarrollo repetible, separado de las migraciones de estructura. Debe preservar relaciones, evitar duplicados y utilizar una organización de prueba identificable. No deben desactivarse restricciones para introducirlo «a la fuerza» ni cargarse como gastos reales de usuarios.

Si el conjunto contiene solamente totales agregados, su conversión a gastos de demostración debe documentarse como una representación sintética; no equivale a disponer de comprobantes o gastos individuales originales.

El mes objetivo será el posterior al último mes del conjunto. No se desplazan fechas históricas sin registrar la transformación. Si hay meses posteriores al calendario actual, deberán excluirse de las pruebas de integración que apliquen la regla de no admitir gastos futuros.

## 11. Pruebas y aceptación

| Caso | Resultado esperado |
| --- | --- |
| Historial válido de 24 meses | HTTP 200; resultados disponibles según cobertura. |
| Analítica del ejemplo | Total 17.000.000 → 17.500.000 centavos; variación 500.000 y 2,94 %. |
| Simulador con el ejemplo | General 17.500.000; categorías 12.500.000 y 5.000.000; `simulated: true`. |
| Categoría reciente con menos de seis meses | Predicción individual insuficiente; estado general `PARTIAL` si otras series son válidas. |
| Historial vacío | HTTP 200; insuficiencia, sin estimaciones inventadas. |
| Mes completo sin gastos | Cero aceptado como dato válido. |
| Importe anterior cero | Porcentaje `null`, diferencia y dirección disponibles. |
| Mes duplicado, desordenado o faltante | HTTP 422 con código de detalle correspondiente. |
| Categoría desconocida o repetida | HTTP 422. |
| Categoría disponible omitida en un mes | HTTP 422, `MISSING_CATEGORY_TOTAL`. |
| Importe negativo, decimal, booleano o string | HTTP 422. |
| Mes objetivo incompatible | HTTP 422, `INVALID_TARGET_MONTH`. |
| Predicciones independientes | Java acepta que su suma no coincida con la general. |
| Python detenido o demora excesiva | Java informa indisponibilidad o tiempo agotado, sin ceros sustitutivos. |
| Respuesta con correlación o estructura inválida | Java detecta fallo de integración. |

Los equipos deben compartir archivos JSON de entrada y respuesta esperada. Las pruebas de contrato verifican estructura y reglas; las pruebas del algoritmo verifican cálculos; la evaluación predictiva mide calidad. Son comprobaciones distintas.

La evaluación del modelo con el historial de dos años respeta el orden temporal: cada mes se predice usando únicamente meses anteriores. Se compara con una referencia sencilla, como el promedio de los últimos tres meses. Un conjunto sintético permite validar comportamiento, pero no demuestra precisión sobre gastos reales.

## 12. Evolución y entregables

La versión 1 incluye comparación mensual y predicción a un mes. No incluye OCR, presupuestos, ajuste por inflación, horizontes múltiples, intervalos de confianza ni diagnóstico causal.

Cambiar tipos, eliminar campos, redefinir su significado o modificar incompatiblemente las reglas requiere una nueva versión principal y rutas `/api/v2/...`. Agregar campos opcionales a una respuesta puede ser compatible; los consumidores deben ignorarlos. El modelo puede evolucionar mediante `model_version` conservando el contrato.

Ubicación recomendada de este documento: `docs/CONTRATO_JAVA_PYTHON_V1.md`. Entregables complementarios para implementar:

- `docs/contracts/openapi.yaml`, como definición formal coherente con este documento.
- `docs/contracts/examples/`, con solicitudes y respuestas válidas e inválidas.
- Validaciones y simulador en Python.
- Cliente HTTP y proveedor de historial de prueba en Java.
- Consulta JDBC y seed de desarrollo a cargo de Java y datos.
- Pruebas de contrato ejecutables por ambos equipos.

El contrato se considera integrado cuando ambos servicios procesan los mismos ejemplos, reproducen los estados acordados y manejan errores de comunicación sin alterar su significado.
