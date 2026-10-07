-- Tabla técnica de demostración; no pertenece al modelo de negocio definitivo.
CREATE TABLE infra_demo (
    id UUID PRIMARY KEY,
    label VARCHAR(100) NOT NULL UNIQUE
);
