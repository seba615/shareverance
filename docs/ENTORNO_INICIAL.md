# Shareverance — Guía manual de entorno: qué se configuró y por qué

Este documento explica cómo se construyó el entorno y qué función cumple cada archivo. Está pensado para que el equipo comprenda la configuración existente y pueda mantenerla. 

**No es necesario volver a crear estos archivos: para clonar, iniciar, actualizar o detener el proyecto ver [README](../README.md) y [GUIA DE DESARROLLO](GUIA_DESARROLLO.md).**


## Resultado y alcance

React → Java → PostgreSQL y Java → Python. El diagnóstico inicial comprueba comunicación y dos registros técnicos. No se implementa codigo ni funciones propias de la aplicación. 

Esta guía no incluye CI/CD ni Nginx. El entorno es local, con la entrada publicada solamente en localhost. 

## Versiones acordadas

| Componente | Versión |
| --- | --- |
| Java | 21, Eclipse Temurin; imagen fijada por digest |
| Spring Boot | 4.0.8 |
| Maven | 3.9.11 |
| Node.js | 24.21.0 |
| React / React DOM | 19.3.0 |
| Vite | 8.3.3 |
| TypeScript | 7.0.2 |
| Python | 3.13.16 |
| FastAPI | 0.142.2 |
| Uvicorn | 0.54.0 |
| uv | 0.12.19 |
| PostgreSQL | 17.11 |
| Flyway y controlador JDBC | Versiones gestionadas por Spring Boot 4.0.8 |

## 1. Instalación de herramientas

**Qué se hizo.** Se eligieron Git y Docker Desktop como herramientas locales. Docker ejecuta Java, Python, Node y PostgreSQL con los entornos definidos en sus imágenes.

**Por qué.** Cada integrante evita instalar y coordinar por separado todos esos runtimes. WSL 2 permite a Docker Desktop ejecutar los contenedores Linux desde Windows. La virtualización y los recursos disponibles siguen siendo requisitos del equipo.

**Cómo interpretar los comandos.** `--version` identifica las herramientas; `docker info` comprueba que el cliente pueda comunicarse con el motor. Tener el comando Docker instalado no implica que el motor esté iniciado.

Instalar Git y Docker Desktop para Windows. Configurar Docker Desktop con backend WSL 2 y contenedores Linux. Seguir las indicaciones del instalador, reiniciar si lo solicita y abrir Docker Desktop. Necesitás conexión a Internet para descargar imágenes y dependencias.

Fuentes oficiales:
- https://git-scm.com/downloads/win
- https://docs.docker.com/desktop/setup/install/windows-install/

No hace falta instalar Java, Python, Node o PostgreSQL localmente para ejecutar esta configuración con Docker. Usar Compose 2.20 o posterior.

Desde PowerShell:

```powershell
git --version
docker --version
docker compose version
docker info
```

Si docker info no encuentra el motor, resolver el inicio de Docker antes de continuar.

## 2. Preparación del repositorio y la rama

**Qué se hizo.** La infraestructura se preparó en una rama para revisarla antes de integrarla.

**Por qué.** Git conserva el historial y permite revisar cambios sin alterar de inmediato la rama compartida. `git status` muestra cambios locales, `branch --show-current` identifica la rama y `remote -v` muestra el repositorio remoto. `pull --ff-only` admite una actualización por avance directo y se detiene si hace falta resolver una divergencia.

**Para quien clona el proyecto.** Esta sección explica el armado inicial; no es necesario volver a crear `chore/entorno-inicial`. La rama que se ejecuta se elige con las instrucciones del README.

```powershell
Set-Location 'C:\Proyectos\shareverance'
git status
git branch --show-current
git remote -v
```

Guardar o confirmar los cambios pendientes antes de cambiar de rama. Crear la rama desde main (o develop si esa es la rama de integración):

```powershell
git switch main
git pull --ff-only
git switch -c chore/entorno-inicial
```

Si la rama ya existe, revisar su contenido y utilizar git switch chore/entorno-inicial. Si OneDrive genera conflictos de sincronización o rendimiento con las herramientas, considerar después una copia del repositorio fuera de la carpeta sincronizada; no es obligatorio moverlo para seguir la guía.

## 3. Creación de carpetas

**Qué se hizo.** Se separó el código por aplicación: `frontend`, `backend-java` y `service-python`. `docs` contiene documentación y `scripts` controles auxiliares.



## 4. Archivos generales

**Qué se hizo.** Se separaron archivos versionados, ajustes locales y archivos generados. 

**Por qué.** El equipo comparte una configuración de referencia, mientras conserva sus puertos y credenciales locales. Git y Docker tienen filtros distintos: `.gitignore` controla qué se agrega al historial y `.dockerignore` qué se envía al construir una imagen.

Si ya existe .gitignore, integrar las entradas sin borrar las anteriores. .env se mantiene privado; .env.example se comparte.

### Archivo `.gitignore`

