-- Solo se incluye con el perfil dev. No altera registros ya existentes.
INSERT INTO infra_demo (id, label) VALUES
('00000000-0000-0000-0000-000000000001', 'Organización de demostración'),
('00000000-0000-0000-0000-000000000002', 'Escenario de comunicación')
ON CONFLICT DO NOTHING;
