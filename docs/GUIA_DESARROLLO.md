# Guía de desarrollo y mantenimiento del entorno

Ayuda para trabajar con Shareverance después del primer arranque. La instalación inicial está en el [README](../README.md). La [ENTORNO INICIAL](ENTORNO_INICIAL.md) explica cómo se construyó la configuración y qué hace cada archivo.


## Convención de ejecución

Ejecutar los comandos desde la raíz del repositorio, en PowerShell. Se utilizan `compose.yaml` y `compose.tools.yaml` para permitir acceso directo a los servicios. En la configuración básica, quitar `-f compose.yaml -f compose.tools.yaml`; solo se publica el frontend. Mantener la misma elección al iniciar y detener.

Puertos externos habituales: frontend 5173, Java 8080, Python 8000 y PostgreSQL 5433. Las variables locales de `.env` pueden cambiarlos si están ocupados. Esto no modifica los puertos internos ni las direcciones entre contenedores.

## 1. Acceder directamente a los servicios

Con ambos archivos Compose y las variables del ejemplo anterior:

| Servicio | Dirección o conexión | Uso |
| --- | --- | --- |
| Frontend | http://localhost:5173 | Pantalla de comprobación del entorno |
| Java | http://localhost:8080/api/dev/status | Diagnóstico de comunicación, habilitado en desarrollo |
| Java | http://localhost:8080/actuator/health | Estado de salud de la API |
| Python | http://localhost:8000/health | Estado de salud de Python |
| Python | http://localhost:8000/docs | Documentación interactiva de FastAPI |
| PostgreSQL | Host `localhost`, puerto `5433` | Conexión desde pgAdmin u otro cliente SQL |

Si se cambian los puertos de `.env`, adaptar estas direcciones. PostgreSQL no se abre como una página web: el cliente necesita nombre de base, usuario y contraseña definidos en `.env`. Desde los contenedores, su dirección es `postgres:5432`.

Los puertos publicados se vinculan a `127.0.0.1`, para pruebas en la propia PC. Esta configuración corresponde al desarrollo local.

## 2. Actualizar la rama actual

Primero revisar el trabajo local:

```powershell
git status
```

Guardar los cambios que se quieran conservar mediante un commit antes de actualizar. No descartar archivos para forzar el cambio de rama. Con el directorio de trabajo limpio:

```powershell
git pull --ff-only
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

`--ff-only` evita crear un merge inesperado al actualizar. Si Git informa que las ramas divergieron, resolverlo con el equipo. Revisar también cambios en `.env.example` y agregar las variables necesarias a `.env`.

La reconstrucción incorpora código y dependencias de la rama actual. `docker compose restart` solo reinicia los contenedores existentes: no construye una imagen nueva.

## 3. Ejecutar otra rama o crear una propia

Conservar primero el trabajo local. Luego detener los servicios y consultar las ramas:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml down
git fetch origin --prune
git branch -r
```

Para una rama que ya existe localmente:

```powershell
git switch NOMBRE_RAMA
git pull --ff-only
```

Para empezar a seguir una rama remota que no existe localmente:

```powershell
git switch --track origin/NOMBRE_RAMA
```

Para crear una rama propia desde la rama en la que se está trabajando:

```powershell
git switch -c feat/nombre-del-cambio
```

Después de seleccionar la rama, revisar su README y `.env.example`, y levantar su código:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

**La base de datos no cambia de versión al cambiar de rama.** El volumen conserva los datos y las migraciones ya aplicadas. Volver a una rama anterior no deshace el esquema. No modificar migraciones ya ejecutadas ni borrar el volumen como rutina.

Si una rama requiere una base independiente para una prueba, detener primero el entorno habitual y usar otro nombre de proyecto:

```powershell
docker compose -p shareverance-prueba -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

Ese proyecto crea su propio volumen. Usar el mismo `-p shareverance-prueba` en todos sus comandos, incluida la detención. Los puertos de la PC deben estar libres; por eso se detiene antes el entorno habitual.

## 4. Trabajar con cambios locales

Los Dockerfiles copian el código durante la construcción. Actualmente no hay montaje del código fuente para recarga automática: guardar un archivo en la PC no actualiza el contenedor.

Para reconstruir únicamente el servicio modificado, elegir el comando que corresponda:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 java-service
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 python-service
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 frontend
```