**Qué hace este archivo.** Los patrones excluyen `.env`, entornos y resultados de construcción. `!.env.example` permite compartir el ejemplo aunque `.env.*` esté ignorado. No protege archivos que Git ya estuviera siguiendo: un secreto previamente versionado requiere corregir su seguimiento e historial según el caso.

```gitignore
.env
.env.*
!.env.example
node_modules/
dist/
target/
.venv/
__pycache__/
*.pyc
.idea/
.vscode/
```

### Archivo `.gitattributes`

**Qué hace este archivo.** `text=auto` indica a Git que identifique archivos de texto. `eol=lf` mantiene finales de línea LF en los tipos indicados, útiles al ejecutar herramientas Linux desde Docker. No define codificación; los archivos se guardan en UTF-8 desde el editor.

```gitattributes
* text=auto
*.sh text eol=lf
Dockerfile text eol=lf
*.yaml text eol=lf
*.sql text eol=lf
```

### Archivo `.env.example`

**Qué hace este archivo.** `WEB_HOST_PORT` define la entrada externa al frontend. Las variables `POSTGRES_*` inicializan PostgreSQL y alimentan la conexión de Java. Los valores son de desarrollo. Copiar el ejemplo genera `.env`, que Compose lee localmente. Cambiar una contraseña en `.env` no cambia automáticamente la contraseña de una base ya inicializada en el volumen.

```dotenv
# Solo desarrollo local. No usar estas credenciales en un servidor público.
WEB_HOST_PORT=5173
POSTGRES_DB=shareverance
POSTGRES_USER=shareverance_dev
POSTGRES_PASSWORD=shareverance_local_only
```


## 5. Frontend

**Qué se hizo.** Se creó una pantalla React que solicita `/api/dev/status`. Vite recibe esa ruta y la dirige a Java.

**Por qué.** El navegador solo necesita la dirección del frontend. El nombre `java-service` se resuelve dentro de Docker; el navegador de Windows no lo resuelve directamente. El proxy evita configurar una dirección de API diferente en cada componente de React.

**Qué no resuelve todavía.** Es una pantalla de diagnóstico, no la aplicación financiera. El servidor Vite se utiliza para desarrollo, no como decisión de publicación.

package.json define dependencias y comandos. tsconfig.json define la revisión de tipos. Vite sirve React y envía /api hacia Java dentro de la red Docker. Los archivos main y App crean la pantalla de diagnóstico.

### Archivo `frontend/package.json`

**Qué hace este archivo.** `private` evita publicar accidentalmente este proyecto en npm. `type: module` selecciona módulos de JavaScript. `scripts` define los comandos de desarrollo y construcción. React se utiliza en la aplicación; TypeScript, Vite y sus tipos se usan para desarrollarla y construirla. Se fijan versiones directas; `package-lock.json` registra también las indirectas.

```json
{
  "name": "shareverance-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0",
    "build": "tsc --noEmit && vite build"
  },
  "dependencies": {
    "react": "19.3.0",
    "react-dom": "19.3.0"
  },
  "devDependencies": {
    "@types/react": "19.3.0",
    "@types/react-dom": "19.3.0",
    "@vitejs/plugin-react": "6.1.2",
    "typescript": "7.0.2",
    "vite": "8.3.3"
  }
}
```

### Archivo `frontend/tsconfig.json`

**Qué hace este archivo.** `target` y `lib` establecen capacidades de JavaScript y del navegador conocidas por TypeScript. `jsx` permite componentes React. `strict` activa revisiones de tipos; `skipLibCheck` evita revisar internamente todos los archivos de declaraciones de terceros. `noEmit` comprueba tipos sin producir archivos JavaScript; Vite genera la compilación. `include` define qué archivos revisar.

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": [
      "ES2022",
      "DOM",
      "DOM.Iterable"
    ],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "skipLibCheck": true,
    "noEmit": true,
    "allowImportingTsExtensions": true
  },
  "include": [
    "src",
    "vite.config.ts"
  ]
}
```

### Archivo `frontend/vite.config.ts`

**Qué hace este archivo.** El plugin permite trabajar con React. `host: 0.0.0.0` acepta conexiones desde fuera del propio contenedor; Compose limita la entrada externa a localhost. `strictPort` evita que Vite cambie de puerto silenciosamente. El proxy conserva `/api` y reenvía la solicitud a Java. El puerto externo del frontend puede variar sin cambiar este archivo.

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0', port: 5173, strictPort: true,
    proxy: { '/api': { target: 'http://java-service:8080' } },
  },
});
```

### Archivo `frontend/index.html`

**Qué hace este archivo.** Es el documento que recibe el navegador. `charset` selecciona UTF-8 y `viewport` adapta el área visible a la pantalla. `div#root` es el punto de montaje de React. El script carga el módulo `main.tsx`; Vite procesa ese código durante el desarrollo y la construcción.

```html
<!doctype html>
<html lang="es"><head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1.0"/><title>Shareverance · Entorno local</title></head><body><div id="root"></div><script type="module" src="/src/main.tsx"></script></body></html>
```

### Archivo `frontend/src/main.tsx`

