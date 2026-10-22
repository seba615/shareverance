# Evidencia P4 — 22/10/2026

Entrega de la comparación en desarrollo, congelamiento de configuración y evaluación controlada del conjunto de reserva 2025 conforme al protocolo normativo P2.

## Reproducción

Desde la raíz con PowerShell y Python (o Docker Desktop Linux):

```powershell
# Ejecución de la suite completa de pruebas unitarias
$env:PYTHONPATH="service-python;scripts"
python -m unittest discover -s scripts -p "test_*.py" -v

# Ejecución de la evaluación de reserva 2025
New-Item -ItemType Directory -Force backend-java/target/p4 | Out-Null
$commit = & git rev-parse HEAD
python scripts/evaluate_predictor_csv.py --csv demo/gastos_prueba.csv --from-month 2025-01 --to-month 2025-12 --commit $commit --allow-reserve | Out-File -FilePath backend-java/target/p4/informe-reserva-2025.json -Encoding utf8
```

O mediante el wrapper de demostración si Docker está disponible:

```powershell
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2025-01 -ToMonth 2025-12 -AllowReserve > backend-java/target/p4/informe-reserva-2025.json
```

## Análisis en desarrollo (2023–2024) y sintonización de hiperparámetros

En desarrollo se evaluó el impacto de variar $\alpha$ en SES ($\alpha \in [0.10, 0.99]$) frente a las referencias basales:
- En series con tendencia nominal (inflación simulada en V1), SES presenta un rezago sistemático estructural frente a la referencia `last_month`.
- A medida que $\alpha \to 1.0$, el pronóstico de SES converge asintóticamente hacia el valor de `last_month`, pero sin superarlo:
  - En organizaciones: $\alpha=0.30$ (MAE ARS 262.123,06) vs. $\alpha=0.99$ (MAE ARS 119.435,64) vs. `last_month` (MAE ARS 119.270,11).
  - En categorías: $\alpha=0.30$ (MAE ARS 35.975,26) vs. $\alpha=0.70$ (MAE ARS 26.388,43) vs. `last_month` (MAE ARS 27.109,02).
- Se congeló la configuración candidata en $\alpha=0.30$ (definida en E6.1) y las referencias basales `last_month` y `historical_mean`.

## Resultados de la evaluación única en reserva (2025-01 a 2025-12)

La evaluación en reserva evaluó 50 organizaciones (600 puntos) y 400 categorías (4.800 puntos) con ventana progresiva estricta:

| Nivel | Método | MAE medio (ARS) | WAPE acumulado (%) | Degradación frente a `last_month` |
| --- | --- | --- | --- | --- |
| **Organizaciones** | **`last_month`** | **199.239,68** | **5,83 %** | *Referencia basal óptima* |
| Organizaciones | `ses` ($\alpha=0.3$) | 443.177,85 | 12,97 % | 50 / 50 series degradadas (100 %) |
| Organizaciones | `historical_mean` | 1.555.786,14 | 45,52 % | 50 / 50 series degradadas (100 %) |
| **Categorías** | **`last_month`** | **45.195,02** | **10,58 %** | *Referencia basal óptima* |
| Categorías | `ses` ($\alpha=0.3$) | 60.456,34 | 14,15 % | 316 / 400 series degradadas (79,0 %) |
| Categorías | `historical_mean` | 194.480,60 | 45,52 % | 400 / 400 series degradadas (100 %) |

## Conclusión y decisión cuantitativa frente al protocolo P2

1. **Incumplimiento de mejora global:** el candidato SES empeora el MAE en un +122,4 % a nivel organizacional y un +33,8 % a nivel categorías frente a `last_month`, incumpliendo el requisito de mejora relativa $\ge 5\,\%$.
2. **Incumplimiento de estabilidad por serie:** la frecuencia de degradación alcanza el 100 % en organizaciones y el 79 % en categorías, superando ampliamente el límite máximo admisible del 20 %.
3. **Decisión:** SES no resulta elegible para promoción. Conforme al protocolo P2, se mantiene `last_month` como la referencia basal superior y recomendada para el comportamiento operativo en este régimen de datos.
4. El archivo [resumen.json](resumen.json) conserva los metadatos, hashes de validación y cómputos exactos en fracciones de centavos.
