# Demo única de presentación

Preparación de #95, base d18408d y cambios locales de resumen individual. Entorno persistente único shareverance-predictor-demo, PostgreSQL shareverance_predictor_demo, URL **http://localhost:5176**. Java/Python/PostgreSQL sin publicar puertos al host. La única organización del recorrido es **Demo presentación Shareverance**; no alternar entre organizaciones de las guías técnicas antiguas. No se borraron esos conjuntos ni volúmenes habituales.

## Fechas previstas: 12 o 19 de noviembre de 2026

Se utiliza el mismo conjunto fijo en ambas fechas. No se desplazan ni actualizan importes al llegar noviembre. Seleccionar explícitamente abril–septiembre 2026 en Panel y octubre 2026 en Calendario; los valores predeterminados dependen del mes de la computadora. La previsión parte del corte histórico septiembre: octubre 2026 es su primer objetivo y seis meses llegan a marzo 2027. En noviembre, octubre ya será histórico respecto del día de presentación; sigue siendo una previsión desde aquel corte, no una estimación recalculada con datos posteriores. Las fechas/origen deben explicarse en el recorrido, sin presentar los datos como gastos reales actuales.

## Preparar, iniciar y detener

Desde raíz, PowerShell y Docker Desktop Linux iniciado:

```powershell
./scripts/presentation-demo.ps1 prepare
./scripts/presentation-demo.ps1 check
./scripts/presentation-demo.ps1 test
./scripts/presentation-demo.ps1 status
```

Prepare construye/inicia el mismo Compose existente y crea/verifica referencias por API Java. Es repetible, no sobrescribe datos divergentes ni restaura una copia inicial. Puede completar una carga parcial que siga concordando. Si alguien edita/anula un gasto de referencia, cambia un rol base o renombra una categoría base, se detiene con error para revisión; no corrige silenciosamente. No vuelve a sembrar otra organización ni usa SQL de escritura. La primera construcción puede necesitar Internet; ejecutar prepare y check antes de la presentación.

El día de la presentación:

```powershell
./scripts/presentation-demo.ps1 start
./scripts/presentation-demo.ps1 check
# Abrir http://localhost:5176
./scripts/presentation-demo.ps1 stop
```

Start sólo inicia/reconstruye contenedores, sin ejecutar la carga. Check consulta referencias, OCR del comprobante ya vinculado, analítica, previsiones y permisos; inicia sesiones técnicas, sin crear gastos/cobertura. Stop conserva volúmenes y datos. No ejecutar down -v, restauración ni limpieza durante el recorrido. Para repetir los tres tests del plan de datos, usar test después de prepare; no requieren red ni aplicación disponible, pero sí la imagen construida.

## Accesos exclusivamente sintéticos y locales

ADMIN y USER usan la misma clave habitual de pruebas confirmada para este entorno. El cargador usa PRESENTATION_PASSWORD desde la configuración local, no incluye esa contraseña en código/documentación ni cambia contraseñas de cuentas existentes. Si no puede iniciar sesión, se detiene en lugar de restablecerlas.

En esta computadora está configurada en demo/presentacion/.env.local, ignorado por Git. En otra computadora puede ingresarse sin mostrarla en consola:

```powershell
$presentationSecret = Read-Host 'Clave habitual de pruebas' -AsSecureString
$env:PRESENTATION_PASSWORD = [System.Net.NetworkCredential]::new('', $presentationSecret).Password
./scripts/presentation-demo.ps1 prepare
./scripts/presentation-demo.ps1 check
Remove-Item Env:PRESENTATION_PASSWORD
```

Alternativamente configurar PRESENTATION_PASSWORD en el archivo .env.local privado; nunca subirlo. Las cuentas auxiliares nuevas se crean con esa misma clave local. Start/stop/status no necesitan leerla.

