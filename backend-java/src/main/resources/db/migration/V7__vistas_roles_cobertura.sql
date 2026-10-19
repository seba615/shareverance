-- El migrador necesita CREATEROLE en la primera instalación. Sin contraseñas en Flyway.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'shareverance_java') THEN
        CREATE ROLE shareverance_java NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'shareverance_python') THEN
        CREATE ROLE shareverance_python NOLOGIN NOINHERIT;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname IN ('shareverance_java','shareverance_python')
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit))
       OR EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member
                  WHERE r.rolname IN ('shareverance_java','shareverance_python')) THEN
        RAISE EXCEPTION 'Los roles de ejecución requieren privilegios mínimos y ninguna membresía heredada';
    END IF;
END $$;

CREATE TABLE analytics_month_coverage (
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    covered_month DATE NOT NULL,
    declared_by_user_id BIGINT NOT NULL,
    declared_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (organization_id, covered_month),
    FOREIGN KEY (organization_id, declared_by_user_id)
        REFERENCES user_organization(organization_id, user_id),
    CHECK (EXTRACT(DAY FROM covered_month) = 1),
    CHECK (covered_month < DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::date)
);

CREATE SCHEMA IF NOT EXISTS analytics;
REVOKE ALL ON SCHEMA analytics FROM PUBLIC;

CREATE OR REPLACE FUNCTION analytics.current_organization_id() RETURNS BIGINT
LANGUAGE plpgsql STABLE SET search_path = pg_catalog AS $$
DECLARE organization_id BIGINT;
BEGIN
    organization_id := NULLIF(current_setting('shareverance.organization_id', TRUE), '')::BIGINT;
    IF organization_id > 0 THEN RETURN organization_id; END IF;
    RETURN NULL;
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION analytics.current_organization_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION analytics.current_organization_id() TO shareverance_python;

-- Calificar tablas con el esquema de Flyway también permite pruebas en esquemas aislados.
DO $$
DECLARE business_schema TEXT := current_schema();
BEGIN
    EXECUTE format($view$
        CREATE OR REPLACE VIEW analytics.v_monthly_expenses WITH (security_barrier = true) AS
        SELECT e.organization_id, date_trunc('month', e.expense_date)::date AS month,
               e.category_id, SUM(e.amount * 100)::bigint AS amount_minor
        FROM %1$I.expenses e JOIN %1$I.organizations o ON o.id=e.organization_id
        WHERE e.organization_id=analytics.current_organization_id() AND e.is_active
          AND o.is_active AND o.currency='ARS'
        GROUP BY e.organization_id, date_trunc('month',e.expense_date)::date, e.category_id
    $view$, business_schema);
    EXECUTE format($view$
        CREATE OR REPLACE VIEW analytics.v_monthly_participations WITH (security_barrier = true) AS
        SELECT e.organization_id, date_trunc('month',e.expense_date)::date AS month,
               e.category_id, s.user_organization_id AS membership_id,
               SUM(s.amount * 100)::bigint AS amount_minor
        FROM %1$I.expenses e JOIN %1$I.expense_splits s
          ON s.organization_id=e.organization_id AND s.expense_id=e.id
        JOIN %1$I.organizations o ON o.id=e.organization_id
        WHERE e.organization_id=analytics.current_organization_id() AND e.is_active
          AND o.is_active AND o.currency='ARS'
        GROUP BY e.organization_id,date_trunc('month',e.expense_date)::date,e.category_id,s.user_organization_id
    $view$, business_schema);
    EXECUTE format($view$
        CREATE OR REPLACE VIEW analytics.v_monthly_coverage WITH (security_barrier = true) AS
        SELECT c.organization_id, c.covered_month AS month
        FROM %1$I.analytics_month_coverage c JOIN %1$I.organizations o ON o.id=c.organization_id
        WHERE c.organization_id=analytics.current_organization_id() AND c.is_confirmed
          AND o.is_active AND o.currency='ARS'
    $view$, business_schema);

    EXECUTE format('REVOKE CONNECT, TEMPORARY ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM shareverance_java, shareverance_python', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO shareverance_java, shareverance_python', current_database());
    EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM PUBLIC', business_schema);
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM shareverance_python', business_schema);
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM PUBLIC, shareverance_python', business_schema);
    EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM PUBLIC, shareverance_python', business_schema);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO shareverance_java', business_schema);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %1$I.users, %1$I.organizations,
        %1$I.user_organization, %1$I.categories, %1$I.expenses, %1$I.expense_splits,
        %1$I.auth_sessions, %1$I.infra_demo, %1$I.analytics_month_coverage TO shareverance_java', business_schema);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO shareverance_java', business_schema);
END $$;
REVOKE ALL ON ALL TABLES IN SCHEMA analytics FROM PUBLIC, shareverance_java, shareverance_python;
GRANT USAGE ON SCHEMA analytics TO shareverance_python;
GRANT SELECT ON analytics.v_monthly_expenses, analytics.v_monthly_participations,
    analytics.v_monthly_coverage TO shareverance_python;