Si cambiaron varios servicios o la configuración compartida, ejecutar el arranque completo. Para ver errores:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml logs --tail 100 java-service python-service frontend
docker compose -f compose.yaml -f compose.tools.yaml logs -f python-service
```

`Ctrl+C` termina el seguimiento de los logs; los servicios siguen ejecutándose. Ejecutar sin Docker sería una modalidad diferente, con runtimes y configuración local propios; esta guía utiliza los contenedores como entorno de ejecución.

## 5. Detener y volver a iniciar

Para finalizar la sesión:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml down
```

Esto elimina los contenedores y la red del proyecto, pero conserva el volumen de PostgreSQL. Para volver a trabajar, ejecutar `up --build -d --wait --wait-timeout 300` con ambos archivos.

Para pausar sin eliminar los contenedores:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml stop
docker compose -f compose.yaml -f compose.tools.yaml start
```

`start` sirve para contenedores existentes; después de `down` hay que utilizar `up`.

**No agregar `-v` a `down` en el uso habitual:** elimina también los volúmenes y los datos de la base. Cambiar las credenciales en `.env` tampoco cambia automáticamente las credenciales de una base ya inicializada.

## 6. Incorporar bibliotecas en Java

Las dependencias se declaran en `backend-java/pom.xml`, dentro de `<dependencies>`. Usar las coordenadas indicadas por la documentación oficial de la biblioteca. Este fragmento es una plantilla, no una dependencia real:

```xml
<dependency>
    <groupId>GRUPO_DE_LA_BIBLIOTECA</groupId>
    <artifactId>NOMBRE_DEL_ARTEFACTO</artifactId>
    <version>VERSION_COMPATIBLE</version>
</dependency>
```

Si Spring Boot gestiona la versión de esa dependencia, omitir `<version>` y conservar su gestión centralizada. Si no la gestiona, definir una versión compatible con Java 21 y el proyecto. No copiar archivos JAR manualmente al contenedor.

Reconstruir Java:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 java-service
```

Maven resuelve las dependencias durante la construcción. Revisar los logs y probar la función incorporada. Subir a Git `pom.xml` y los cambios de código o configuración necesarios. Si la función cambia el esquema, agregar una nueva migración SQL; no editar una migración aplicada. Se mantiene JDBC: agregar una biblioteca no implica incorporar JPA/Hibernate.

## 7. Incorporar bibliotecas o frameworks en Python

`service-python/pyproject.toml` declara las dependencias; `service-python/uv.lock` conserva las versiones resueltas. Se deben actualizar y subir **ambos archivos**. El Dockerfile instala con el bloqueo existente y falla si quedó desactualizado.

Se puede ejecutar uv desde un contenedor temporal, sin instalar Python en Windows. Desde la raíz del repositorio, reemplazar `nombre-del-paquete` por la dependencia elegida:

```powershell
$PythonSource = (Resolve-Path .\service-python).Path
docker run --rm --mount "type=bind,source=$PythonSource,target=/app" -w /app python:3.13.16-slim-bookworm sh -c "pip install --no-cache-dir uv==0.12.19 && uv add --no-sync nombre-del-paquete"
```

`--mount` permite que uv escriba los archivos en la carpeta de la PC. `--no-sync` actualiza la declaración y el bloqueo sin crear un entorno virtual Linux en esa carpeta. Elegir versiones compatibles; para fijar una concreta, usar `nombre-del-paquete==VERSION`.

Revisar y reconstruir:

```powershell
git diff -- service-python/pyproject.toml service-python/uv.lock
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 python-service
```

Probar `/health`, `/docs` y el diagnóstico del frontend. No instalar paquetes únicamente con `docker compose exec ... pip install`: esa modificación no queda declarada y se pierde al recrear el contenedor.

Agregar un framework no siempre es solo agregar una dependencia. FastAPI y Uvicorn ya forman parte del arranque actual. Si se reemplazan, adaptar también el código, el comando de inicio del Dockerfile y la comprobación de salud. Conservar o actualizar de manera coordinada el contrato HTTP que utiliza Java. Las bibliotecas del predictor o del OCR se elegirán cuando se retomen esos módulos.

## 8. Incorporar bibliotecas en React

`frontend/package.json` declara las dependencias y `frontend/package-lock.json` bloquea la resolución. Para agregar una biblioteca desde Docker, reemplazar el nombre del ejemplo:

