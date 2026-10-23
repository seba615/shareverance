# P5: decisión conservadora de no promoción de SES

Corrección del 23/10/2026 sobre `b94dc4f`. Relacionado con [#77](https://github.com/seba615/shareverance/issues/77) y [#59](https://github.com/seba615/shareverance/issues/59). La aceptación y el cierre siguen siendo manuales.

## Decisión y límites

Se conserva `PREDICTOR_MODE=mock` como predeterminado y SES como candidato experimental explícito. No hay evidencia suficiente para promover SES 0.3. Esto no acepta un modelo operativo, no cierra la evaluación predictiva ni acredita precisión sobre hogares reales: V1 es sintético. Aunque mock reutiliza el último importe, sigue identificado como simulación y no equivale a una referencia promovida.

P2 conserva sus umbrales de 5 % de mejora y 20 % de series degradadas como propuesta para aceptación manual. P2-H no fija todavía umbrales por horizonte, regla conjunta ni mínimo de muestras. La comparación siguiente es descriptiva; no declara una aceptación o un rechazo formal bajo un protocolo aprobado.

## Evidencia utilizada

La [evidencia P4](../p4/README.md) distingue desarrollo 2024 de resultados históricos exploratorios de 2025. No consta aceptación previa del protocolo ni congelación acreditada; 2025 ya fue consultado y no vuelve a presentarse como reserva intacta. No se reevalúa ese período en esta corrección.

| Nivel, desarrollo 2024 | MAE último mes (ARS) | MAE SES 0.3 (ARS) | MAE media histórica (ARS) | Series SES degradadas contra la mejor de ambas referencias |
| --- | --- | --- | --- | --- |
| Organización | 119.270,11 | 262.123,06 | 649.680,74 | 50/50 (100 %) |
| Categoría | 27.109,02 | 35.975,26 | 81.344,79 | 313/400 (78,25 %) |

SES 0.3 empeora el MAE de desarrollo en aproximadamente 119,8 % y 32,7 %, respectivamente. Los conteos de desarrollo aplican la comparación por serie con el menor MAE de último mes y media histórica, como propone P2. El barrido incluye alpha 0.7, que mejora el MAE agregado de categorías pero empeora organizaciones; alpha 1 reproduce último mes. No se demuestra una mejora conjunta que justifique promoción.

En 2025, los MAE históricos de último mes/SES fueron 199.239,68/443.177,85 ARS por organización y 45.195,02/60.456,34 ARS por categoría. Los conteos históricos 50/50 y 316/400 comparan sólo contra último mes: no son el conteo exacto de la regla P2 contra la mejor de ambas referencias por serie ni una evaluación final acreditada.

La [evidencia P3-H](../p3h/README.md) registra siete orígenes de desarrollo y 18.900 puntos: último mes tiene menor MAE agregado que SES 0.3 y media histórica en ambos niveles para h=1…6. Esto no demuestra superioridad en cada serie, no crea un quinto criterio de aceptación y no sustituye P4-H. Los orígenes solapados tampoco son muestras independientes. Umbrales y aceptación multihorizonte continúan pendientes.

## Cómo reproducir y probar

Desde la raíz, con Docker Desktop Linux activo y PowerShell. Los comandos offline comparan SES y referencias sin iniciar servicios ni consultar errores de 2025:

```powershell
New-Item -ItemType Directory -Force backend-java/target/p5 | Out-Null
.\scripts\predictor-demo.ps1 test
.\scripts\predictor-demo.ps1 evaluate -FromMonth 2024-01 -ToMonth 2024-12 -SweepAlpha > backend-java/target/p5/desarrollo-alpha.json
.\scripts\predictor-demo.ps1 forecast -FromOrigin 2023-12 -ToOrigin 2024-06 -MaxHorizon 6 > backend-java/target/p5/desarrollo-forecast.json
```

Comprobar que cada comando termina correctamente antes de utilizar el JSON. No es necesario repetir las evaluaciones para revisar el texto de la interfaz.

Para el circuito integrado mock, usar la [guía P1-D](../../python_predictor.md#entorno-demo-predictor), con proyecto, datos y credenciales de demostración separados:

```powershell
.\scripts\predictor-demo.ps1 start
.\scripts\predictor-demo.ps1 load
.\scripts\predictor-demo.ps1 check
.\scripts\predictor-demo.ps1 status
# Abrir http://localhost:5176, elegir la organización CSV y Predicción.
.\scripts\predictor-demo.ps1 stop
```

`stop` conserva datos y volúmenes. La UI vigente sigue siendo mensual; el comando forecast es offline. Para el candidato SES, el evaluador anterior permite comparar sus resultados; la activación del servicio usa `PREDICTOR_MODE=ses` según [configuración Python](../../python_predictor.md). El wrapper P1-D fija mock y limpia variables heredadas: asignar esa variable antes de `start` no activa SES. Esta entrega no acredita una prueba integrada de SES ni modifica el wrapper para activarlo.

Para verificar el mensaje React con Node y dependencias del lock instaladas:

```powershell
Push-Location frontend
try {
    npm test
    npm run build
} finally { Pop-Location }
```

## Evidencia de comprobaciones

Las cifras de suites de la descripción inicial del PR corresponden a lo declarado por su autor; no acreditan por sí solas PostgreSQL real, aislamiento, HTTP 503 ni el circuito completo. La suite Python declaró dos omisiones de PostgreSQL. Las evidencias históricas de integración permanecen en el roadmap y no se presentan como repetidas aquí.

Esta corrección modifica documentación y el aviso React, sin cambiar contratos, configuración predeterminada, modelos, migraciones ni dependencias. Los resultados efectivamente ejecutados en la corrección se registran en el roadmap; la aceptación integral y predictiva sigue pendiente.

Corrección del 23/10/2026: npm ci con lock, 40 pruebas frontend aprobadas sin omisiones y npm run build correcto; 63 enlaces relativos a archivos y git diff --check comprobados. No se ejecutaron las suites Java/Python ni integración con PostgreSQL, no se iniciaron servicios y no se reevaluó 2025.