**Qué hace este archivo.** Importa React, el componente principal y el CSS. `createRoot` conecta la aplicación con `div#root`; `render` muestra `App`. El signo `!` comunica a TypeScript que ese elemento existe, pero no crea el elemento. `StrictMode` ayuda a detectar ciertos problemas durante desarrollo.

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './style.css';
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
```

### Archivo `frontend/src/App.tsx`

**Qué hace este archivo.** `Status` describe la forma esperada de la respuesta; no valida por sí solo un JSON recibido en ejecución. `useState` guarda resultado, error y estado de espera. `check` limpia el resultado anterior, hace `fetch`, controla el código HTTP y muestra el JSON. `finally` vuelve a habilitar el botón incluso si hay error. El render muestra cada campo; `/api/dev/status` pasa por el proxy de Vite. Todavía no se cargan gastos ni se autentican usuarios.

```tsx
import { useState } from 'react';
type Status = { java: string; database: string; demoRows: number; python: string };
export default function App() {
  const [result, setResult] = useState<Status | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function check() {
    setBusy(true); setError(''); setResult(null);
    try {
      const response = await fetch('/api/dev/status');
      if (!response.ok) throw new Error(`Java respondió HTTP ${response.status}`);
      setResult(await response.json());
    } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo conectar'); }
    finally { setBusy(false); }
  }
  return <main>
    <p>Shareverance · Desarrollo</p><h1>Comprobar el entorno</h1>
    <p>Esta pantalla verifica la comunicación entre servicios. Las funciones de negocio se desarrollarán después.</p>
    <button onClick={check} disabled={busy}>{busy ? 'Comprobando…' : 'Verificar conexiones'}</button>
    {error && <p role="alert">{error}</p>}
    {result && <dl>{Object.entries(result).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>}
  </main>;
}
```

### Archivo `frontend/src/style.css`

**Qué hace este archivo.** Define colores, tipografía, espacios y presentación del diagnóstico. `button:disabled` indica visualmente una solicitud en curso. CSS modifica la apariencia y no calcula saldos ni controla permisos.

```css
body { margin: 0; font-family: system-ui, sans-serif; background: #faf8f3; color: #1f2937; }
main { max-width: 720px; margin: 70px auto; padding: 24px; }
h1 { color: #1e3a5f; } button { padding: 12px 18px; border: 0; border-radius: 8px; background: #2b5fb8; color: white; cursor: pointer; }
button:disabled { opacity: .6; } dl div { display: flex; gap: 24px; padding: 10px; border-bottom: 1px solid #e8dfc8; } dt { width: 130px; font-weight: 600; } dd { margin: 0; }
```

### Archivo `frontend/src/vite-env.d.ts`

**Qué hace este archivo.** La referencia carga los tipos que proporciona Vite, por ejemplo para importaciones de recursos. No es código que se ejecute en el navegador; ayuda a la comprobación de TypeScript.

```typescript
/// <reference types="vite/client" />
```

### Generar package-lock.json

**Qué se hizo y por qué.** npm resolvió versiones directas e indirectas y guardó su integridad en el bloqueo. Ese archivo se comparte; cada compañero instala lo ya resuelto con `npm ci`. `docker run --rm` usa y elimina un contenedor temporal; `--mount` permite escribir el archivo en la carpeta local y `-w` selecciona su directorio de trabajo.

No escribirlo manualmente. Desde la raíz del repositorio, ejecutar npm dentro de un contenedor temporal que escribe en la carpeta frontend:

```powershell
$frontendPath = Join-Path $PWD.Path 'frontend'
docker run --rm --mount "type=bind,source=$frontendPath,target=/app" -w /app node:24.21.0-bookworm-slim npm install --package-lock-only --ignore-scripts --no-audit --no-fund
```

Verificar que apareció frontend/package-lock.json. El Dockerfile lo utiliza con npm ci para instalar lo registrado. La instalación inicial genera el lock con las dependencias indirectas disponibles; la selección queda compartida al confirmar este archivo en Git.

### Archivo `frontend/Dockerfile`

**Qué hace este archivo.** `FROM` fija la imagen Node. `WORKDIR` define el directorio interno. Se copian primero los manifiestos y se ejecuta `npm ci`, que instala desde el bloqueo; esa capa se puede reutilizar si no cambian las dependencias. Luego se copia código y `npm run build` comprueba tipos y construcción. `EXPOSE` documenta el puerto y no lo publica. `CMD` inicia Vite en desarrollo: la compilación generada no es lo que sirve este comando.

```dockerfile
FROM node:24.21.0-bookworm-slim@sha256:d6aa754f16b3197301076f047b5def2f02ea1dbbc2ca920407d46d7ec7f87b20
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
EXPOSE 5173
CMD ["npm", "run", "dev"]
```

### Archivo `frontend/.dockerignore`

**Qué hace este archivo.** Evita enviar `node_modules`, `dist`, `.env` y metadatos Git como parte de la construcción del frontend. Docker utiliza el contexto `frontend`, por lo que este filtro se aplica a esa carpeta.

```text
node_modules
dist
.env
.git
```

## 6. Python

**Qué se hizo.** Se creó una API mínima con FastAPI, ejecutada por Uvicorn y preparada con uv.

**Por qué.** Java puede consultar a Python mediante HTTP sin depender de su código interno. Python no recibe credenciales de PostgreSQL en esta configuración. Las bibliotecas de predicción u OCR se podrán agregar después conservando el contrato de comunicación.

**Distinción.** FastAPI define la aplicación; Uvicorn la ejecuta; uv instala y bloquea sus dependencias. Son responsabilidades diferentes.

FastAPI expone el endpoint de salud. Uvicorn ejecuta la API. uv administra el entorno y el bloqueo de dependencias. No se eligen todavía las bibliotecas de predicción ni OCR.

### Archivo `service-python/pyproject.toml`

**Qué hace este archivo.** La sección `project` identifica el servicio, declara Python 3.13 como serie admitida y enumera dependencias directas. El proyecto funciona como aplicación, no necesita publicarse como paquete. FastAPI y Uvicorn son la base HTTP; no determinan qué algoritmo predictivo se utilizará.

```toml
[project]
name = "shareverance-python"
version = "0.1.0"
requires-python = ">=3.13,<3.14"
dependencies = ["fastapi==0.142.2", "uvicorn==0.54.0"]
```

### Archivo `service-python/.python-version`

**Qué hace este archivo.** Registra el intérprete preferido para las herramientas que reconocen este archivo. No instala Python por sí solo; la imagen del Dockerfile proporciona el intérprete. `requires-python` indica compatibilidad de la aplicación y `.python-version` la selección concreta del entorno.

```text
3.13.16
```

### Archivo `service-python/app/__init__.py`

Se mantiene vacío. Identifica `app` como un paquete Python convencional y permite organizar sus módulos. No necesita contener lógica de negocio ni configuración para que `app.main` pueda importarse.

### Archivo `service-python/app/main.py`

**Qué hace este archivo.** Crea el objeto ASGI `app`. El decorador `@app.get` registra una ruta GET. `health` devuelve un diccionario que FastAPI serializa a JSON. El comando Uvicorn importa ese objeto mediante `app.main:app`. Este endpoint comprueba que la API responde; cuando se incorpore un modelo habrá que definir si su preparación forma parte de la salud.

```python
from fastapi import FastAPI

app = FastAPI(title="Shareverance - Servicio Python", version="0.1.0")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "python"}
```

### Generar uv.lock

**Qué se hizo y por qué.** uv calculó el conjunto de dependencias y lo registró en `uv.lock`. No se debe editar a mano ni volver a resolverlo como parte del primer inicio de cada compañero. El contenedor temporal monta la carpeta para escribir el bloqueo; `pip install uv` prepara uv solo en ese contenedor temporal.

Desde la raíz ejecutar:

```powershell
$pythonPath = Join-Path $PWD.Path 'service-python'
docker run --rm --mount "type=bind,source=$pythonPath,target=/app" -w /app python:3.13.16-slim-bookworm sh -c "pip install uv==0.12.19 && uv lock"
```

Verificar que apareció service-python/uv.lock. El contenedor temporal ya tiene Python 3.13.16; no necesita descargar otro intérprete. No editar uv.lock a mano.

### Archivo `service-python/Dockerfile`

**Qué hace este archivo.** La primera etapa aporta el ejecutable uv; la imagen Python aporta el intérprete. `UV_PYTHON_DOWNLOADS=never` evita descargar otro Python durante la construcción. Las otras variables evitan bytecode y facilitan ver logs sin buffering. `uv sync --locked` exige coherencia entre manifiesto y bloqueo; `--no-dev` excluye dependencias de desarrollo y `--no-install-project` no instala la aplicación como paquete. Uvicorn se ejecuta desde `.venv` y escucha en 8000 dentro del contenedor.

```dockerfile
FROM ghcr.io/astral-sh/uv:0.12.19 AS uv
FROM python:3.13.16-slim-bookworm@sha256:a1165e272e578941b84abc79e4ab38a0305cd12803a5c4247979ac7655f4d641
COPY --from=uv /uv /usr/local/bin/uv
WORKDIR /app
ENV UV_PYTHON_DOWNLOADS=never PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --locked --no-dev --no-install-project
COPY app ./app
EXPOSE 8000
CMD ["/app/.venv/bin/uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### Archivo `service-python/.dockerignore`

**Qué hace este archivo.** Excluye el entorno virtual de la computadora y archivos generados. Un entorno Windows no se copia dentro de un contenedor Linux; el Dockerfile prepara su propio entorno.

```text
.venv
__pycache__
*.pyc
.env
.git
```

## 7. Java y JDBC

**Qué se hizo.** Se configuró Spring Boot con controladores HTTP, JDBC, Flyway y Actuator. El diagnóstico utiliza una conexión real con PostgreSQL y una llamada real a Python.

**Por qué.** Java administra las reglas de negocio y la persistencia. Se eligió JDBC para mantener visible el SQL y evitar incorporar un ORM en esta etapa. JDBC no elimina la necesidad de transacciones, permisos o manejo de concurrencia.

**Lectura desde Python.** El constructor recibe `JdbcTemplate` y la configuración desde afuera: es inyección de dependencias. Spring crea y conecta esos objetos, en lugar de que el controlador abra manualmente cada conexión.

Maven administra la construcción. Spring MVC expone el diagnóstico; JdbcTemplate consulta PostgreSQL. Actuator verifica preparación del backend. Flyway aplica migraciones al iniciar. No se incluyen JPA/Hibernate ni Spring Security en esta prueba técnica; la seguridad sigue formando parte del desarrollo del MVP.

### Archivo `backend-java/pom.xml`

**Qué hace este archivo.** El parent de Spring Boot administra versiones compatibles. `groupId`, `artifactId` y `version` identifican el proyecto. `java.version` configura Java 21. Web MVC atiende HTTP; JDBC administra acceso SQL; Actuator expone salud; Flyway y su módulo PostgreSQL aplican migraciones; el driver convierte JDBC en comunicación con PostgreSQL. `scope: runtime` del driver permite disponer de él al ejecutar. `finalName` fija el nombre del JAR y el plugin Boot lo empaqueta con sus dependencias. No se agregó ORM ni autenticación a esta base técnica.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.0.8</version><relativePath/></parent>
  <groupId>com.shareverance</groupId><artifactId>backend</artifactId><version>0.1.0</version>
  <properties><java.version>21</java.version><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  </dependencies>
  <build><finalName>shareverance</finalName><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
```

### Archivo `backend-java/src/main/java/com/shareverance/ShareveranceApplication.java`

**Qué hace este archivo.** Es el punto de entrada. `main` inicia Spring Boot; `@SpringBootApplication` habilita configuración automática y búsqueda de componentes en `com.shareverance` y sus subpaquetes, incluido `dev`. La ruta física y `package` deben ser coherentes.

```java
package com.shareverance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class ShareveranceApplication {
    public static void main(String[] args) { SpringApplication.run(ShareveranceApplication.class, args); }
}
```

### Archivo `backend-java/src/main/java/com/shareverance/dev/DevelopmentController.java`

**Qué hace este archivo.** `@Profile("dev")` limita el controlador al perfil de desarrollo. `@RestController` devuelve datos como respuesta HTTP. El constructor recibe JDBC y la dirección Python; crea un cliente con límite de conexión. `record Status` agrupa los campos de salida. `SELECT 1` comprueba consulta y `count(*)` revisa el seed. La solicitud GET a Python tiene timeout; `BodyHandlers.discarding()` no conserva su cuerpo, por lo que esta prueba solo exige HTTP 200. Una excepción de entrada/salida deja Python UNAVAILABLE; una interrupción restaura la marca del hilo. Los fallos de base no se ocultan como UP.

```java
package com.shareverance.dev;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
public class DevelopmentController {
    private final JdbcTemplate jdbc;
    private final URI pythonHealth;
    private final HttpClient client;
    public DevelopmentController(JdbcTemplate jdbc, @Value("${python.service.url}") String pythonUrl) {
        this.jdbc = jdbc;
        this.pythonHealth = URI.create(pythonUrl + "/health");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    public record Status(String java, String database, long demoRows, String python) {}
    @GetMapping("/api/dev/status")
    public Status status() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        Long count = jdbc.queryForObject("SELECT count(*) FROM infra_demo", Long.class);
        String python = "UNAVAILABLE";
        try {
            var request = HttpRequest.newBuilder(pythonHealth).timeout(Duration.ofSeconds(3)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) python = "UP";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.io.IOException e) {
            // Una caída de Python no impide consultar la base.
        }
        return new Status("UP", "UP", count == null ? 0 : count, python);
    }
}
```

### Archivo `backend-java/src/main/resources/application.yaml`

**Qué hace este archivo.** Configura Java en 8080, el nombre de la aplicación y la ubicación de migraciones. Actuator publica solo salud; la preparación incluye el estado del proceso y la conexión a base. La URL de Python se toma de una variable con valor interno por defecto. Las credenciales de PostgreSQL llegan por variables de Compose, no se escriben aquí.

```yaml
server:
  port: 8080
spring:
  application:
    name: shareverance-java
  flyway:
    locations: classpath:db/migration
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
python:
  service:
    url: ${PYTHON_SERVICE_URL:http://python-service:8000}
```

### Archivo `backend-java/src/main/resources/application-dev.yaml`

**Qué hace este archivo.** Se combina con la configuración general cuando `SPRING_PROFILES_ACTIVE=dev`. Agrega `db/dev` a las ubicaciones Flyway. En otros perfiles, el seed de desarrollo no se incluye automáticamente.

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/dev
```

## 8. Migraciones, datos técnicos y empaquetado Java

**Qué se hizo.** Se versionó un cambio de estructura y una carga técnica de desarrollo; luego se definió cómo empaquetar Java.

**Por qué.** Una base vacía puede prepararse automáticamente al iniciar. `infra_demo` queda aislada del modelo financiero para verificar el entorno sin introducir reglas pendientes. Los datos de ejemplo no son aportes ni gastos reales.

**Orden.** PostgreSQL acepta conexiones, Flyway aplica cambios y después Java puede quedar preparado. La comprobación de salud no sustituye la validación de migraciones.

V1 tiene dos guiones bajos entre versión y nombre. Crea una tabla técnica aislada del modelo de negocio. R__ es una migración repetible que se incluye solo con el perfil dev: Flyway la ejecuta la primera vez y cuando cambia su contenido, no necesariamente en todos los arranques.

### Archivo `backend-java/src/main/resources/db/migration/V1__crear_tabla_demostracion.sql`

**Qué hace este archivo.** Crea `infra_demo`: la clave primaria evita repetir identificadores y `UNIQUE` evita repetir etiquetas. Flyway registra versión, ejecución y checksum en su historial. V1 no se ejecuta de nuevo en cada arranque; una corrección estructural compartida se agrega como nueva migración.

```sql
-- Tabla técnica de demostración; no pertenece al modelo de negocio definitivo.
CREATE TABLE infra_demo (
    id UUID PRIMARY KEY,
    label VARCHAR(100) NOT NULL UNIQUE
);
```

### Archivo `backend-java/src/main/resources/db/dev/R__datos_demostracion.sql`

**Qué hace este archivo.** Inserta dos UUID estables y etiquetas. `ON CONFLICT DO NOTHING` conserva los registros que ya existan y evita duplicarlos. No actualiza un registro anterior ni restablece el escenario: repetir una carga y reiniciar datos son operaciones distintas.

```sql
-- Solo se incluye con el perfil dev. No altera registros ya existentes.
INSERT INTO infra_demo (id, label) VALUES
('00000000-0000-0000-0000-000000000001', 'Organización de demostración'),
('00000000-0000-0000-0000-000000000002', 'Escenario de comunicación')
ON CONFLICT DO NOTHING;
```

### Archivo `backend-java/Dockerfile`

**Qué hace este archivo.** Es una construcción multietapa. La imagen Maven contiene el JDK para compilar y empaquetar. La imagen final incluye un JRE para ejecutar el JAR; no requiere Maven. `curl` se instala para la comprobación de salud de Compose. `COPY --from=build` lleva solo el JAR al entorno final. `EXPOSE` no publica el puerto; `CMD` ejecuta la aplicación.

```dockerfile
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package
FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/shareverance.jar ./app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Archivo `backend-java/.dockerignore`

**Qué hace este archivo.** Excluye resultados locales y ajustes privados de la construcción Java. El JAR se genera dentro de la etapa Maven, por lo que no depende de `target` de una computadora.

```text
target
.env
.git
```

Las migraciones definitivas de usuarios, organizaciones, gastos y movimientos se incorporan durante el desarrollo. Una migración aplicada y compartida se conserva; para modificar la estructura se agrega una nueva versión.

## 9. Compose

**Qué se hizo.** Compose define cuatro servicios, una red compartida y un volumen con nombre.

**Por qué.** Es el punto común de coordinación: evita iniciar cada aplicación con conexiones y parámetros diferentes. Dentro de un contenedor, `localhost` identifica al propio contenedor; por eso Java usa `postgres:5432` y `python-service:8000`.

**Imagen, contenedor y volumen.** La imagen contiene el entorno y el código. El contenedor es una ejecución de esa imagen. El volumen conserva los datos fuera de su ciclo de vida. Un cambio de código requiere reconstrucción porque esta base usa `COPY`, sin montajes de código para recarga automática.

postgres acepta conexiones antes de iniciar Java. Java no depende del estado de Python para arrancar: el diagnóstico representa una caída de Python como UNAVAILABLE. Los contenedores comparten una red interna y se encuentran por sus nombres. El navegador entra por el puerto del frontend. El volumen conserva los datos de PostgreSQL.

### Archivo `compose.yaml`

**Qué hace este archivo.** `name` fija el nombre del proyecto y agrupa recursos. `build` define los contextos de construcción y `image` el PostgreSQL descargado. `${VARIABLE:-valor}` usa un valor por defecto y `${VARIABLE:?mensaje}` requiere configuración. `$$` deja la variable para que la interprete el contenedor, en vez de Compose. `depends_on: service_healthy` ordena preparación inicial. Las comprobaciones utilizan intervalos, timeout, reintentos y tolerancia inicial. El volumen con nombre conserva PostgreSQL. Los servicios se conectan por nombre dentro de la red; solo frontend publica puerto en la configuración básica.

```yaml
name: shareverance
services:
  postgres:
    image: postgres:17.11-bookworm@sha256:3645570cccdfa447589da9f57dd740faa29b30938e861289a5574b6ca6b03826
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-shareverance}
      POSTGRES_USER: ${POSTGRES_USER:-shareverance_dev}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Copiar .env.example a .env}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 5s
      timeout: 3s
      retries: 20
      start_period: 15s
  python-service:
    build: ./service-python
    healthcheck:
      test: ["CMD", "/app/.venv/bin/python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 15s
  java-service:
    build: ./backend-java
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-shareverance}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-shareverance_dev}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:?Copiar .env.example a .env}
      PYTHON_SERVICE_URL: http://python-service:8000
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "--fail", "--silent", "http://127.0.0.1:8080/actuator/health/readiness"]
      interval: 10s
      timeout: 5s
      retries: 20
      start_period: 60s
  frontend:
    build: ./frontend
    ports:
      - "127.0.0.1:${WEB_HOST_PORT:-5173}:5173"
    depends_on:
      java-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:5173').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
      interval: 10s
      timeout: 5s
      retries: 12