```powershell
$FrontendSource = (Resolve-Path .\frontend).Path
docker run --rm --mount "type=bind,source=$FrontendSource,target=/app" -w /app node:24.21.0-bookworm-slim npm install --package-lock-only --save-exact nombre-del-paquete
```

Para una herramienta usada únicamente durante el desarrollo, agregar `--save-dev`. Este comando actualiza los archivos de dependencias; la instalación efectiva del contenedor se realiza después con `npm ci`.

```powershell
git diff -- frontend/package.json frontend/package-lock.json
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 frontend
```

Subir los dos archivos y el código correspondiente. No subir `node_modules`.

## 9. Dependencias del sistema y cambios compartidos

Si una biblioteca requiere programas o bibliotecas nativas del sistema operativo, declararlos en el Dockerfile del servicio. Esto puede ser necesario al implementar OCR; instalar el paquete Python por sí solo podría no alcanzar. Si cambia un puerto interno o una variable requerida, revisar también Compose, los clientes, las pruebas de salud y `.env.example`.

Los cambios en los archivos de dependencias no reemplazan las pruebas funcionales. Antes de compartir una rama, reconstruir los servicios afectados, comprobar sus logs y volver a verificar las conexiones. Subir los archivos que permiten reproducir el cambio; no subir `.env`, entornos virtuales ni carpetas generadas.

