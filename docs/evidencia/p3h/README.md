# Evidencia P3-H — 23/10/2026

Entrega del evaluador multihorizonte offline de desarrollo ($h=1 \dots 6$) desde cortes comunes de información, conforme al protocolo normativo P2-H.

## Reproducción

Desde la raíz con PowerShell y Python (o mediante Docker):

```powershell
# Ejecución de la suite completa de pruebas unitarias (46 pruebas)
$env:PYTHONPATH="service-python;scripts"
python -m unittest discover -s scripts -p "test_*.py" -v

# Ejecución del evaluador multihorizonte sobre los 7 orígenes de desarrollo
New-Item -ItemType Directory -Force backend-java/target/p3h | Out-Null
$commit = & git rev-parse HEAD
python scripts/evaluate_forecast_csv.py --csv demo/gastos_prueba.csv --from-origin 2023-12 --to-origin 2024-06 --max-horizon 6 --commit $commit | Out-File -FilePath backend-java/target/p3h/informe-forecast-desarrollo.json -Encoding utf8
```

O mediante el wrapper de demostración si Docker está activo:

```powershell
.\scripts\predictor-demo.ps1 forecast -FromOrigin 2023-12 -ToOrigin 2024-06 -MaxHorizon 6 > backend-java/target/p3h/informe-forecast-desarrollo.json
```

## Configuración y diseño experimental

- **Período de desarrollo:** 7 orígenes históricos comunes: `2023-12`, `2024-01`, `2024-02`, `2024-03`, `2024-04`, `2024-05`, `2024-06`.
- **Ventana de proyección:** para cada origen $t_0$, se proyectan los horizontes $h=1 \dots 6$ hacia los objetivos $t_0+1 \dots t_0+6$, contenidos estrictamente en el año 2024 (sin tocar el conjunto de reserva 2025).
- **Aislamiento temporal estricto:** cada proyección a $h$ meses depende exclusivamente del historial disponible hasta el corte $t_0$, sin utilizar reales futuros ni incorporarlos entre pasos.
- **Universo evaluado:** 50 organizaciones y 400 categorías completas; 7 orígenes $\times$ 6 horizontes $\times$ 450 series = 18.900 puntos de evaluación común.

## Resultados consolidados por horizonte en organizaciones

| Horizonte ($h$) | Mes relativo | MAE `last_month` (ARS) | WAPE `last_month` (%) | MAE `ses` ($\alpha=0.3$) (ARS) | WAPE `ses` (%) | MAE `historical_mean` (ARS) |
| --- | --- | --- | --- | --- | --- | --- |
| $h=1$ | $t_0 + 1$ | **104.353,98** | **5,82 %** | 232.366,55 | 12,95 % | 512.980,48 |
| $h=2$ | $t_0 + 2$ | **159.459,60** | **8,52 %** | 309.780,81 | 16,55 % | 590.394,74 |
| $h=3$ | $t_0 + 3$ | **236.761,38** | **12,12 %** | 391.949,94 | 20,06 % | 672.563,87 |
| $h=4$ | $t_0 + 4$ | **324.357,58** | **15,89 %** | 480.079,94 | 23,51 % | 760.693,87 |
| $h=5$ | $t_0 + 5$ | **413.491,53** | **19,40 %** | 569.225,86 | 26,71 % | 849.839,78 |
| $h=6$ | $t_0 + 6$ | **513.548,48** | **23,02 %** | 669.282,80 | 30,00 % | 949.896,73 |

## Resultados consolidados por horizonte en categorías

| Horizonte ($h$) | Mes relativo | MAE `last_month` (ARS) | WAPE `last_month` (%) | MAE `ses` ($\alpha=0.3$) (ARS) | WAPE `ses` (%) | MAE `historical_mean` (ARS) |
| --- | --- | --- | --- | --- | --- | --- |
| $h=1$ | $t_0 + 1$ | **24.267,09** | **10,82 %** | 32.246,07 | 14,38 % | 64.338,72 |
| $h=2$ | $t_0 + 2$ | **29.617,46** | **12,66 %** | 40.446,47 | 17,29 % | 73.910,59 |
| $h=3$ | $t_0 + 3$ | **35.783,16** | **14,65 %** | 49.918,10 | 20,44 % | 84.110,52 |
| $h=4$ | $t_0 + 4$ | **44.430,04** | **17,41 %** | 60.570,19 | 23,73 % | 95.103,01 |
| $h=5$ | $t_0 + 5$ | **54.005,35** | **20,27 %** | 71.501,03 | 26,84 % | 106.239,55 |
| $h=6$ | $t_0 + 6$ | **65.909,27** | **23,63 %** | 83.824,47 | 30,06 % | 118.745,25 |

## Conclusiones y observaciones técnicas

1. **Degradación monotónica por horizonte:** en todas las series y modelos se observa un crecimiento sostenido del error monetario ($MAE$) y relativo ($WAPE$) a medida que $h$ se aleja del corte histórico ($h=1 \to 6$). Esto respalda la regla de UI que debe advertir que la incertidumbre crece con la distancia temporal proyectada.
2. **Superioridad consistente de la referencia basal:** `last_month` supera de forma sistemática a `ses` y a `historical_mean` en **todos los horizontes** ($h=1 \dots 6$), tanto a nivel organizacional como de categorías.
3. **Comportamiento plano:** los modelos autorregresivos sin componente explícito de tendencia (SES, último mes y media histórica) proyectan valores constantes a lo largo del horizonte futuro. Esto confirma que el aumento del error está gobernado por el rezago frente a la tendencia nominal de la serie sintética.
4. El archivo [resumen.json](resumen.json) almacena las métricas por nivel, fracciones exactas de centavos y metadatos de configuración.
