# Shareverance — Reglas de negocio (MVP)

Actualizado: Octubre 2026.

Este documento establece las reglas funcionales y de negocio para el alcance mínimo viable (MVP) de la aplicación, complementando el diagrama de entidad-relación del [README.md](README.md).

---

## 1. Alcance Funcional

El objetivo central del MVP es permitir que múltiples organizaciones registren y compartan gastos entre sus miembros, permitiendo calcular la participación de cada uno y realizar análisis y proyecciones mensuales de gastos.

### 1.1 Organizaciones y Usuarios
1. **Multi-tenant:** Una cuenta global de usuario (`USER`) puede pertenecer a una o más organizaciones (`ORGANIZATION`).
2. **Roles por organización:** Cada vínculo entre usuario y organización (`USER_ORGANIZATION`) define un rol exclusivo para ese ámbito:
   - **`ADMIN`:** Puede gestionar la configuración de la organización, categorías, miembros y cargar gastos tanto propios como en nombre de otros usuarios del grupo.
   - **`USER`:** Puede registrar sus propios gastos y participar en los gastos compartidos de la organización.
3. **Aislamiento:** La información de gastos, participantes y categorías de una organización es completamente invisible e inaccesible para miembros de otras organizaciones.

---

## 2. Registro y Reparto de Gastos

### 2.1 Atributos del Gasto (`EXPENSE`)
Todo gasto registrado debe contener:
- **Organización:** Organización a la cual pertenece.
- **Fecha (`expense_date`):** Cuándo ocurrió o a qué fecha corresponde el gasto (no puede ser una fecha futura).
- **Importe (`amount`):** Monto positivo en moneda local con hasta dos decimales (máximo 9.999.999.999,99).
- **Descripción:** Detalle o concepto del egreso.
- **Categoría:** Categoría asociada (perteneciente a la misma organización).
- **Autor (`created_by_user_id`):** Usuario que está cargando el registro en el sistema.
- **Miembro pagador / titular (`paid_by_membership_id`):** Miembro de la organización a nombre de quien se imputa el desembolso inicial.
  - Un usuario con rol `USER` solo puede cargar gastos a su propio nombre.
  - Un usuario con rol `ADMIN` puede cargar gastos a su nombre o **en nombre de cualquier otro usuario de su organización**.

### 2.2 Modalidades de Reparto (`EXPENSE_SPLIT`)
Al cargar el gasto se define con quién se reparte:
1. **Con nadie (Gasto individual):** El 100% del importe se asigna únicamente al miembro titular (`paid_by_membership_id`). Se genera un único registro en `EXPENSE_SPLIT`.
2. **Con otro usuario (Entre dos):** Se divide entre el titular y otro usuario específico de la organización.
3. **Con otros usuarios (Grupo específico):** Se divide entre una selección puntual de miembros de la organización.
4. **Con todos los usuarios:** Se divide en partes iguales entre la totalidad de los miembros activos de la organización en ese momento.

**Reglas de consistencia del reparto:**
- Cada miembro puede figurar a lo sumo una vez en la división de un mismo gasto (`UNIQUE(expense_id, user_organization_id)`).
- Cada parte asignada debe ser mayor a cero.
- La suma exacta de todas las partes (`EXPENSE_SPLIT.amount`) debe coincidir con el total del gasto (`EXPENSE.amount`).

---

## 3. Análisis y Proyecciones Mensuales

1. **Agrupación mensual:** Los gastos se totalizan mensualmente tomando como base la fecha del gasto (`expense_date`), categorizados para generar métricas organizacionales y por usuario.
2. **Servicio predictivo (Python):**
   - Consume el historial de gastos vigentes de la organización provisto por el backend (Spring Boot).
   - Genera estimaciones y proyecciones del gasto esperado para el mes siguiente.
   - Solo computa gastos activos (`is_active = true`).
   - Asiste en la carga de gastos a partir de la interpretación de documentos.

---

## 4. Validaciones e Integridad

- **Fechas:** La fecha del gasto no puede ser futura.
- **Pertenencia:** El usuario que registra, el miembro titular, los miembros asignados en el reparto y la categoría deben pertenecer a la misma organización.
- **Moneda:** Una moneda única por organización (inicialmente `ARS`).
- **Anulaciones:** Un gasto puede ser marcado como inactivo (`is_active = false`), excluyéndose de balances y proyecciones futuras.

---

## 5. Extensiones Post-MVP (Fuera de Alcance Inicial)

Las siguientes funciones se postergan para fases posteriores:
- Fondos comunes de tesorería y aportes de caja (`FUND_CONTRIBUTION`).
- Registro de transacciones bancarias o pagos directos parciales al proveedor (`EXPENSE_PAYMENT`).
- Reintegros o compensaciones monetarias directas entre miembros (`USER_REIMBURSEMENT`).
- Flujo complejo de invitaciones por email con tokens (`ORGANIZATION_INVITATION`).ceptación. |