volumes:
  postgres_data:
```

## 10. Construir y comprobar

**Qué se hizo.** Se validó la configuración, se construyeron imágenes y se esperó la preparación de los servicios.

**Por qué.** Que una imagen compile no demuestra comunicación entre servicios. La pantalla de diagnóstico comprueba el recorrido completo.

**Flags.** `config --quiet` valida y no imprime toda la configuración; `--build` reconstruye; `-d` deja los servicios en segundo plano; `--wait` espera estados de ejecución/salud; `--wait-timeout 300` limita esa espera a 300 segundos. `ps` muestra el estado y `logs` ayuda a diagnosticar errores.

```powershell
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 300
docker compose ps
```

La primera construcción puede tardar varios minutos. El límite --wait-timeout corresponde a la espera de preparación del entorno. Si falla, consultar los logs antes de repetir.

Abrir http://localhost:5173 (o el puerto de .env) y pulsar Verificar conexiones. Resultado esperado:

```json
{"java":"UP","database":"UP","demoRows":2,"python":"UP"}
```

Si Python todavía está arrancando, esperar a que aparezca healthy y volver a comprobar.

```powershell
docker compose logs --tail 100 java-service
docker compose logs --tail 100 python-service
docker compose logs --tail 100 postgres
docker compose logs --tail 100 frontend
```

Como copiamos código a las imágenes, cada cambio requiere reconstruir el servicio correspondiente. La recarga mediante carpetas montadas se puede agregar después.

## 11. Comprobación manual desde PowerShell

**Qué se hizo.** Se agregó un script que revisa el mismo diagnóstico que utiliza React.

**Por qué.** Facilita una comprobación manual repetible sin depender de leer visualmente la pantalla. Una respuesta HTTP correcta no basta: el script revisa sus campos y espera dos registros.

**Alcance.** Es un control de infraestructura. No reemplaza pruebas de reglas de negocio, permisos ni operaciones financieras.

### Archivo `scripts/check.ps1`

**Qué hace este archivo.** `Stop` convierte errores en fallos del script. `Push-Location` trabaja desde la raíz y `finally` restaura la ubicación anterior. Lee WEB_HOST_PORT desde .env, consulta el endpoint y revisa todos los valores. `Invoke-RestMethod` convierte el JSON en un objeto PowerShell. La política Bypass del comando de ejecución se aplica a ese proceso; no cambia de forma permanente la política de la computadora.

```powershell
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $port = "5173"
    if (Test-Path .env) {
        $line = Get-Content .env | Where-Object { $_ -match '^WEB_HOST_PORT=' } | Select-Object -Last 1
        if ($line) { $port = ($line -split '=', 2)[1].Trim() }
    }
    $r = Invoke-RestMethod -Uri "http://localhost:$port/api/dev/status" -TimeoutSec 15
    $r | Format-List
    if ($r.java -ne 'UP' -or $r.database -ne 'UP' -or $r.python -ne 'UP' -or $r.demoRows -ne 2) {
        throw "La comprobación no pasó. Revisar logs y estado de los servicios."
    }
    Write-Host "Entorno verificado: Java, PostgreSQL, Python y 2 registros de demostración." -ForegroundColor Green
} finally { Pop-Location }
```

Ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check.ps1
```

