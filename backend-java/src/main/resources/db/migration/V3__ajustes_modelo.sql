
-- V3: Ajustes al modelo de datos de Shareverance

-- 1. Eliminar el campo timezone de la tabla organizacion
ALTER TABLE organizations
    DROP COLUMN timezone;

-- 2. Agregar el miembro asociado al desembolso inicial
ALTER TABLE expenses
    ADD COLUMN paid_by_membership_id BIGINT NOT NULL;

-- 3. Garantizar que el miembro pertenezca a la misma organización
ALTER TABLE expenses
    ADD CONSTRAINT fk_expense_paid_by_membership
    FOREIGN KEY (organization_id, paid_by_membership_id)
    REFERENCES user_organization (organization_id, id);
