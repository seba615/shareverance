# Shareverance - Resumen de Minutas de Seguimiento

## Minuta 1 — Sábado 26 de septiembre de 2026
- **Objetivo:** Alineación inicial y kick-off del proyecto Shareverance.
- **Temas tratados y acuerdos:**
  1. *Propósito del sistema:* Plataforma de gestión y división equitativa de gastos compartidos en hogares u organizaciones.
  2. *Stack tecnológico inicial:* React/Vite en frontend, Java/Spring Boot para lógica transaccional de negocio, PostgreSQL para persistencia y Python/FastAPI para analítica y futuros modelos de predicción/OCR.
  3. *Plan de acción inmediato:* Sebastián prepara la creación del repositorio base y estructura inicial; Marcelo y Aliocha comienzan a bosquejar el alcance funcional del MVP y entidades clave.

## Minuta 2 — Miércoles 30 de septiembre de 2026
- **Objetivo:** Modelo de datos inicial y delimitación del alcance.
- **Temas tratados y acuerdos:**
  1. *Entidades centrales:* Definición de Organizaciones, Miembros, Categorías, Gastos y Partes de gasto.
  2. *Reglas de negocio tempranas:* Se acuerda la separación en roles básicos (ADMIN y USER por organización) y se documentan las primeras pautas en `README.md` y guías de arquitectura.
  3. *Acuerdos técnicos:* Marcelo toma la estructura de servicios backend en Java; Sebastián avanza en la definición formal de modelos relacionales e inicialización del entorno Docker.

## Minuta 3 — Sábado 3 de octubre de 2026
- **Objetivo:** Acotación de alcance (*Scope cut*) del MVP 1 y definiciones operativas.
- **Temas tratados y acuerdos:**
  1. *Criterios de simplificación:* Para evitar dispersión, se decide explícitamente no incluir multimoneda (moneda única ARS en centavos enteros), prescindir de pagos/liquidaciones bancarias en esta etapa y limitar los repartos a métodos deterministas (individual o partes iguales).
  2. *Aislamiento de servicios:* React consumirá exclusivamente Java; Java orquestará Python por red interna. Python no tendrá acceso directo de escritura a la base de datos de negocio.
  3. *Próximos pasos:* Configurar entorno Docker Compose base con Java, Python, PostgreSQL y React.

## Minuta 4 — Miércoles 7 de octubre de 2026
- **Objetivo:** Contratos de integración inter-servicios y modelo relacional Flyway.
- **Temas tratados y acuerdos:**
  1. *Contratos API:* Se define el primer borrador de `docs/contratos_api.md`. Java enviará solicitudes a Python autenticadas mediante secreto interno Bearer token. Presentación de la API de autenticación (sesión, CSRF) y microservicios Java consumiendo PostgreSQL real (eliminación de mocks iniciales).
  2. *Evolución de base de datos:* Se acuerda utilizar migraciones versionadas con Flyway en PostgreSQL (V1, V2, V3).
  3. *Microservicios CRUD en Java:* Marcelo presenta la base de controladores y servicios Spring Boot para organizaciones y usuarios. Aliocha coordina las validaciones de entrada en Python.
  4. *WebApp base:* Sebastián y Aliocha revisan las primeras pantallas en React (login y creación/selección de organización).
  5. *Servicio Python:* Estructura inicial de FastAPI con endpoint de predicción simulada (*mock*) validado contra esquemas Pydantic estrictos.

## Minuta 5 — Miércoles 14 de octubre de 2026
- **Objetivo:** Reestructuración hacia el Roadmap definitivo del MVP 1 e Instrucciones para Agentes.
- **Temas tratados y acuerdos:**
  1. *Gestión de gastos e historial:* Requisitos de listado paginado, detalle, filtros y vista de calendario mensual en frontend.
  2. *Manejo de concurrencia y datos:* Se define control de concurrencia optimista, respuesta HTTP 409 ante colisiones y protección estricta del último ADMIN para impedir organizaciones huérfanas.
  3. *Acceso de Python a PostgreSQL (E3.1 / E3.2):* Python debe conectarse con credencial de solo lectura (`shareverance_python`) y consumir exclusivamente vistas agregadas autorizadas (`v_monthly_organization_expenses`), fijando la variable local de sesión SQL `shareverance.organization_id`. Se retira definitivamente cualquier llamada inversa de Python a Java.
  4. *Asignaciones:* Aliocha revisa la persistencia de vistas y cobertura; Marcelo coordina la orquestación en Java; Sebastián toma la integración del panel React y calendario.

## Minuta 6 — Miércoles 21 de octubre de 2026
- **Objetivo:** Cierre de Analítica, OCR y lanzamiento del Plan Complementario del Predictor (P1 a P5).
- **Temas tratados y acuerdos:**
  1. *Balance de E4 y E5:* Aprobada la integración del motor OCR Tesseract con volumen compartido protegido y el panel analítico con métricas mensuales comparativas.
  2. *Entorno Demo del Predictor:* Se acuerda crear un entorno aislado con Docker y un dataset sintético auditado de 24 meses (2023–2024) para evaluar formalmente el comportamiento predictivo sin alterar datos de usuarios reales.
  3. *Protocolo de evaluación:* Se acuerda que para promover cualquier modelo sobre la referencia de último mes, debe evaluarse en desarrollo y superar umbrales cuantitativos estrictos, manteniendo 2025 como partición de reserva.

## Minuta 7 — Sábado 24 de octubre de 2026
- **Objetivo:** Evaluación de resultados predictivos, decisión conservadora (P5) e integración multihorizonte (P5-H).
- **Temas tratados y acuerdos:**
  1. *Decisión operativa del modelo (P5):* Se revisan los resultados de los evaluadores offline.
  2. *Previsiones multihorizonte:* Se aprobó y verificó la integración end-to-end de la proyección a 4, 5 y 6 meses.