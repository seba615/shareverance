# Tendencia real experimental: demostración #85

Se incorpora un modelo que aprende del historial, en lugar de reutilizar el último importe. Predeterminado log_trend, método log_linear_trend, versión 1, simulated false. Implementación y límites en [Python](../../python_predictor.md). No es aceptación productiva ni precisión sobre gastos reales argentinos.

## Evaluación ejecutada

CSV V1 sintético, 50 hogares, 400 series de categorías. Siete orígenes 2023-12 a 2024-06; horizontes 1 a 6, objetivos exclusivamente 2024. Mismos cortes para SES 0.3, último mes, media histórica y tendencia. Sin ajuste con observaciones futuras ni evaluación de errores de 2025. La elección usa desarrollo, no una evaluación final independiente. [Resumen con hashes y métricas completas por horizonte](resumen.json); informe detallado generado localmente, no publicado por tamaño. code_commit es la base 380fd72 y los hashes identifican código local aún sin commit.

MAE en ARS, promedio con igual peso por serie:

| Nivel / método | h1 | h6 |
| --- | ---: | ---: |
| Organización / último mes | 104.353,98 | 513.548,48 |
| Organización / SES | 232.366,55 | 669.282,80 |
| Organización / media | 512.980,48 | 949.896,73 |
| Organización / tendencia | 67.000,66 | 104.148,05 |
| Categoría / último mes | 24.267,09 | 65.909,27 |
| Categoría / SES | 32.246,07 | 83.824,47 |
| Categoría / media | 64.338,72 | 118.745,25 |
| Categoría / tendencia | 18.753,08 | 26.403,67 |

La tendencia mejora MAE agregado frente a las tres referencias en ambos niveles y todos los horizontes; esto no garantiza mejora en cada serie. WAPE organizacional h1/h6: 3,73 % / 4,67 %; categorías: 8,36 % / 9,47 %. Sin umbral acordado ni intervalos calibrados, no se marca accepted ni se promueve por una prueba automática. El dataset tiene tendencia nominal artificial; la ventaja es evidencia técnica bajo esos supuestos, sin representatividad nacional. 2025 ya fue explorado previamente, no es reserva intacta.

## Repetir y probar la aplicación

Desde raíz en PowerShell, Docker Desktop Linux activo:

```powershell
./scripts/predictor-demo.ps1 evaluate-trend > backend-java/target/evaluacion-tendencia.json
./scripts/predictor-demo.ps1 test
./scripts/predictor-demo.ps1 start
./scripts/predictor-demo.ps1 load
./scripts/predictor-demo.ps1 check-forecast
./scripts/predictor-demo.ps1 stop
```

El evaluador funciona sin iniciar servicios. La demo usa localhost:5176 y base aislada; stop conserva volúmenes. [Acceso, organización, recorrido y capturas](../demo-sintetica/README.md). No copiar CSV ni proyecciones a bases ajenas. Las previsiones no se guardan como gastos.

Ejecutado: 135 pruebas Python aprobadas, 2 omitidas (SQL de esa suite), 48 pruebas de scripts, 42 frontend y build correcto. Java: 87 ejecutadas, 50 omitidas, cero fallos. HTTP integrado con PostgreSQL real: horizontes 4/5/6, permisos, insuficiencia, equivalencia mensual y ausencia de escrituras comprobados. La captura real muestra seis importes diferentes. Sigue pendiente gráfico combinado histórico/previsión y aceptación humana. No se cierra ninguna tarea.