| Cuenta | Rol / propósito |
| --- | --- |
| admin@shareverance.com | ADMIN en la organización principal; usar para comenzar |
| usuario@shareverance.com | USER; propia participación y altas a su nombre |
| invitado@shareverance.com | Cuenta existente sin membresía inicial, para probar incorporación/cambio de rol/desactivación/reactivación |
| ajeno@shareverance.com | Sin acceso a la organización; no incorporarlo, se usa para comprobar aislamiento |

No se cambia la contraseña de usuarios habituales ni se requieren cuentas personales. ADMIN es la cuenta habitual y conserva sus organizaciones previas; seleccionar **Demo presentación Shareverance**, cuyo ID informa check. USER tiene la organización de presentación. La copia técnica anterior con correos example.test se conserva con sus miembros/partes originales fuera del recorrido: no se reasignan gastos ni se borran datos para cambiar identidades. Hay un solo entorno y un conjunto oficial para las cuentas confirmadas.

## Datos y fechas del recorrido

Configuración versionada en demo/presentacion/config.json, corte **2026-09**, comprobante **2026-10-15**. Se toman las 192 filas de organización externa 1, enero 2023–diciembre 2024, del CSV V1 sintético. Conservan categorías/importes y se desplazan **21 meses** a octubre 2024–septiembre 2026. Marcadores registran mes original/categoría y el informe registra SHA-256 y desplazamiento. No se modifica el CSV ni se usa esta copia para aceptar modelos. No hay fuente oficial ni representatividad nacional.

El historial suma **ARS 31.211.212,92**, con 24 meses cerrados confirmados por Java. Las categorías se distribuyen de forma determinista entre individual ADMIN, individual USER e iguales entre ambos. El reparto se calcula por Java; los centavos sobrantes se asignan por ID de membresía ascendente. Una categoría histórica queda inactiva después de cargar los gastos, para comprobar conservación del historial. No se registra gasto futuro ni cobertura del mes en curso.

Casos recientes en la misma organización:

| Fecha | Gasto | Partes ADMIN / USER | Estado |
| --- | ---: | --- | --- |
| 01/10/2026 | $10,01 | $5,01 / $5,00 | Vigente |
| 05/10/2026 | $100,00 | $50 / $50 | Vigente |
| 10/10/2026 | $20 | $0 / $20 | Vigente |
| 12/10/2026 | $9,99 | Se conserva el reparto, excluido de totales vigentes | Anulado |
| 15/10/2026 | $1.234,56 | $0 / $1.234,56 | Vigente, comprobante OCR vinculado |

En octubre, antes de añadir ensayos: ADMIN **$55,01 / 2 gastos**, USER **$1.309,56 / 4 gastos**. En todo el historial hay 196 gastos vigentes y uno anulado (197 referencias). Los totales individuales generales incluyen también los repartos históricos; ver resultado de check, no confundir las cifras de octubre con todo el historial. Un gasto compartido cuenta una vez para la organización y una vez en el resumen de cada participante.

No cambiar el corte de configuración sobre la organización existente: las referencias de fechas deben coincidir. Si la presentación se traslada a otro período, revisar configuración, comprobantes y transformación antes de preparar nuevos datos; el script no desplaza datos ya cargados silenciosamente.

## Guion por funcionalidad

