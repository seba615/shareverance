INSERT INTO users (full_name, email, password_hash)
VALUES (
    'Admin Shareverance',
    'admin@shareverance.com',
    '$2a$10$xPEyIRY8G7tNH9viFwPnuuwLmdzdAX/5xpDhQGqvmHplpMA4XD2.m'
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO organizations (name, type, currency)
SELECT 'Organizacion Demo', 'HOUSEHOLD', 'ARS'
WHERE NOT EXISTS (
    SELECT 1
    FROM organizations
    WHERE name = 'Organizacion Demo' AND type = 'HOUSEHOLD'
);

INSERT INTO user_organization (user_id, organization_id, role)
SELECT users.id, organizations.id, 'ADMIN'
FROM users
CROSS JOIN organizations
WHERE users.email = 'admin@shareverance.com'
  AND organizations.name = 'Organizacion Demo'
  AND organizations.type = 'HOUSEHOLD'
ON CONFLICT (organization_id, user_id) DO NOTHING;