Referencias externas: [Docker Compose](https://docs.docker.com/reference/cli/docker/compose/up/), [volúmenes y detención](https://docs.docker.com/reference/cli/docker/compose/down/), [uv](https://docs.astral.sh/uv/concepts/projects/dependencies/), [Maven](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) y [npm](https://docs.npmjs.com/cli/commands/npm-install/).

## 10. Resolver un puerto ocupado

Si Docker informa que un puerto ya está asignado, elegir otro puerto externo para ese servicio en `.env`. Por ejemplo, si Python no puede publicar 8000:

```dotenv
PYTHON_HOST_PORT=8001
```

Recrear los servicios con la configuración actualizada:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

En esa PC se accede entonces a `http://localhost:8001/docs`. Java sigue utilizando `http://python-service:8000`. Este ajuste local no se sube a Git ni cambia el puerto habitual de los demás integrantes.

## 11. Crear y aplicar migraciones de base de datos

Una migración es un archivo SQL que describe un cambio en la base de datos: crear tablas, agregar columnas, definir restricciones o modificar datos.

En este proyecto utilizamos **Flyway**, integrado con Spring Boot. Al iniciar Java, Flyway consulta qué migraciones ya se ejecutaron y aplica las pendientes en orden.

**Flyway y esta configuración son aportes externos a la clase.** El contenido de las migraciones se escribe en SQL.

### 11.1. Ubicación y nombre del archivo

Las migraciones de estructura se guardan en:

```text
backend-java/src/main/resources/db/migration/
```

El nombre sigue este formato:

```text
V<version>__<descripcion>.sql
```

- `V`: identifica una migración versionada.
- `<version>`: número de versión, sin repetirlo en otro archivo.
- `__`: dos guiones bajos que separan la versión de la descripción.
- `<descripcion>`: nombre breve del cambio, usando guiones bajos entre palabras.
- `.sql`: extensión del archivo.

Ejemplos:

```text
V1__crear_tabla_demostracion.sql
V2__modelo_inicial.sql
V3__agregar_indice_gastos.sql
```

Por ejemplo `V2_Modelo_Inicial.sql` no respeta el formato: le falta el segundo guion bajo.

Antes de crear una migración, coordinar su número con el equipo para evitar que dos ramas incorporen archivos con la misma versión.

### 11.2. Crear la siguiente migración

Desde PowerShell, en la raíz del repositorio:

```powershell
New-Item -ItemType File -Path .\backend-java\src\main\resources\db\migration\V2__modelo_inicial.sql
```

Abrir el archivo en el editor y escribir el SQL. Guardarlo con codificación UTF-8.

V2 debe incluir los cambios nuevos. No copiar dentro el contenido de V1 ni volver a crear las tablas que ya existen.

### 11.3. Ejemplo de construcción

El siguiente ejemplo muestra cómo crear dos tablas relacionadas. 

Contenido de `V2__modelo_inicial.sql`:

```sql
-- Primero se crea la tabla que será referenciada.
CREATE TABLE organization (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'ARS',
    timezone VARCHAR(100) NOT NULL
        DEFAULT 'America/Argentina/Buenos_Aires',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Después se crea la tabla que depende de organization.
CREATE TABLE category (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(7),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_category_organization
        FOREIGN KEY (organization_id)
        REFERENCES organization (id)
);

-- Facilita las consultas de categorías de una organización.
CREATE INDEX idx_category_organization
    ON category (organization_id);
```

Qué hace este código:

- `CREATE TABLE` crea una tabla.
- `GENERATED BY DEFAULT AS IDENTITY` genera identificadores automáticamente.
- `PRIMARY KEY` identifica cada registro de forma única.
- `NOT NULL` exige que el campo tenga un valor.
- `DEFAULT` establece el valor usado si no se proporciona uno.
- `FOREIGN KEY` impide registrar una categoría con una organización inexistente.
- `CREATE INDEX` crea una estructura que puede acelerar las consultas por organización.

El orden importa: `organization` debe existir antes de crear la clave foránea de `category`.

Antes de aplicar V2, completar y revisar el SQL del modelo acordado. No aplicar este ejemplo parcial si se pretende que V2 contenga todo el modelo inicial.

### 11.4. Aplicar la migración

Desde la raíz del repositorio:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300 java-service
```

Este comando reconstruye la imagen de Java para incorporar el nuevo archivo SQL y arranca el servicio.

Durante el inicio, Flyway:

1. Consulta la tabla `flyway_schema_history`.
2. Valida las migraciones ya aplicadas.
3. Detecta las versiones pendientes.
4. Ejecuta sus archivos SQL en orden.
5. Registra las migraciones aplicadas correctamente.

No ejecutar el archivo manualmente en pgAdmin: Flyway debe aplicar el cambio y registrar su historial.

### 11.5. Verificar el resultado

Consultar los logs de Java:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml logs --tail 100 java-service
```

Desde pgAdmin, conectado a la base del proyecto:

```sql
SELECT version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

V1 y V2 deberían aparecer con `success = true`. También puede aparecer la carga repetible de demostración, cuyo campo `version` es nulo.

Para comprobar las tablas creadas por el ejemplo:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('organization', 'category')
ORDER BY table_name;
```

Finalmente, volver a comprobar las conexiones desde el frontend.

### 11.6. Realizar cambios posteriores

Las migraciones versionadas se ejecutan una sola vez por base de datos.

| Estado de la base | Comportamiento |
| --- | --- |
| Base vacía | Ejecuta V1 y después V2 |
| V1 ya aplicada | Ejecuta V2 |
| V1 y V2 aplicadas | No vuelve a ejecutarlas |
| Se incorpora V3 | Ejecuta V3 si está pendiente |

Una vez que una migración fue aplicada y compartida, conservar su nombre y contenido. Flyway guarda una comprobación de su contenido; modificarla puede causar un error de validación.

Por ejemplo, para agregar más adelante un índice sobre el nombre de la organización, crear:

```text
V3__agregar_indice_nombre_organizacion.sql
```

Contenido:

```sql
-- Agrega un índice sin volver a crear la tabla.
CREATE INDEX idx_organization_name
    ON organization (name);
```

Reconstruir Java con el mismo comando para aplicar V3. El índice es un ejemplo: incorporarlo solo si las consultas del proyecto lo justifican.

### 11.7. Compartir la migración con el equipo

Subir el archivo SQL a Git junto con el código que depende del cambio. Cada compañero obtiene la migración al actualizar su rama y reconstruir Java.

No hace falta compartir una copia de la base ni crar tablas manualmente.

Tener en cuenta:

- No editar migraciones ya aplicadas y compartidas.
- No reutilizar números de versión.
- No borrar el volumen de PostgreSQL para aplicar una migración nueva.
- Cambiar de rama no revierte las migraciones de la base.
- Revisar especialmente cambios que eliminan columnas, tablas o datos.
- Si una migración falla, revisar los logs y el estado de la base antes de volver a intentarlo.

Los archivos `V...` se usan para cambios versionados. Los archivos `R__...` son repetibles: Flyway los vuelve a ejecutar cuando cambia su contenido. La carga `R__datos_demostracion.sql` utiliza este segundo mecanismo y está preparada para no duplicar registros.

Referencia técnica externa: [Migraciones versionadas de Flyway](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations).