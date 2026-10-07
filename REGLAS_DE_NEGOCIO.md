# Shareverance — Reglas de negocio

Actualizado: 3 de octubre de 2026.

Este documento las definiciones para el MVP, las decisiones que todavía debe cerrar el equipo y los criterios para continuar el desarrollo. Complementa el modelo de datos del [README.md](README.md). 


## 1. Para el MVP

### 1.1 Organizaciones y participantes

Cada participante del reparto es un usuario miembro de la organización. La cuenta personal se representa con USER y su participación, rol y saldo dentro de un grupo con USER_ORGANIZATION.

Una persona puede pertenecer a varias organizaciones y tener roles distintos en cada una. Sus saldos se mantienen separados por organización. El MVP no incluye áreas o departamentos independientes de las cuentas personales.

El diseño debe facilitar una futura incorporación de unidades de gasto con responsables propios. Esa ampliación requerirá cambios de datos y pantallas; por ahora no se agrega esa entidad.

### 1.2 Gasto, autor, reparto y pagador

| Concepto | Qué significa |
|---|---|
| Gasto | Importe de un consumo o egreso de la organización, con fecha y categoría. |
| Autor | Usuario que registra el gasto. No necesariamente puso el dinero. |
| Reparto | Importe que cada participante debe asumir. |
| Pagador | Usuario o fondo común que entrega dinero al proveedor. |

Un gasto individual tiene una parte asignada; un gasto compartido tiene varias. Cada parte es positiva y la suma debe coincidir exactamente con el total del gasto. No se incluye al mismo participante dos veces ni se agrega una parte de importe cero.

EXPENSE conserva al autor en created_by_user_id. La asignación a participantes pasa a EXPENSE_SPLIT y se elimina user_id de EXPENSE. Los pagos se registran aparte en EXPENSE_PAYMENT.

### 1.3 Aportes, pagos y reintegros

El sistema contempla tres operaciones diferentes:

| Operación | Efecto económico al confirmarse |
|---|---|
| Aporte al fondo | Aumenta el dinero del fondo y el crédito individual de quien aporta. |
| Pago personal de un gasto | Aumenta el crédito del pagador y no modifica el fondo. |
| Pago desde el fondo | Reduce el dinero del fondo y no genera crédito individual nuevo. |
| Reintegro entre usuarios | Reduce la deuda del emisor y el crédito del destinatario; no modifica el fondo. |

**Un pago personal no exige aporte previo, crédito individual ni dinero disponible en el fondo.** El origen del dinero se identifica en cada pago: USER para dinero personal y FUND para el fondo común. El sistema no verifica la cuenta bancaria personal del pagador.

Solo los pagos desde el fondo requieren caja disponible. Si un gasto se paga usando ambos orígenes, se registran pagos separados. Los pagos confirmados más los pendientes vigentes no pueden superar el importe del gasto.

El mismo dinero utilizado para pagar directamente al proveedor no se registra también como aporte al fondo: eso duplicaría el crédito. Los aportes pueden exceder la deuda y dejar crédito a favor.

### 1.4 Confirmación de recepción

El ADMIN confirma que el fondo recibió un aporte. El destinatario confirma que recibió un reintegro. Mientras estén pendientes, ambos movimientos son visibles, pero no afectan los saldos confirmados ni la caja.

Los reintegros se realizan entre miembros distintos de la misma organización. No pueden superar la deuda confirmada del emisor ni el crédito confirmado del destinatario, descontando los reintegros pendientes que ya comprometen esos saldos.

La confirmación de pagos al proveedor todavía debe definirse; no debe confundirse con estas dos confirmaciones.

### 1.5 Saldos y fondo común

El saldo de cada participante se obtiene de sus partes asignadas y sus movimientos:

**Saldo individual = aportes confirmados + pagos personales confirmados + reintegros enviados confirmados − partes vigentes asignadas − reintegros recibidos confirmados.**

Un resultado positivo representa crédito; uno negativo, deuda.

**Dinero del fondo = aportes confirmados − pagos confirmados desde el fondo.**

Esta fórmula parte de un fondo inicial en cero y no incluye retiros, devoluciones ni ajustes, que no están contemplados en el alcance actual.

#### Ejemplo de uso del fondo

| Paso | Dinero del fondo | Saldo Ana | Saldo Luis | Saldo Marta |
|---|---:|---:|---:|---:|
| Ana aporta $50.000 y se confirma | $50.000 | $50.000 | $0 | $0 |
| Se registra un gasto de $30.000 con tres partes iguales | $50.000 | $40.000 | -$10.000 | -$10.000 |
| El fondo paga el gasto completo | $20.000 | $40.000 | -$10.000 | -$10.000 |
| Luis aporta $10.000 y se confirma | $30.000 | $40.000 | $0 | -$10.000 |

#### Ejemplo de pago personal

Sin aportes previos y con el fondo en cero, Ana paga personalmente un gasto de $30.000 repartido en tres partes de $10.000. Al confirmar el pago, Ana queda con $20.000 de crédito; Luis y Marta, con $10.000 de deuda cada uno. El fondo sigue en cero.

El saldo individual no representa necesariamente dinero que pueda retirarse del fondo. Tampoco indica por sí solo qué gasto particular quedó liquidado.

### 1.6 Acceso y participación

Todos los miembros activos pueden consultar los gastos y saldos individuales de su organización. Un USER puede registrar un gasto compartido con otros miembros, siempre que también participe en el reparto.

Los permisos se aplican por organización. Ser ADMIN de un grupo no habilita a operar sobre otro. La matriz completa de edición, anulación y administración se encuentra en los puntos por definir.

### 1.7 Invitaciones

Se incorporan mediante ORGANIZATION_INVITATION, separada de la membresía.

1. Un ADMIN activo genera una invitación por email para su organización activa.
2. El enlace vence a los siete días. En el MVP, el ADMIN lo copia y comparte.
3. La persona se registra o inicia sesión con una cuenta correspondiente al email invitado.
4. Al aceptar, se crea una membresía con rol USER y se marca la invitación como aceptada.
5. Un enlace solo se acepta una vez; si venció o fue revocado, no permite ingresar.
6. No se invita a un miembro activo ni se generan dos invitaciones pendientes y vigentes para el mismo email y organización.
7. Una membresía desactivada requiere reactivación explícita, no una nueva invitación.

Estados: PENDING, ACCEPTED, REVOKED y EXPIRED. El código secreto se almacena como hash.

### 1.8 Moneda, importes y fechas

| Tema | Regla del MVP |
|---|---|
| Moneda | Una por organización, inicialmente ARS. |
| Cambio de moneda | Solo mientras no haya gastos ni movimientos de dinero. |
| Importes | Positivos, con hasta dos decimales; rechazar entradas con más decimales sin redondearlas silenciosamente. |
| Máximo por registro | 9.999.999.999,99, según DECIMAL(12,2). |
| Reparto | Partes positivas cuya suma coincide con el gasto. |
| Pago del fondo | No puede comprometer más dinero del disponible, considerando otros pagos pendientes. |
| Pago personal | No requiere disponibilidad en el fondo. |
| Fecha de gasto | Obligatoria y no futura; puede ser anterior al alta de la organización. |
| Fecha de movimiento | Desde el alta de la organización y no futura. |
| Registro y confirmación | Fechas generadas por el servidor. |
| Día actual | Zona horaria de la organización; inicialmente America/Argentina/Buenos_Aires. |

El autor debe estar habilitado al registrar antecedentes, aunque el gasto sea anterior a su incorporación.

### 1.9 Límites del MVP

No se permite anular un gasto con pagos confirmados ni revertir movimientos confirmados. Estas funciones requieren tratar devoluciones y correcciones sin perder historial, y quedan para una etapa posterior.

El contrato detallado del predictor se definirá más adelante. Su objetivo es estimar el gasto total del próximo mes. Java gestiona permisos y datos del negocio; Python realiza el análisis y la predicción.

## 2. Definiciones pendientes para discutir

### 2.1 Reparto y liquidación

| Decisión | Pregunta para resolver |
|---|---|
| Modalidades | ¿El MVP permite partes iguales, importes manuales, porcentajes o pesos? |
| Centavos | ¿Cómo asignamos de forma consistente los centavos que sobran al dividir? |
| Liquidación por gasto | ¿Cómo se aplican los movimientos a cada parte para mostrar pendiente, parcial o pagada? |
| Reintegros | ¿Cómo vinculamos una transferencia con las obligaciones que cancela? |

No alcanza con un campo paid ni con el saldo agregado para responder si una parte específica ya está liquidada.

### 2.2 Permisos restantes

Matriz propuesta:

| Operación | ADMIN propuesto | USER propuesto |
|---|---|---|
| Modificar organización | Sí | No |
| Desactivar miembros y cambiar roles | Sí, sin dejar el grupo sin ADMIN | No |
| Gestionar categorías | Sí | No |
| Registrar un gasto | Para cualquier miembro | Individual propio o compartido en el que participa |
| Editar o anular un gasto permitido | Cualquiera de su organización | Los que creó |
| Acceder al dashboard predictivo | Sí | Sí |
| Registrar movimientos por otra persona | Sí, con autor identificado | No |
| Registrar pagos desde el fondo | Sí | No |

También debemos decidir si un ADMIN puede confirmar sus propios aportes y cuándo puede intervenir en un reintegro ajeno. 

### 2.3 Confirmaciones, correcciones y desactivaciones

- Definir quién confirma pagos personales y pagos desde el fondo, y qué comprobación se exige.
- Definir quién puede rechazar o cancelar un movimiento pendiente.
- Decidir qué datos de un gasto pueden editarse cuando tiene movimientos asociados. Se propone bloquear importe y reparto, permitiendo evaluar correcciones descriptivas.
- Antes de anular un gasto sin pagos confirmados, resolver sus movimientos pendientes para que no puedan confirmarse después.
- Cerrar el tratamiento de organización, usuario, membresía y categoría desactivados.

Propuesta para el MVP: conservar el historial, bloquear operaciones nuevas sobre elementos desactivados, impedir desactivar membresías con saldo pendiente y conservar siempre un ADMIN activo. Para organizaciones desactivadas, se propone mantener consulta histórica; para cuentas globales desactivadas, bloquear acceso. Las categorías desactivadas dejarían de estar disponibles para gastos nuevos, sin alterar los anteriores.

### 2.4 Definiciones técnicas y predictivas

| Tema | Qué falta cerrar | Momento |
|---|---|---|
| Comprobantes | Enlace externo o subida de archivos, almacenamiento y permisos. | Antes de implementar adjuntos. |
| Sesiones y email | Vencimiento/cierre de sesión, efecto de desactivación y comprobación del email invitado. | Antes de completar autenticación. |
| Predictor | Entradas, respuestas, historial mínimo, meses incompletos o sin información, algoritmo y evaluación. | Antes de integrar la predicción. |
| Comunicación | Framework Python, conexión con Java, errores y configuración. | Al definir infraestructura e integración. |
| Saldos | Calcular desde registros o almacenar resultados actualizados, con verificación de consistencia. | Antes de implementar consultas económicas. |


## 3. Para tener en cuenta en el desarrollo.

### 3.1 Integridad y operaciones simultáneas

Verificar en el servidor que cada categoría, participante, pagador y receptor pertenece a la organización correspondiente. Evitar membresías duplicadas con una restricción de usuario y organización. Las FK individuales no resuelven por sí solas el aislamiento entre grupos.

Crear gasto y reparto de forma consistente; aceptar invitación y crear membresía en una misma transacción. Validar también en el servidor, aunque la interfaz ya haya comprobado los campos.

Dos solicitudes simultáneas no deben utilizar el mismo dinero del fondo ni compensar dos veces una deuda. Los movimientos pendientes que comprometan dinero deben considerarse al validar nuevos registros. Al confirmar, revisar nuevamente los límites sin descontar dos veces la reserva del propio movimiento.

Una confirmación repetida no debe producir dos efectos económicos.

### 3.2 Historial, reportes y estados

Conservar autoría, fechas y estado de los registros. Los movimientos confirmados no se borran ni vuelven a pendiente. Si se implementan estados rechazado o cancelado antes de confirmar, no tendrán efecto económico.

El criterio de análisis a utilizar es contar gastos vigentes por fecha del gasto una sola vez. Pagos, aportes y reintegros no son nuevos consumos. Desactivar un miembro o categoría no debería eliminar gastos históricos de los reportes. Si se anula o corrige un gasto histórico, la siguiente predicción debe usar la información actualizada.

Para revisar consistencia, bajo las fórmulas y el alcance económico actual:

**Suma de saldos individuales = dinero del fondo − gastos vigentes todavía no pagados al proveedor.**

Esto supone fondo inicial cero, sin retiros ni devoluciones, y solo pagos confirmados. Los reintegros se compensan entre miembros. 

### 3.3 Posibles casos de prueba

- Acceso y modificaciones entre organizaciones distintas: deben bloquearse.
- Repartos: suma exacta, participantes únicos y USER incluido cuando crea un gasto compartido.
- Aportes y reintegros pendientes: no alteran saldos confirmados.
- Pago personal con fondo en cero: permitido, sin aporte previo y sin duplicar crédito.
- Pago desde el fondo sin dinero suficiente: rechazado.
- Pago mixto: registros separados cuya suma no supera el gasto.
- Confirmaciones repetidas o simultáneas: un único efecto por movimiento y sin exceder límites.
- Invitaciones vencidas, usadas o revocadas: no permiten incorporarse.
- Anulación de un gasto pagado: bloqueada en MVP.
- Importes cero, negativos, con más de dos decimales o fechas futuras: rechazados.
- Reportes: cada gasto se cuenta una sola vez; no se suma otra vez al pagarlo.


## 4. Ampliaciones posteriores al MVP (Seguro no llegamos pero podemos documentar la escalabilidad)

| Ampliación | Qué deberá contemplar |
|---|---|
| Revertir movimientos confirmados | Conservar el original, motivo, responsable y efecto sobre saldos. |
| Anular gastos con pagos confirmados | Registrar devoluciones explícitas; anular no significa que el proveedor devolvió el dinero. |
| Varias monedas | Conversión y reglas de tipos de cambio. |
| Gastos planificados | Fechas futuras separadas de gastos efectivamente realizados. |
| Unidades independientes | Áreas o departamentos con responsables y migración de las participaciones actuales. |
| Correo automático | Envío del enlace de invitación sin mezclar entrega con aceptación. |

