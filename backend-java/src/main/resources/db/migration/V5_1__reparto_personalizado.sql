-- V6 está reservada por feat/web-app para categorías iniciales.
-- No reclasificar registros históricos: CUSTOM antes se guardaba como EQUAL.
ALTER TABLE expenses DROP CONSTRAINT ck_expense_split_type;
ALTER TABLE expenses ADD CONSTRAINT ck_expense_split_type
    CHECK (split_type IN ('EQUAL', 'INDIVIDUAL', 'CUSTOM'));
