# Roadmap del servicio Python

Este documento describe el avance del servicio Python de Shareverance y las tareas pendientes. Cada etapa se considera completada después de verificar su funcionamiento.

## 1. Puesta en marcha del servicio — OK

- Se configuró el servicio Python con FastAPI y Uvicorn.
- Se incorporó su ejecución mediante Docker Compose.
- Se habilitó el endpoint `/health`.
- Se verificó que el servicio iniciara correctamente y respondiera con estado `UP`.
- Se comprobó el acceso directo desde el entorno local.

## 2. Estructura inicial y pruebas de la API — OK

Se implementó una estructura que permite recibir datos y devolver respuestas simuladas antes de desarrollar el modelo predictivo.

### Organización del código

- Separación de rutas HTTP, esquemas de datos, validaciones, servicio de predicción y predictor.
- Preparación del servicio al iniciar la aplicación.
- Inyección de dependencias para conectar la API con el servicio y permitir sustituir componentes durante las pruebas.
- Implementación de un predictor simulado, identificado mediante `simulated: true`.
- Conservación del endpoint de salud.

### Validaciones y manejo de errores

- Validación de campos obligatorios, tipos de datos e importes.
- Rechazo de campos desconocidos.
- Validación de meses duplicados y continuidad del historial.
- Comprobación de categorías declaradas y del mes objetivo.
- Respuestas de error con una estructura uniforme.
- Control del tipo de contenido y del tamaño de las solicitudes.
- Manejo de fallos internos sin exponer detalles técnicos al cliente.

### Herramientas y dependencias utilizadas

- **Pydantic:** definición y validación de los datos.
- **pytest:** ejecución de pruebas automatizadas.
- **TestClient de FastAPI y HTTPX:** envío de solicitudes dentro de las pruebas automatizadas.
- **uv y uv.lock:** gestión y bloqueo de dependencias.
- **Hoppscotch:** pruebas manuales contra el servicio en ejecución.

Las versiones y dependencias directas se mantienen en `service-python/pyproject.toml` y `service-python/uv.lock`.

### Verificaciones realizadas

- Se ejecutaron las pruebas automatizadas: **37 aprobadas y una advertencia**.
- Se ejecutaron **11 casos manuales en Hoppscotch**:
  1. Solicitud válida.
  2. Campo obligatorio faltante.
  3. Tipo de dato incorrecto.
  4. Importe negativo.
  5. Campo desconocido.
  6. Mes duplicado.
  7. Historial discontinuo.
  8. Categoría desconocida.
  9. Mes objetivo incorrecto.
  10. JSON mal formado.
  11. Tipo de contenido no admitido.
- Se documentaron pasos, resultados y capturas en el documento de casos de prueba.

Los casos manuales presentaron el comportamiento esperado en los campos comprobados. La captura de la solicitud válida muestra los resultados generales y parte de las categorías; queda pendiente registrar la respuesta completa.

Esta etapa valida la estructura de la API y su comportamiento con un predictor simulado. La calidad predictiva se evaluará cuando se implemente el modelo real.

## 3. Revisión y cierre de la estructura inicial — PENDIENTE

- [ ] Revisar las dependencias agregadas y sus versiones.
- [ ] Revisar la advertencia de compatibilidad entre el cliente de pruebas y HTTPX.
- [ ] Completar la evidencia de todas las categorías de la respuesta válida.
- [ ] Comprobar la construcción y ejecución desde una copia limpia de la rama.
- [ ] Registrar el commit correspondiente a las pruebas.
- [ ] Presentar el pull request y completar la revisión del equipo.

## 4. Integración con Java — PENDIENTE

- [ ] Probar el envío de datos desde Java hacia Python por HTTP.
- [ ] Comprobar que Java interprete correctamente la respuesta.
- [ ] Verificar el tratamiento de errores y de historial insuficiente.
- [ ] Probar el comportamiento cuando Python no esté disponible.
- [ ] Acordar los tiempos de espera de las solicitudes.
- [ ] Validar los datos preparados por Java a partir de PostgreSQL.

El formato de intercambio se consulta en el documento independiente del contrato Python.

## 5. Desarrollo del modelo predictivo — PENDIENTE

- [ ] Seleccionar y documentar el método de predicción.
- [ ] Definir los requisitos de historial del modelo.
- [ ] Implementar el predictor real manteniendo el contrato acordado.
- [ ] Generar predicciones generales y por categoría.
- [ ] Definir cómo identificar categorías que tienden a crecer.
- [ ] Documentar la versión del modelo y sus limitaciones.

## 6. Evaluación y pruebas del modelo — PENDIENTE

- [ ] Separar los datos de desarrollo de los datos de evaluación.
- [ ] Evaluar el error de las predicciones.
- [ ] Agregar pruebas automatizadas del cálculo predictivo.
- [ ] Repetir las pruebas de la API con el predictor real.
- [ ] Ejecutar pruebas completas de integración con Java.
- [ ] Documentar los resultados obtenidos.

## Mantenimiento

Actualizar este roadmap después de cada avance verificado. Mantener las tareas pendientes sin marcar hasta completar su implementación y comprobación.

Las herramientas y decisiones técnicas incorporadas como ampliaciones del diseño son aportes externos a la clase.