-- V8 publicada: ampliar la integridad sin modificar su checksum ni descartar datos.
-- Si existen cruces previos, la validación falla y PostgreSQL revierte la migración.
ALTER TABLE receipts
    ADD CONSTRAINT fk_receipt_expense_organization
    FOREIGN KEY (organization_id, expense_id)
    REFERENCES expenses (organization_id, id);
