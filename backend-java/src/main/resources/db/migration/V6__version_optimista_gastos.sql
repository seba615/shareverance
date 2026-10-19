-- V6: Version optimista en gastos para control de concurrencia

ALTER TABLE expenses
    ADD COLUMN version INT NOT NULL DEFAULT 1;