Bypass se aplica solo a ese proceso. También se puede verificar manualmente en el navegador.

## 12. Persistencia y seed sin duplicados

**Qué se hizo.** Se reinició el entorno y se volvió a ejecutar el SQL de demostración.

**Por qué.** Son dos comprobaciones diferentes: persistencia del volumen e idempotencia de la carga. `INSERT 0 0` después de repetir el SQL indica cero filas insertadas; los registros ya existentes se conservaron.

**Límite.** Dos registros siguen siendo dos después de la carga, pero esta prueba no verifica todavía el cálculo de saldos ni las tablas financieras.

```powershell
docker compose down
docker compose up -d --wait --wait-timeout 300
```

Volver a verificar: demoRows debe ser 2. No usar down -v para esta prueba: elimina el volumen y sus datos.

Para probar que repetir el SQL no duplica registros, primero configurar salida UTF-8 y luego ejecutar dos veces el comando de carga:

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 .\backend-java\src\main\resources\db\dev\R__datos_demostracion.sql | docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Volver a ejecutar la línea de Get-Content y comprobar que demoRows sigue siendo 2. Los identificadores y restricciones evitan duplicados; no se reinician los datos.

## 13. Falla de Python

**Qué se hizo.** Se detuvo solamente Python y luego se lo volvió a iniciar.