1. **Acceso y organización:** entrar como ADMIN, recargar y verificar sesión/única organización principal. Para probar registro, crear otra cuenta sintética de ensayo (sin usar datos personales). El invitado ya existe para probar el alta por email.
2. **Gastos e historial:** buscar «Presentación V1», filtrar octubre 2026, consultar estados Vigentes/Anulados/Todos y abrir detalles de los casos divididos. En $10,01, la suma de partes debe ser exacta. Titular/autor no sustituyen participación.
3. **Crear/editar/anular:** crear un gasto nuevo con descripción «Ensayo presentación ...» y fecha actual; probar individual, dos o grupo/partes iguales, modificarlo y anularlo. Para conflicto 409, abrir el mismo gasto de ensayo en dos pestañas y guardar una edición antes de enviar la otra. No editar ni anular las referencias precargadas.
4. **Mi resumen:** filtrar 01/10/2026–31/10/2026. ADMIN cambia el selector entre su participación y Usuario presentación; comprobar las cifras anteriores. Luego Todo el historial. Como USER no debe existir selector de otro miembro; las llamadas directas ajenas devuelven 403.
5. **Calendario:** abrir octubre 2026, consultar los días 1/5/10/15 y sus detalles. El anulado del día 12 no suma. Los agregados históricos se registraron al último día de cada mes y no representan hábitos diarios reales.
6. **Categorías:** crear una categoría de ensayo, cambiar nombre/descripción/color y desactivarla. Comprobar que la categoría histórica inactiva conserva sus gastos; no renombrar ni reactivar categorías base para evitar divergencias del control.
7. **Miembros:** incorporar invitado@shareverance.com como USER, cambiarle rol, desactivar y reactivar. Conservar roles base ADMIN/USER y no incorporar al ajeno. Java protege al último ADMIN. Los gastos nuevos con Todos incluyen los miembros activos del momento; las partes históricas no cambian.
8. **OCR y guardado:** cargar demo/presentacion/comprobante-sintetico.png desde el formulario. Esperar fecha 15/10/2026 e importe $1.234,56, revisar descripción y guardar un gasto de ensayo si se desea. La precarga es editable. demo/presentacion/comprobante-sin-texto.png sirve para comprobar aviso por falta de reconocimiento y carga manual. Cancelar no crea un gasto, aunque la subida temporal puede conservarse hasta mantenimiento.
9. **Panel:** seleccionar explícitamente abril–septiembre 2026; ese período cerrado tiene datos, aunque en noviembre el selector inicial pueda proponer otro rango. Consultar también octubre 2024–septiembre 2026, revisar evolución/categorías y la categoría histórica inactiva. Octubre 2026 no está confirmado: no se interpreta como mes completo ni cero conocido.
10. **Predicción:** abre cuatro meses desde septiembre 2026; cambiar a cinco/seis. Objetivos octubre 2026–marzo 2027, con histórico y proyección diferenciados. Próximo mes conserva octubre 2026. Para insuficiencia, usar origen noviembre 2024 (sólo dos meses). Son estimaciones sobre un conjunto sintético, sin intervalos calibrados ni aceptación de precisión real.
11. **Permisos y salida:** iniciar como USER, comprobar acceso al resumen propio y restricciones de administración. La cuenta ajena no lista la organización. Cerrar sesión y comprobar que la sesión no puede seguir operando.

La cobertura histórica está precargada por API; no existe aquí una pantalla nueva para administrarla. Alta de organizaciones nuevas conserva el flujo normal y requerirá cobertura declarada para usar predicción; no forma parte de los datos de referencia de esta demo.

## Control y límites

Check verifica fecha/importe OCR, asociación del comprobante, partes, anulados, analítica de 24 meses, modelo activo y horizontes 4/5/6, resúmenes USER/ADMIN y rechazos 403. Mantener los archivos PNG disponibles para la prueba de subida del humano. El blanco sólo es un caso de falta de texto; no certifica robustez OCR en fotos reales.

Las verificaciones automáticas no sustituyen el recorrido visual/humano, accesibilidad completa, concurrencia con usuarios reales o una evaluación predictiva nueva. Respaldo/restauración y limpieza son procedimientos operativos separados que requieren mantenimiento; no se ejecutaron contra la base de presentación. Los ejemplos antiguos del predictor/resumen se conservan como evidencia técnica, no como demos adicionales que haya que iniciar.


## Evidencia y regeneración de archivos sintéticos

[Comprobaciones y capturas](evidencia/presentacion/README.md). Los PNG se generan con scripts/generate_presentation_receipts.py y config.json usando Pillow de la imagen Python declarada; no son comprobantes reales. No hace falta regenerarlos para presentar. Si se cambia la fecha/configuración, revisar también las referencias de datos y volver a verificar OCR antes de usar nuevos archivos; no modificar silenciosamente los ya asociados.
