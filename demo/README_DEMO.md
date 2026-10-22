# Datos de demostración — Shareverance

Esta carpeta contiene datos de prueba y un ejemplo de solicitud para el servicio Python. Permite trabajar en la integración sin depender de las tablas de negocio.

## Archivos

| Archivo | Contenido |
| --- | --- |
| `gastos_prueba.csv` | Gastos mensuales agregados por organización y categoría. |
| `prediction_request_demo.json` | Solicitud de predicción de la organización 1, preparada según el contrato Java–Python v1. |

## Dataset de prueba

El CSV utiliza UTF-8 y punto y coma (`;`) como separador.

| Columna | Significado |
| --- | --- |
| `organization_id` | Identificador de organización; inventado para las pruebas. |
| `month` | Mes del total, en formato `YYYY-MM`. |
| `category_id` | Identificador de categoría. |
| `category` | Nombre descriptivo de categoría; no se envía a Python. |
| `amount_minor` | Importe entero en centavos. Por ejemplo, `10000` representa ARS 100,00. |
| `currency` | Moneda del importe: `ARS`. |

Para la organización 1 se verificaron **36 meses**, desde enero de 2023 hasta diciembre de 2025, con **8 categorías y 288 registros**. Cada registro representa un total mensual por categoría, no un gasto individual ni un comprobante.

### Procedencia y validez del dataset V1

Fuente directa: **generación sintética**, sin extracción ni calibración con archivos oficiales. El paquete original se identifica como `shareverance_dataset_v1/gastos_mensuales_sinteticos.csv`; el archivo versionado de este repositorio es `demo/gastos_prueba.csv`. El paquete original y una transformación reproducible entre ambos no están disponibles aquí: el hash del archivo local identifica sus bytes, no demuestra equivalencia con el original.

Las 50 entidades originales H001–H050 representan hogares ficticios. El CSV local usa IDs numéricos; la correspondencia exacta debe documentarse antes de atribuir identidades originales. Los supuestos declarados de generación son distribución base ilustrativa por categoría, crecimiento nominal del 4,5 % mensual, variación aleatoria por categoría y una semilla. No están disponibles el código generador, la fórmula exacta ni el valor documentado de esa semilla; no puede reproducirse cada importe desde esos supuestos.

Cobertura declarada para el experimento sintético: 50 hogares ficticios, 36 meses (enero de 2023 a diciembre de 2025) y ocho categorías; cada fila es un total mensual de una categoría de ese universo simulado. La auditoría estructural comprueba las filas del archivo, no que representen gasto real completo. No es una muestra representativa de hogares argentinos ni acredita precisión sobre usuarios reales.

ENGHo 2017–2018 y otras fuentes oficiales fueron identificadas como posibilidades futuras, pero no se descargaron ni aplicaron para calibrar V1. Se retira la atribución anterior a canastas oficiales como fuente directa por ser incorrecta.

No hay evidencia disponible de entrenamiento, comparación o ajuste de modelos con 2025. Esto permite proponerlo como reserva, pero no demuestra por sí solo que nunca se haya utilizado. Registrar responsables, alcance de la revisión y congelación antes de una evaluación final; si aparecen usos previos para selección, revisar la reserva. Partición propuesta: 2023 para entrenamiento inicial, 2024 para validación/selección temporal y 2025 para evaluación final. Es compatible con agrupar 2023–2024 como desarrollo en P2, pero cualquier cambio de ventana concreta debe acordarse antes de evaluar. V1 sirve para pruebas técnicas y experimentos sintéticos; no para aceptación de precisión sobre gastos reales argentinos.

## Solicitud JSON

El archivo `prediction_request_demo.json` es el cuerpo que Java debe enviar a:

```http
POST /api/v1/predictions/monthly
Content-Type: application/json
```

Dentro de Docker, la dirección es:

```text
http://python-service:8000/api/v1/predictions/monthly
```

| Campo | Contenido del ejemplo |
| --- | --- |
| `contract_version` | `"1"`. |
| `request_id` | UUID de ejemplo para correlacionar la solicitud y la respuesta. Java genera uno por solicitud. |
| `organization_id` | `1`. |
| `currency` | `"ARS"`. |
| `target_month` | `"2026-01"`: mes siguiente al último mes del dataset. |
| `categories` | Identificadores y primer mes con cobertura completa (`available_from_month`). No incluye nombres. |
| `history` | Los 36 meses ordenados, con sus totales por categoría en `category_totals`. |

Cada elemento de `category_totals` contiene únicamente `category_id` y `amount_minor`. Los importes del CSV ya están convertidos a centavos. Python obtiene los totales generales sumando las categorías de cada mes.

Para este ejemplo se considera completo el historial de prueba y se declara `available_from_month: "2023-01"` para las ocho categorías. Un cero representa ausencia confirmada de gastos; no debe utilizarse para sustituir datos desconocidos.

## Uso en desarrollo

Para auditar estructura y manifiesto del CSV sin cargar la base, seguir [P1: auditoría reproducible](../docs/python_predictor.md#auditoria-p1). El comando `scripts/predictor-demo.ps1 audit` no requiere iniciar servicios; necesita Docker Desktop activo. Existe alternativa con Python local. Un resultado estructural correcto no confirma cobertura real ni acepta el conjunto para evaluación final.

La [guía integrada P1-D](../docs/python_predictor.md#entorno-demo-predictor) permite cargar la organización externa 1 hasta 2024-12 mediante Java en una base aislada y probar con usuario desde localhost:5176. Comandos PowerShell, credenciales, conciliación, repetición y detención están allí; no cargar el SQL local ajeno ni insertar directamente estas filas en la base habitual. Las 50 organizaciones completas y otros cortes todavía no están soportados por el cargador.

Para comparar SES, último mes y media histórica con el CSV, seguir la [guía ejecutable de evaluación](../docs/python_predictor.md#evaluación-con-el-csv-de-demostración). El [plan P1–P5](../docs/road_map.md#plan-complementario-del-predictor) organiza auditoría, protocolo, evaluador CSV, comparación e integración. La prueba actual trabaja offline con 2023–2024 y no carga PostgreSQL; 2025 se propone como reserva. Los datos sintéticos no acreditan precisión sobre hogares reales ni aceptación automática del modelo.

1. Usar el JSON como entrada compartida para probar la serialización en Java y la validación en Python.
2. Enviarlo al endpoint cuando esté implementado, inicialmente con el simulador.
3. Comprobar que la respuesta corresponde a la organización 1 y al mes `2026-01`, y devuelve el mismo `request_id`.
4. Identificar las respuestas simuladas mediante `simulated: true`.

Para probar `POST /api/v1/analytics/monthly`, utilizar el mismo cuerpo **eliminando `target_month`**. La comparación será entre noviembre y diciembre de 2025.

Las predicciones generales y por categoría son independientes: su suma no tiene que coincidir con la predicción general.

P1-D incorpora carga repetible mediante Java para la organización 1 y meses 2023–2024 en PostgreSQL aislado, según la guía enlazada. Las 50 organizaciones completas y otros cortes requieren una ampliación posterior. Cada total agregado se identifica como gasto sintético mensual; no representa la distribución diaria real ni un comprobante.

La especificación completa de campos, validaciones, respuestas y errores se encuentra en [Contrato python](../docs/contratos_api.md).