**Por qué.** Una capacidad de análisis indisponible no debe inutilizar la consulta del negocio. La llamada tiene límites de espera y el diagnóstico devuelve `UNAVAILABLE` para Python. Java sigue accediendo a PostgreSQL.

**Flags y efecto.** `stop` detiene el contenedor sin eliminarlo; `start` inicia un contenedor existente. Estos comandos no reconstruyen código ni instalan dependencias nuevas.

```powershell
docker compose stop python-service
```

El botón debe seguir devolviendo Java y database UP, con python UNAVAILABLE. Restablecer:

```powershell
docker compose start python-service
```

Esperar que esté saludable y comprobar otra vez. Los healthchecks iniciales no reemplazan los límites de espera y manejo de errores durante la ejecución.

## 14. Acceso opcional con pgAdmin y a las APIs

**Qué se hizo.** Se agregó un archivo Compose complementario que publica puertos para consultar directamente PostgreSQL, Java y Python desde la computadora.

**Por qué.** Permite utilizar pgAdmin, abrir la documentación de FastAPI y probar Java sin pasar por React. No modifica las direcciones internas entre servicios.

**Qué significa publicar.** `127.0.0.1:8001:8000` dirige el puerto 8001 de la computadora al puerto 8000 del contenedor y limita el acceso al equipo local. Si un puerto externo está ocupado, se cambia en `.env`, no en el código Python ni en la dirección interna de Java.

