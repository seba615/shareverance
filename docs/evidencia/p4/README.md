# Evidencia P4 — desarrollo reproducible y reserva exploratoria

Corrección del 22/10/2026, relacionado con #71. La ejecución original ya consultó valores de 2025 y publicó resultados. No consta evidencia previa de aceptación manual de P2, universo/configuración congelados o un registro que acredite ejecución única. Por ello **los resultados de reserva se conservan como históricos exploratorios, no como evaluación final acreditada**. Una nueva ejecución no puede presentarse como primera evaluación de una reserva intacta.

## Cómo reproducir el desarrollo

Desde la raíz, Docker Desktop Linux y PowerShell; no iniciar base/frontend:

```powershell
New-Item -ItemType Directory -Force backend-java/target/p4 | Out-Null
.\scripts\predictor-demo.ps1 test
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12 -SweepAlpha > backend-java/target/p4/desarrollo-alpha.json
```

Grid fijo: 0.1, 0.3, 0.5, 0.7, 0.9, 0.99 y 1.0. SES se calcula con su implementación existente; referencias y objetivos comunes para cada alpha. El [informe de desarrollo](desarrollo-alpha.json) identifica hash del CSV/evaluador, commit base y configuración, métricas exactas por nivel y frecuencia/máximo deterioro frente a la **mejor de ambas referencias por serie**. Es desarrollo sintético, sin aceptación, promoción o consulta de errores reservados. No hay congelación retroactiva.

| Alpha | MAE organización ARS | MAE categoría ARS | Organizaciones degradadas / 50 | Categorías degradadas / 400 |
| --- | --- | --- | --- | --- |
| 0.10 | 577.559,99 | 72.380,80 | 50 | 396 |
| 0.30 | 262.123,06 | 35.975,26 | 50 | 313 |
| 0.50 | 170.621,28 | 28.218,47 | 50 | 203 |
| 0.70 | 135.319,09 | 26.388,43 | 46 | 142 |
| 0.90 | 121.858,13 | 26.577,45 | 38 | 100 |
| 0.99 | 119.435,64 | 27.045,40 | 33 | 87 |
| 1.00 | 119.270,11 | 27.109,02 | 0 | 2 |

Último mes tiene MAE de desarrollo 119.270,11 ARS por organización y 27.109,02 ARS por categoría. Alpha 0.70 sí mejora el MAE de categorías, pero no el de organizaciones. Alpha 1.0 reproduce exactamente último mes; las dos categorías degradadas se comparan contra la mejor referencia por serie, que puede ser media histórica. No se concluye que ningún alpha pueda superar último mes en cualquier serie ni se selecciona un modelo a partir de 2025. El alpha histórico 0.3 corresponde al candidato E6.1, no a una selección óptima demostrada por el barrido.

## Reserva ya observada: evidencia histórica

Se conserva [resumen.json](resumen.json) con sus métricas originales y una rectificación explícita de alcance. No se repitió la evaluación real de 2025 durante esta corrección. El commit registrado era base del código local, no prueba de congelación previa. Las frecuencias históricas siguientes fueron calculadas contra último mes; no se presentan como la regla P2 contra la mejor de ambas referencias por serie.

| Nivel | Método | MAE ARS | WAPE | Degradación histórica frente a último mes |
| --- | --- | --- | --- | --- |
| Organización | Último mes | 199.239,68 | 5,83 % | Referencia |
| Organización | SES 0.3 | 443.177,85 | 12,97 % | 50 / 50 |
| Organización | Media histórica | 1.555.786,14 | 45,52 % | 50 / 50 |
| Categoría | Último mes | 45.195,02 | 10,58 % | Referencia |
| Categoría | SES 0.3 | 60.456,34 | 14,15 % | 316 / 400 |
| Categoría | Media histórica | 194.480,60 | 45,52 % | 400 / 400 |

Estas cifras no justifican promover SES; tampoco convierten la referencia ganadora de reserva en una selección operativa validada. `accepted:null`, `model_promoted:false` y `final_evaluation_accredited:false`. P5 exige una decisión con evidencia y política definida en desarrollo; P2-H/P3-H siguen pendientes para 4–6 meses.

## Controles del CLI y pruebas

Sin `-AllowReserve`, 2025 sigue bloqueado. Con ese flag sólo se admite exactamente 2025-01 a 2025-12, alpha 0.3 y todas las series/objetivos del archivo completos. Rangos parciales o mixtos, huecos y series insuficientes fallan sin publicar un informe de éxito. El alcance emitido es `synthetic_reserve_exploratory` y nunca acredita evaluación final o ejecución única. No es un mecanismo que impida al operador repetir una exploración.

No se incluye un comando de reapertura de reserva como prueba habitual: su comportamiento se prueba con fixtures sintéticos. La corrección aprobó 29 regresiones en Docker (8 cargador, 8 auditoría, 13 evaluador/barrido). Se ejecutó el grid sólo en desarrollo 2024. El entorno integrado mantiene los comandos de [P1-D](../../python_predictor.md#entorno-demo-predictor); no se iniciaron servicios ni se alteraron bases. Para una futura evaluación final se necesita protocolo aceptado, registro inmutable previo de universo/configuración y un conjunto temporal acordado que no se haya utilizado para selección.
