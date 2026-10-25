# Justificación del stack tecnológico

La arquitectura de **Shareverance** adopta una división por **especialización de responsabilidades**: cada componente tecnológico fue elegido por sus características para resolver un dominio específico del sistema.

```
[ Frontend: React + TypeScript (Vite) ]
               │ (HTTP / Cookies same-origin / CSRF)
               ▼
[ Backend de Negocio: Java (Spring Boot) ]
        │                       │
        │ (JDBC transaccional)  │ (HTTP interno / Bearer token)
        ▼                       ▼
[ PostgreSQL ] ◄──────── [ Servicio Especializado: Python (FastAPI) ]
  (Negocio + Vistas)     (Lectura de Vistas SQL + OCR Tesseract)
```

---

### 1. Java con Spring Boot: Núcleo de negocio y transaccionalidad

La elección de Java y Spring Boot responde a la necesidad de contar con un entorno de alta confiabilidad para la gestión contable, autorización y consistencia operativa:

* **Exactitud en cálculos monetarios**: El tipado estático y el soporte de enteros de precisión fija garantizan que el reparto equitativo de gastos y la distribución determinista de sobrantes se ejecuten sin errores de aproximación ni pérdidas de precisión.
* **Manejo transaccional estricto y concurrencia**: La gestión de transacciones declarativas y el control de concurrencia optimista aseguran la atomicidad del alta simultánea de gastos y partes, impidiendo inconsistencias contables o degradaciones concurrentes de administradores.
* **Seguridad y orquestación centralizada**: Spring Security permite gobernar la autenticación y las políticas de acceso por organización en un único punto de entrada, aislando al frontend de la infraestructura interna y ofreciendo degradación controlada ante indisponibilidad de servicios auxiliares.

---

### 2. Python con FastAPI: Analítica, modelos predictivos y OCR

La incorporación de Python y FastAPI como servicio auxiliar se fundamenta en las capacidades analíticas y de visión requeridas por la plataforma:

* **Ecosistema consolidado de Ciencia de Datos e IA**: Proporciona las bibliotecas estadísticas más maduras y eficientes para el desarrollo y evaluación de modelos predictivos.
* **Capacidades nativas para procesamiento de imágenes y OCR**: Facilita la implementación de la canalización de comprobantes mediante Tesseract OCR para la extracción automática de datos.
* **Contratos HTTP asíncronos y tipados**: FastAPI y Pydantic aseguran validaciones estrictas de esquemas de datos tanto en entrada como en salida, operando bajo el principio de mínimo privilegio con credenciales de solo lectura sobre vistas de base de datos.
* **Independencia del ciclo de vida**: Desacopla la calibración y experimentación de modelos predictivos de la lógica contable del sistema, permitiendo evolucionar los algoritmos sin comprometer la estabilidad del backend de negocio.

---

### 3. PostgreSQL: Persistencia relacional y segregación de accesos

PostgreSQL fue seleccionado como motor de base de datos para garantizar la integridad estructural y la seguridad a nivel de datos:

* **Garantías ACID e integridad referencial**: El dominio de gastos compartidos es de naturaleza relacional. Las claves foráneas y restricciones a nivel de motor garantizan la coherencia de datos entre organizaciones, usuarios, membresías, gastos y comprobantes.
* **Aislamiento multi-tenant por variables de sesión**: Permite reforzar el aislamiento entre organizaciones asegurando que las consultas analíticas nunca mezclen datos de distintos ámbitos.
* **Segregación de roles de base de datos**: Ofrece un control granular de permisos que habilita roles diferenciados para migraciones de esquema, operaciones de negocio y consultas de analítica.

---

### 4. React con TypeScript y Vite: Interfaz interactiva y tipado de contratos

La elección de React, TypeScript y Vite en el frontend se basa en la necesidad de ofrecer una interfaz reactiva, mantenible y fuertemente integrada con los contratos del servidor:

* **Tipado estricto de extremo a extremo con TypeScript**: Permite reflejar con exactitud los DTOs del backend (estructuras de gastos, estados de cobertura, insuficiencias y proyecciones de 4 a 6 meses), detectando incompatibilidades de datos en tiempo de compilación y evitando fallos en tiempo de ejecución.
* **Arquitectura basada en componentes y control de estados asíncronos**: El modelo declarativo de React facilita la gestión de vistas complejas permitiendo cancelar consultas obsoletas y evitar carreras de datos ante cambios rápidos de contexto o recargas.
* **Rendimiento y visualización liviana con Vite**: Proporciona un entorno de desarrollo ágil y optimizado para producción, facilitando la implementación de gráficos SVG nativos y accesibles para analítica y previsión sin necesidad de incorporar librerías externas pesadas.