Solo crear y utilizar este archivo si necesitan accesos directos desde la computadora.

### Archivo `compose.tools.yaml`

**Qué hace este archivo.** Es una extensión del archivo principal: ambos se combinan por nombre de servicio. Publica puertos sin reemplazar la configuración de las aplicaciones. La primera parte es el puerto de la computadora y la segunda el interno. Los valores por defecto son PostgreSQL 5433, Java 8080 y Python 8000; se pueden cambiar en .env. `127.0.0.1` limita acceso al equipo. Usar ambos archivos de manera consistente mantiene estos accesos durante actualizaciones.

```yaml
# Uso opcional: docker compose -f compose.yaml -f compose.tools.yaml up --build -d
services:
  postgres:
    ports:
      - "127.0.0.1:${POSTGRES_HOST_PORT:-5433}:5432"
  java-service:
    ports:
      - "127.0.0.1:${JAVA_HOST_PORT:-8080}:8080"
  python-service:
    ports:
      - "127.0.0.1:${PYTHON_HOST_PORT:-8000}:8000"
```

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

Puertos externos opcionales: PostgreSQL 5433, Java 8080 y Python 8000. Se pueden modificar en .env con POSTGRES_HOST_PORT, JAVA_HOST_PORT y PYTHON_HOST_PORT. Los internos siguen siendo 5432, 8080 y 8000.

