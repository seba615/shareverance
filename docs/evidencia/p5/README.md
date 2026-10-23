# Evidencia y Decisión Operativa P5 — 23/10/2026

Seguimiento [#77](https://github.com/seba615/shareverance/issues/77), relacionado con [#59](https://github.com/seba615/shareverance/issues/59), [#71](https://github.com/seba615/shareverance/issues/71), [#73](https://github.com/seba615/shareverance/issues/73) y [#75](https://github.com/seba615/shareverance/issues/75).

---

## 1. Contexto y regla de decisión

La etapa **P5 del Roadmap del Predictor** establece la regla operativa vinculante:
> *"Decidir método operativo y documentar evidencia frente al umbral. Integrar configuración y mensajes si corresponde; validar Java→Python→vistas SQL, aislamiento, insuficiencia y frontend. **Mantener referencia si no hay mejora demostrada**."*

Habiendo concluido las etapas de evaluación offline reproducible:
* **P4:** Barrido paramétrico ($\alpha$) y evaluación sobre desarrollo 2024 y reserva exploratoria 2025 para un mes ($h=1$).
* **P3-H:** Evaluación multihorizonte offline para horizontes $h=1 \dots 6$ desde un mes de corte común.

Este documento formaliza el contraste contra los criterios cuantitativos propuestos en **P2** y documenta la decisión operativa final respecto a la promoción del modelo.

---

## 2. Síntesis de la evidencia cuantitativa observada

### 2.1. Evaluación en horizonte $h=1$ (Evidencia P4)

Sobre el conjunto de 50 organizaciones y 400 categorías con historial completo en 2024 y 2025:

| Nivel | Período evaluado | Métrica | Último mes (`last_month`) | SES ($\alpha=0.3$) | Media histórica (`historical_mean`) | Resultado SES vs Referencia |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Organización** | Desarrollo 2024 | MAE (ARS) | **119.270,11** | 262.123,06 | 649.680,74 | Empeora un **119,8 %** |
| **Organización** | Reserva 2025 | MAE (ARS) | **199.239,68** | 443.177,85 | 1.555.786,14 | Empeora un **122,4 %** |
| **Organización** | Reserva 2025 | WAPE (%) | **5,83 %** | 12,97 % | 45,52 % | Empeora 7,14 pp |
| **Categoría** | Desarrollo 2024 | MAE (ARS) | **27.109,02** | 35.975,26 | 81.344,79 | Empeora un **32,7 %** |
| **Categoría** | Reserva 2025 | MAE (ARS) | **45.195,02** | 60.456,34 | 194.480,60 | Empeora un **33,8 %** |
| **Categoría** | Reserva 2025 | WAPE (%) | **10,58 %** | 14,15 % | 45,52 % | Empeora 3,57 pp |

**Tasa de degradación por serie (Reserva 2025 vs `last_month`):**
* Nivel Organizaciones: degradó en **50 de 50 series (100 %)**.
* Nivel Categorías: degradó en **316 de 400 series (79,0 %)**.

**Barrido paramétrico de $\alpha$ en desarrollo 2024:**
* $\alpha = 0.10 \dots 0.99$: Ningún valor de $\alpha$ logró superar de forma integral a `last_month`. Si bien $\alpha = 0.70$ mejoró levemente el MAE en categorías (26.388 ARS vs 27.109 ARS), degradó el nivel organizacional (135.319 ARS vs 119.270 ARS).
* Para $\alpha = 1.00$, el modelo SES reproduce matemáticamente a `last_month`.

### 2.2. Evaluación en horizontes $h=1 \dots 6$ (Evidencia P3-H)

Evaluado sobre 7 orígenes históricos de corte común (`2023-12` a `2024-06`) con 18.900 puntos de evaluación común:

* **Superioridad unánime de la referencia basal:** En **todos los horizontes** ($h=1, 2, 3, 4, 5, 6$), la referencia de último mes (`last_month`) superó sistemáticamente tanto a `ses` ($\alpha=0.3$) como a `historical_mean`, tanto en el nivel de organizaciones como en el nivel de categorías.
* **Progresión de errores en organizaciones:**
  * $h=1$: MAE `last_month` = 104.353,98 ARS vs `ses` = 232.366,55 ARS.
  * $h=3$: MAE `last_month` = 236.761,38 ARS vs `ses` = 391.949,94 ARS.
  * $h=6$: MAE `last_month` = 513.548,48 ARS vs `ses` = 669.282,80 ARS.
* **Progresión de errores en categorías:**
  * $h=1$: MAE `last_month` = 24.267,09 ARS vs `ses` = 32.246,07 ARS.
  * $h=6$: MAE `last_month` = 65.909,27 ARS vs `ses` = 83.824,47 ARS.

---

## 3. Evaluación frente a los umbrales cuantitativos (Protocolo P2)

| Criterio formal (P2) | Umbral requerido | Resultado observado de SES | Veredicto |
| :--- | :--- | :--- | :--- |
| **Criterio 1: Mejora de MAE en organizaciones** | Reducción de al menos **5,0 %** de MAE respecto a la mejor referencia | Aumento del error de **+119,8 %** en desarrollo y **+122,4 %** en reserva | **NO CUMPLE** |
| **Criterio 2: Mejora de MAE en categorías** | Reducción de al menos **5,0 %** de MAE respecto a la mejor referencia | Aumento del error de **+32,7 %** en desarrollo y **+33,8 %** en reserva | **NO CUMPLE** |
| **Criterio 3: Frecuencia de degradación organizacional** | Máximo **20,0 %** de series con error mayor a la referencia | Degradación en el **100,0 %** (50/50 series) | **NO CUMPLE** |
| **Criterio 4: Frecuencia de degradación en categorías** | Máximo **20,0 %** de series con error mayor a la referencia | Degradación en el **79,0 %** (316/400 series) | **NO CUMPLE** |
| **Criterio 5: Comportamiento multihorizonte ($h=1\dots 6$)** | Consistencia en horizontes extendidos sin fuga temporal | Superado sistemáticamente por `last_month` en todos los horizontes | **NO CUMPLE** |

---

## 4. Resolución y Decisión Operativa Formal

En estricta observancia del principio rector de calidad técnica y de la regla del roadmap:

1. **NO PROMOCIÓN DEL CANDIDATO SES:**
   Se resuelve formalmente **no promover el modelo Suavizado Exponencial Simple (SES) a modelo operativo en producción**, debido a que no alcanzó ninguno de los umbrales de mejora requeridos y evidenció un rendimiento significativamente inferior a la referencia básica de último mes (`last_month`).

2. **PRESERVACIÓN DEL MODO OPERATIVO PREDETERMINADO:**
   * El modo predeterminado de ejecución de Shareverance se mantiene en **`PREDICTOR_MODE=mock`** (cálculo simulado para pruebas de integración continua y desarrollo local).
   * El candidato SES se preserva en el código fuente (`PREDICTOR_MODE=ses`) exclusivamente como **candidato experimental de investigación**, sin atribuirle certificación de precisión ni idoneidad para la toma de decisiones financieras en producción.

3. **CLARIDAD Y TRANSPARENCIA EN LA INTERFAZ DE USUARIO:**
   * La vista de Predicción en el frontend React (`PredictionMessages.tsx`) mantiene los avisos explícitos exigidos por los criterios de aceptación A11 y A12:
     - Distinción visual inequívoca entre cálculo simulado (`simulated: true`, mock) y cálculo experimental (`simulated: false`, SES v1).
     - El badge experimental informa con precisión técnica que la calidad predictiva del cálculo no fue aceptada frente a la referencia.
     - Se preserva el manejo estricto de historial insuficiente (`INSUFFICIENT_HISTORY` y estimación `null`) cuando no se dispone de al menos 6 meses cerrados continuos.

4. **VERIFICACIÓN DE NO REGRESIÓN DE LA INTEGRACIÓN:**
   * La cadena de integración unidireccional `React → Java → Python → PostgreSQL (vistas SQL)` se mantiene íntegra y sin alteraciones.
   * Se verifica el aislamiento multi-tenant por contexto SQL de sesión transaccional (`shareverance.organization_id`), la ausencia de fugas temporales y la resiliencia frente a caídas o demoras del servicio interno mediante HTTP 503 controlado.
