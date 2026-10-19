-- Ejecutado por psql con el propietario administrativo, también sobre bases existentes.
\set ON_ERROR_STOP on
\getenv java_password JAVA_DB_PASSWORD
\getenv python_password PYTHON_DB_PASSWORD
SELECT length(:'java_password') > 0 AND length(:'python_password') > 0
       AND :'java_password' <> :'python_password' AS passwords_valid \gset
\if :passwords_valid
\else
  DO $$ BEGIN RAISE EXCEPTION 'Se requieren contraseñas SQL no vacías y distintas para Java y Python'; END $$;
\endif
BEGIN;
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='shareverance_java') THEN
        CREATE ROLE shareverance_java NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='shareverance_python') THEN
        CREATE ROLE shareverance_python NOLOGIN NOINHERIT;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname IN ('shareverance_java','shareverance_python')
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit))
       OR EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member
                  WHERE r.rolname IN ('shareverance_java','shareverance_python')) THEN
        RAISE EXCEPTION 'Roles preexistentes incompatibles; revisar permisos antes de continuar';
    END IF;
END $$;
-- %L evita interpretar comillas u otros caracteres de una contraseña como SQL.
SELECT format('ALTER ROLE shareverance_java LOGIN PASSWORD %L', :'java_password') \gexec
SELECT format('ALTER ROLE shareverance_python LOGIN PASSWORD %L', :'python_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO shareverance_java, shareverance_python', current_database()) \gexec
COMMIT;