Para pgAdmin instalado localmente, conectar a localhost:5433 y usar las credenciales de .env. Si pgAdmin está en otro contenedor, localhost representa a ese contenedor y la conexión requiere otra configuración.

Usar los mismos dos archivos Compose para detener esta variante:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml down
```

### Ejemplo de conflicto de puerto externo

Si el puerto 8000 ya está ocupado, agregar o actualizar en `.env`:

```dotenv
PYTHON_HOST_PORT=8001
```

Luego recrear usando ambos archivos:

```powershell
docker compose -f compose.yaml -f compose.tools.yaml up --build -d --wait --wait-timeout 300
```

La documentación queda en [http://localhost:8001/docs](http://localhost:8001/docs), mientras Java sigue utilizando `http://python-service:8000`. La conexión interna y la externa tienen propósitos distintos. El valor 8001 es un ejemplo local, no un cambio obligatorio de los puertos de todos los integrantes.

### Recorrido completo que debe poder explicarse

1. El navegador carga React desde Vite.
2. React solicita `/api/dev/status`.
3. Vite reenvía esa solicitud a Java.
4. Java consulta PostgreSQL y el endpoint de Python.
5. Java devuelve los campos del diagnóstico como JSON.
6. React muestra el resultado.

`healthy` comprueba cada servicio con su propia prueba; el botón confirma el recorrido de integración. Reiniciar prueba persistencia y repetir el SQL prueba idempotencia. Detener Python prueba que Java puede seguir funcionando cuando esa dependencia está indisponible.

### Referencias técnicas externas

- [Docker Compose: arranque y salud](https://docs.docker.com/compose/how-tos/startup-order/).
- [Docker Compose: construcción y recreación](https://docs.docker.com/reference/cli/docker/compose/up/).
- [Docker Compose: detención y volúmenes](https://docs.docker.com/reference/cli/docker/compose/down/).
- [npm ci](https://docs.npmjs.com/cli/commands/npm-ci/).
- [uv: bloqueo y sincronización](https://docs.astral.sh/uv/concepts/projects/sync/).
- [Maven: dependencias](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html).
- [Spring Boot 4.0: requisitos](https://docs.spring.io/spring-boot/4.0/system-requirements.html).

Las versiones y configuraciones se revisan cuando cambien las necesidades; no implican que debamos adoptar automáticamente la versión más nueva de cada herramienta.
