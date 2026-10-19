# Instrucciones para agentes

## Lectura obligatoria antes de actuar

Leer siempre README.md, docs/road_map.md y docs/alcance_mvp.md para conocer el estado vigente. Luego leer los documentos del componente afectado y docs/contratos_api.md si cambia una integración. Revisar código, migraciones, git status, rama y diferencias locales. No deducir implementación a partir de una especificación o un estado histórico.

## Coordinación de tareas y dependencias

Antes de iniciar una tarea, revisar el roadmap, los issues, el proyecto, las ramas remotas y los PR abiertos. Comprobar si existe trabajo equivalente, si otra persona lo está realizando y si las dependencias necesarias están implementadas.

Si existe superposición, una asignación a otra persona o una dependencia incompleta que impide realizar la tarea, informar la situación y preguntar cómo continuar. No reasignar tareas ni asumir disponibilidad de componentes todavía pendientes.

Crear un issue no constituye una reserva exclusiva del trabajo. Reutilizar la tarea existente cuando corresponda. El agente no modifica estados del tablero ni cierra tareas.

## Ramas y conflictos: regla obligatoria

Antes de modificar archivos, revisar ramas remotas y PR abiertos relevantes, incluidos sus cambios de alcance, diseño, contratos y código. Si los cambios propuestos entran en conflicto con alguna rama abierta, avisar, describir la superposición y preguntar cómo continuar ANTES de realizar los cambios afectados. No resolver el conflicto silenciosamente ni sobrescribir trabajo ajeno. Se puede continuar trabajo independiente sin conflicto.

Si no es posible comprobar ramas/PR remotos, indicarlo expresamente y no afirmar que no hay conflictos. Un nombre distinto de rama no prueba independencia. No cerrar, integrar, rebasar ni eliminar ramas ajenas para despejar el conflicto.

## Actualización de la rama de trabajo

Antes de entregar, consultar origin/main y comprobar si los cambios nuevos afectan la tarea. No es obligatorio reescribir una rama que sigue siendo compatible.

Todos los merges, incluidos los merges locales de main hacia una rama de trabajo, son manuales.

El agente puede actualizar mediante rebase únicamente su propia rama cuando no está publicada ni compartida y no existen conflictos con trabajo ajeno. Si la rama está publicada, compartida o el rebase presenta conflictos, informar y preguntar cómo continuar antes de modificar su historial.

No realizar push forzado, incluido --force-with-lease, sin autorización explícita. No realizar push directo a main ni a ramas protegidas.

## Coordinación de migraciones Flyway

Antes de crear una migración, revisar las versiones existentes en main, ramas remotas y PR abiertos. Registrar en el issue la versión prevista y comprobar que no exista otra tarea utilizando el mismo número.

Antes de entregar, repetir la comprobación de versiones.

Solo puede renumerarse una migración propia si se comprobó que no fue aplicada en ninguna base ni compartida como versión estable. Si fue aplicada, si otra persona la está utilizando o si no puede verificarse su situación, no renumerarla: informar la colisión y preguntar cómo continuar.

Nunca modificar ni renumerar migraciones ya aplicadas para resolver silenciosamente una colisión.

## Estabilidad de contratos compartidos

Antes de modificar un contrato, revisar sus consumidores y las tareas, ramas y PR relacionados. Esta regla comprende APIs HTTP, DTO, vistas SQL, formatos de respuesta y referencias de archivos utilizadas por OCR.

Si el cambio afecta trabajo activo en otra rama, informar su impacto y preguntar cómo continuar antes de realizarlo.

No introducir unilateralmente cambios incompatibles. Una nueva versión requiere definir consumidores, compatibilidad, transición y pruebas; crear una ruta /v2/ no reemplaza esa coordinación.

## Ediciones documentales concurrentes

Mantener la obligación de actualizar los documentos afectados y el roadmap en cada entrega.

En el roadmap, limitar las modificaciones a la tarea correspondiente. Registrar evidencia identificada por tarea, sin modificar estados ajenos ni reorganizar tablas, secciones o formato de manera incidental.

No marcar una tarea como completada ni cerrar su seguimiento. Registrar por separado lo implementado, las comprobaciones ejecutadas y sus resultados; la aceptación y el cierre son manuales.

Si aparece un conflicto documental con otra rama, mostrar las diferencias y preguntar cómo continuar. No descartar contenido ajeno ni elegir automáticamente una de las versiones.

