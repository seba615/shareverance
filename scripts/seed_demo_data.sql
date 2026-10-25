-- Seed de datos de prueba para Organizacion Demo (Shareverance)
-- Inserta miembros adicionales, categorías y gastos con divisiones para poblar Historial y Calendario.

BEGIN;

-- 1. Usuarios adicionales de prueba (contraseña: password123)
INSERT INTO users (id, full_name, email, password_hash, is_active)
VALUES
    (2, 'Juan Pérez', 'juan.perez@shareverance.com', '$2a$10$xPEyIRY8G7tNH9viFwPnuuwLmdzdAX/5xpDhQGqvmHplpMA4XD2.m', TRUE),
    (3, 'María Gómez', 'maria.gomez@shareverance.com', '$2a$10$xPEyIRY8G7tNH9viFwPnuuwLmdzdAX/5xpDhQGqvmHplpMA4XD2.m', TRUE),
    (4, 'Carlos López', 'carlos.lopez@shareverance.com', '$2a$10$xPEyIRY8G7tNH9viFwPnuuwLmdzdAX/5xpDhQGqvmHplpMA4XD2.m', TRUE)
ON CONFLICT (email) DO NOTHING;

-- Asegurar secuencia de users
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 1));

-- 2. Membresías en Organizacion Demo (id: 1)
INSERT INTO user_organization (id, user_id, organization_id, role, is_active)
VALUES
    (2, 2, 1, 'USER', TRUE),
    (3, 3, 1, 'USER', TRUE),
    (4, 4, 1, 'USER', TRUE)
ON CONFLICT (organization_id, user_id) DO NOTHING;

-- Asegurar secuencia de user_organization
SELECT setval('user_organization_id_seq', GREATEST((SELECT MAX(id) FROM user_organization), 1));

-- 3. Categorías de gastos para Organizacion Demo
INSERT INTO categories (id, organization_id, name, description, color, is_active)
VALUES
    (1, 1, 'Supermercado', 'Compras de alimentos y limpieza', '#10B981', TRUE),
    (2, 1, 'Servicios e Impuestos', 'Luz, gas, internet y agua', '#3B82F6', TRUE),
    (3, 1, 'Alquiler y Expensas', 'Vivienda y gastos comunes', '#8B5CF6', TRUE),
    (4, 1, 'Mantenimiento', 'Reparaciones y arreglos del hogar', '#F59E0B', TRUE),
    (5, 1, 'Comida y Salidas', 'Restaurantes, delivery y cafetería', '#EF4444', TRUE),
    (6, 1, 'Transporte', 'Combustible, peajes y pasajes', '#06B6D4', TRUE)
ON CONFLICT (organization_id, name) DO NOTHING;

-- Asegurar secuencia de categories
SELECT setval('categories_id_seq', GREATEST((SELECT MAX(id) FROM categories), 1));

-- 4. Gastos con repartos en Organizacion Demo
-- Gasto 1: Supermercado mensual (Reparto igual entre los 4 miembros)
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (1, 1, 1, 1, 1, 120000.00, '2026-10-05', 'Compra mensual mayorista Coto', 'EQUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 1, 1, 30000.00),
    (1, 1, 2, 30000.00),
    (1, 1, 3, 30000.00),
    (1, 1, 4, 30000.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Gasto 2: Factura de Internet y Cable (Reparto igual entre los 4 miembros)
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (2, 1, 1, 2, 2, 45000.00, '2026-10-08', 'Internet Fibertel 500MB', 'EQUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 2, 1, 11250.00),
    (1, 2, 2, 11250.00),
    (1, 2, 3, 11250.00),
    (1, 2, 4, 11250.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Gasto 3: Expensas edificio (Reparto igual)
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (3, 1, 1, 1, 3, 85000.00, '2026-10-10', 'Expensas Octubre departamento', 'EQUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 3, 1, 21250.00),
    (1, 3, 2, 21250.00),
    (1, 3, 3, 21250.00),
    (1, 3, 4, 21250.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Gasto 4: Delivery cena pizza (Gasto individual pagado por María)
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (4, 1, 1, 3, 5, 28000.00, '2026-10-12', 'Pizzas y empanadas fin de semana', 'INDIVIDUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 4, 3, 28000.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Gasto 5: Reparación cerradura puerta principal (Reparto igual)
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (5, 1, 1, 4, 4, 32000.00, '2026-10-15', 'Cambio cerrajero tambor entrada', 'EQUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 5, 1, 8000.00),
    (1, 5, 2, 8000.00),
    (1, 5, 3, 8000.00),
    (1, 5, 4, 8000.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Gasto 6: Carga de nafta vehículo compartido
INSERT INTO expenses (id, organization_id, created_by_user_id, paid_by_membership_id, category_id, amount, expense_date, description, split_type, version, is_active)
VALUES (6, 1, 1, 2, 6, 50000.00, '2026-10-18', 'Tanque lleno YPF Infinia', 'EQUAL', 0, TRUE)
ON CONFLICT (organization_id, id) DO NOTHING;

INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
VALUES
    (1, 6, 1, 12500.00),
    (1, 6, 2, 12500.00),
    (1, 6, 3, 12500.00),
    (1, 6, 4, 12500.00)
ON CONFLICT (expense_id, user_organization_id) DO NOTHING;

-- Asegurar secuencia de expenses y expense_splits
SELECT setval('expenses_id_seq', GREATEST((SELECT MAX(id) FROM expenses), 1));
SELECT setval('expense_splits_id_seq', GREATEST((SELECT MAX(id) FROM expense_splits), 1));

COMMIT;