Leer el estado de los documentos otra vez si cambiaron durante la tarea. Distinguir requerido, implementado y verificado. No marcar pruebas como PASS si no se ejecutaron. Registrar limitaciones y parámetros abiertos sin inventar resoluciones. Documentación autónoma: no referenciar conversaciones, planillas de definición ni procesos de aprobación como fuente de requisitos.

## Comunicación entre componentes

React consume Java como única entrada del backend para el frontend. Java autoriza las operaciones, escribe los datos de negocio en PostgreSQL y solicita a Python las operaciones de OCR, analítica y predicción.

Python consulta PostgreSQL mediante vistas autorizadas y una credencial propia de solo lectura. Para OCR, lee imágenes desde el volumen persistente compartido, montado con permisos de solo lectura en su contenedor.

## Límites de arquitectura y MVP

ARS única, calendario Buenos Aires, reparto individual/iguales/personalizado por importes (CUSTOM). No incorporar caja, pagos, liquidaciones, recurrencias ni multimoneda al MVP.

Conservar funcionalidades existentes del frontend. No introducir JPA ni reorganizar servicios como consecuencia incidental de una tarea. Respetar parámetros abiertos del roadmap; plantear una decisión cuando afecte alcance o contrato.

## Seguridad y datos

No subir .env, secretos, tokens, boletas reales ni datos personales. No exponer PostgreSQL/Python fuera del entorno local. No borrar volúmenes ni purgar base de desarrollo. Pruebas destructivas solo en base aislada explícitamente identificada. Respetar pertenencia de organización en cada operación y evitar paths arbitrarios en OCR.

## Ejecución y validación

Usar comandos vigentes del README, inspeccionar cambios antes de actualizar ramas y preservar trabajo local. Verificar reglas con pruebas apropiadas; PostgreSQL real para integridad, permisos SQL y concurrencia; mocks para aislamiento unitario. Validar enlaces Markdown. No instalar dependencias sin declararlas y actualizar su archivo de bloqueo.

## Registro de tareas en GitHub Projects

Proyecto: https://github.com/users/seba615/projects/3. Repositorio: seba615/shareverance.

Al iniciar una tarea, identificar la cuenta autenticada de GitHub y buscar una tarea equivalente para evitar duplicados. Si no existe, crear un issue con título y descripción exclusivamente en español, asignarlo a la cuenta autenticada que está trabajando y agregarlo al proyecto. No inferir la cuenta por el propietario del repositorio o por user.name de Git. No crear un issue nuevo por cada mensaje o reanudación de una misma tarea.

La función del agente en el tablero se limita a crear/vincular la tarea y asignarla. No cambiar estados del tablero, marcar tareas como completadas, cerrar issues ni automatizar su cierre. La evaluación de finalización y el cierre son exclusivamente manuales. Si hay una tarea existente asignada a otra persona, no reasignarla silenciosamente: informar la situación y preguntar cómo continuar.

Si faltan capacidades o permisos para identificar la cuenta, asignarla o agregar el issue al proyecto, informar exactamente qué operación no se pudo realizar. No afirmar que la tarea quedó registrada/asignada ni usar otra identidad como alternativa implícita.

## Git y pull requests: límites obligatorios

Para publicar el trabajo, el agente puede crear commits, hacer push de su rama de trabajo y crear pull requests. Títulos, descripciones y textos de tareas/PR deben estar en español. Referenciar el issue con «Relacionado con #N»; no utilizar palabras de cierre automático como Closes, Fixes o Resolves.

Cada merge debe realizarse manualmente por una persona. El agente no puede ejecutar merges locales o remotos, integrar PR, habilitar auto-merge, incorporar PR a una cola de integración ni configurar automatizaciones que completen tareas o integren ramas. No hacer push directo a main ni a ramas protegidas. No sustituir el merge manual por otro mecanismo de integración. La creación del PR y un resultado de pruebas satisfactorio no autorizan a cerrar la tarea ni a integrar el cambio.

## Entrega

Describir cambio, propósito, comprobaciones ejecutadas y límites. Identificar archivos y commit/PR cuando existan. No afirmar publicación, integración, despliegue o prueba sin evidencia. Los documentos normativos son los enlazados desde README; no crear una segunda especificación paralela.
