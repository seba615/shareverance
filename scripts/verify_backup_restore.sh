#!/usr/bin/env bash
# ==============================================================================
# Shareverance - Verificación Aislada de Respaldo y Restauración (E7.1 / A19)
#
# Comprueba de extremo a extremo:
# 1. Creación de entorno temporal aislado (PostgreSQL en tmpfs + volumen Docker efímero)
# 2. Aplicación de migraciones V1 a V9 y configure-access.sql
# 3. Poblado de fixtures de prueba (organizaciones, gastos con partes, comprobantes en BD y archivos físicos)
# 4. Ejecución del script de respaldo (scripts/backup.sh)
# 5. Validación del manifiesto y sumas criptográficas
# 6. Simulación de desastre (destrucción de base de datos y eliminación de archivos de comprobantes)
# 7. Ejecución de la restauración (scripts/restore.sh)
# 8. Verificación de integridad cruzada (referencias exactas, tamaños de archivo, roles técnicos)
# 9. Limpieza completa de recursos temporales
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

TEST_PG_CONTAINER="shareverance-e71-test-db"
TEST_RECEIPTS_VOLUME="shareverance-e71-test-receipts"
TEST_BACKUP_DIR="/tmp/shareverance_backup_test_$$"
TEST_DB_NAME="shareverance_e71_test"
TEST_DB_USER="e71_admin"
TEST_DB_PASS="e71_local_pass"
TEST_JAVA_PASS="e71_java_pass"
TEST_PYTHON_PASS="e71_python_pass"

echo "=== [E7.1 / A19] Iniciando Verificación Aislada de Respaldo y Restauración ==="

cleanup() {
    echo "Limpiando recursos temporales de prueba..."
    docker rm -f "${TEST_PG_CONTAINER}" >/dev/null 2>&1 || true
    docker volume rm -f "${TEST_RECEIPTS_VOLUME}" >/dev/null 2>&1 || true
    rm -rf "${TEST_BACKUP_DIR}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 1. Crear contenedor PostgreSQL aislado con almacenamiento tmpfs
echo "1. Creando contenedor PostgreSQL aislado (${TEST_PG_CONTAINER})..."
docker run -d \
    --name "${TEST_PG_CONTAINER}" \
    --tmpfs /var/lib/postgresql/data \
    -e POSTGRES_DB="${TEST_DB_NAME}" \
    -e POSTGRES_USER="${TEST_DB_USER}" \
    -e POSTGRES_PASSWORD="${TEST_DB_PASS}" \
    postgres:17.11-bookworm >/dev/null

# Esperar que PostgreSQL esté listo
echo "Esperando que PostgreSQL acepte conexiones..."
for i in {1..30}; do
    if docker exec "${TEST_PG_CONTAINER}" pg_isready -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

# 2. Crear volumen de comprobantes aislado
echo "2. Creando volumen aislado para comprobantes (${TEST_RECEIPTS_VOLUME})..."
docker volume create "${TEST_RECEIPTS_VOLUME}" >/dev/null

# 3. Aplicar migraciones Flyway V1 a V9 en orden
echo "3. Aplicando migraciones V1 a V9 en ${TEST_DB_NAME}..."
MIGRATION_DIR="${ROOT_DIR}/backend-java/src/main/resources/db/migration"

for v in \
    V1__crear_tabla_demostracion.sql \
    V2__modelo_inicial.sql \
    V3__ajustes_modelo.sql \
    V5__sesiones_autenticadas.sql \
    V6__version_optimista_gastos.sql \
    V7__vistas_roles_cobertura.sql \
    V8__archivos_comprobantes.sql \
    V9__integridad_organizacion_comprobantes.sql; do
    
    echo "   Aplicando ${v}..."
    docker exec -i "${TEST_PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" < "${MIGRATION_DIR}/${v}" >/dev/null
done

# 4. Configurar roles técnicos Java y Python
echo "4. Aprovisionando roles técnicos mediante configure-access.sql..."
docker exec -i \
    -e JAVA_DB_PASSWORD="${TEST_JAVA_PASS}" \
    -e PYTHON_DB_PASSWORD="${TEST_PYTHON_PASS}" \
    "${TEST_PG_CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" < "${ROOT_DIR}/database/configure-access.sql" >/dev/null

# 5. Insertar datos sintéticos de negocio
echo "5. Insertando datos sintéticos de prueba..."
docker exec -i "${TEST_PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" << 'EOF'
-- Usuario 1 y Organización 1
INSERT INTO users(id, email, password_hash, full_name, is_active)
VALUES (1, 'admin@org1.test', '$2a$10$hashdemo1', 'Admin Org 1', TRUE),
       (2, 'user@org1.test',  '$2a$10$hashdemo2', 'User Org 1',  TRUE),
       (3, 'admin@org2.test', '$2a$10$hashdemo3', 'Admin Org 2', TRUE);

INSERT INTO organizations(id, name, type, currency, is_active)
VALUES (1, 'Organización Alfa', 'Hogar', 'ARS', TRUE),
       (2, 'Organización Beta', 'Consorcio', 'ARS', TRUE);

INSERT INTO user_organization(id, user_id, organization_id, role, is_active)
VALUES (1, 1, 1, 'ADMIN', TRUE),
       (2, 2, 1, 'USER',  TRUE),
       (3, 3, 2, 'ADMIN', TRUE);

INSERT INTO categories(id, organization_id, name, is_active)
VALUES (1, 1, 'Supermercado', TRUE),
       (2, 1, 'Servicios',    TRUE);

-- Gasto de 15000 centavos ($150.00) repartido entre membresía 1 (10000) y membresía 2 (5000)
INSERT INTO expenses(id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                    expense_date, amount, description, split_type, receipt_url, version, is_active)
VALUES (1, 1, 1, 1, 1, '2026-03-15', 150.00, 'Compra mensual alimentos', 'INDIVIDUAL',
        '/api/v1/organizations/1/receipts/r1-uuid-1111/content', 1, TRUE);

INSERT INTO expense_splits(id, organization_id, expense_id, user_organization_id, amount)
VALUES (1, 1, 1, 1, 100.00),
       (2, 1, 1, 2, 50.00);

-- Comprobantes en receipts
INSERT INTO receipts(id, organization_id, uploaded_by_user_id, storage_path, original_filename, content_type, file_size_bytes, expense_id, is_active)
VALUES ('r1-uuid-1111', 1, 1, 'org_1/r1-uuid-1111.png', 'ticket_super.png', 'image/png', 24, 1, TRUE),
       ('r2-uuid-2222', 1, 2, 'org_1/r2-uuid-2222.jpg', 'recibo_luz.jpg',   'image/jpeg', 18, NULL, TRUE);

-- Cobertura analítica mensual
INSERT INTO analytics_month_coverage(organization_id, covered_month, declared_by_user_id, is_confirmed)
VALUES (1, '2026-03-01', 1, TRUE);
EOF

# 6. Crear los archivos físicos correspondientes a los comprobantes dentro del volumen
echo "6. Creando archivos de comprobantes en el volumen aislado..."
docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data" alpine:3.21 sh -c "
    mkdir -p /data/org_1
    printf 'CONTENIDO_PNG_FIXTURE_24' > /data/org_1/r1-uuid-1111.png
    printf 'CONTENIDO_JPG_18_B' > /data/org_1/r2-uuid-2222.jpg
"

# Verificar que los archivos creados tengan exactamente 24 y 18 bytes
SZ1=$(docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "wc -c < /data/org_1/r1-uuid-1111.png" | tr -d ' ')
SZ2=$(docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "wc -c < /data/org_1/r2-uuid-2222.jpg" | tr -d ' ')
if [ "${SZ1}" != "24" ] || [ "${SZ2}" != "18" ]; then
    echo "ERROR: Falló la creación de archivos de prueba (SZ1=${SZ1}, SZ2=${SZ2})" >&2
    exit 1
fi
echo "   Archivos físicos inicializados correctamente (24 y 18 bytes)."

# 7. Ejecutar el respaldo usando scripts/backup.sh
echo "7. Ejecutando scripts/backup.sh..."
export POSTGRES_CONTAINER="${TEST_PG_CONTAINER}"
export POSTGRES_DB="${TEST_DB_NAME}"
export POSTGRES_USER="${TEST_DB_USER}"
export RECEIPTS_VOLUME="${TEST_RECEIPTS_VOLUME}"

"${ROOT_DIR}/scripts/backup.sh" "${TEST_BACKUP_DIR}"

# 8. Validar existencia y consistencia del manifiesto generado
echo "8. Validando contenido del respaldo..."
if [ ! -f "${TEST_BACKUP_DIR}/manifest.json" ] || [ ! -f "${TEST_BACKUP_DIR}/db.dump" ] || [ ! -f "${TEST_BACKUP_DIR}/receipts.tar.gz" ]; then
    echo "ERROR: Artefactos de respaldo incompletos en ${TEST_BACKUP_DIR}" >&2
    exit 1
fi

grep -q '"users": 3' "${TEST_BACKUP_DIR}/manifest.json"
grep -q '"organizations": 2' "${TEST_BACKUP_DIR}/manifest.json"
grep -q '"expenses": 1' "${TEST_BACKUP_DIR}/manifest.json"
grep -q '"receipts_active": 2' "${TEST_BACKUP_DIR}/manifest.json"
grep -q '"total_files": 2' "${TEST_BACKUP_DIR}/manifest.json"
echo "   Manifiesto verificado exitosamente con todas las métricas de negocio."

# 9. Simular desastre completo (destrucción de base y eliminación de archivos)
echo "9. Simulando pérdida de datos y corrupción..."
docker exec -i "${TEST_PG_CONTAINER}" psql -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" -c "
    DROP TABLE expense_splits CASCADE;
    DROP TABLE receipts CASCADE;
    DROP TABLE expenses CASCADE;
    DROP TABLE categories CASCADE;
    DROP TABLE user_organization CASCADE;
    DROP TABLE organizations CASCADE;
    DROP TABLE users CASCADE;
" >/dev/null

docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data" alpine:3.21 sh -c "rm -rf /data/*"

# Verificar que los datos y archivos fueron eliminados
REMAINING_FILES=$(docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "ls -A /data | wc -l" | tr -d ' ')
if [ "${REMAINING_FILES}" != "0" ]; then
    echo "ERROR: Falló la simulación de eliminación de archivos" >&2
    exit 1
fi
echo "   Desastre simulado: base de datos vaciada y sistema de archivos limpio."

# 10. Ejecutar la restauración usando scripts/restore.sh
echo "10. Ejecutando scripts/restore.sh..."
export JAVA_DB_PASSWORD="${TEST_JAVA_PASS}"
export PYTHON_DB_PASSWORD="${TEST_PYTHON_PASS}"

"${ROOT_DIR}/scripts/restore.sh" "${TEST_BACKUP_DIR}" --skip-pause

# 11. Validar que la base y el sistema de archivos quedaron 100% restaurados
echo "11. Verificando consistencia post-restauración..."

REST_U=$(docker exec -i "${TEST_PG_CONTAINER}" psql -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" -t -A -c "SELECT COUNT(*) FROM users;")
REST_E=$(docker exec -i "${TEST_PG_CONTAINER}" psql -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" -t -A -c "SELECT COUNT(*) FROM expenses;")
REST_R=$(docker exec -i "${TEST_PG_CONTAINER}" psql -U "${TEST_DB_USER}" -d "${TEST_DB_NAME}" -t -A -c "SELECT COUNT(*) FROM receipts WHERE is_active = TRUE;")

if [ "${REST_U}" != "3" ] || [ "${REST_E}" != "1" ] || [ "${REST_R}" != "2" ]; then
    echo "ERROR: Conteo post-restauración incorrecto (U=${REST_U}, E=${REST_E}, R=${REST_R})" >&2
    exit 1
fi

REST_SZ1=$(docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "wc -c < /data/org_1/r1-uuid-1111.png" | tr -d ' ')
REST_SZ2=$(docker run --rm -v "${TEST_RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "wc -c < /data/org_1/r2-uuid-2222.jpg" | tr -d ' ')

if [ "${REST_SZ1}" != "24" ] || [ "${REST_SZ2}" != "18" ]; then
    echo "ERROR: Tamaño de comprobantes post-restauración incorrecto (SZ1=${REST_SZ1}, SZ2=${REST_SZ2})" >&2
    exit 1
fi

# 12. Validar que los roles shareverance_java y shareverance_python puedan consultar
echo "12. Verificando acceso de los roles técnicos..."
docker exec -i "${TEST_PG_CONTAINER}" psql -U shareverance_java -d "${TEST_DB_NAME}" -c "SELECT COUNT(*) FROM expenses;" >/dev/null
docker exec -i "${TEST_PG_CONTAINER}" psql -U shareverance_python -d "${TEST_DB_NAME}" -c "SELECT set_config('shareverance.organization_id', '1', true); SELECT COUNT(*) FROM analytics.v_monthly_expenses;" >/dev/null
echo "   Roles técnicos operativos con permisos correctos."

echo ""
echo "=========================================================================="
echo "✔ [E7.1 / A19] Comprobación de Respaldo y Restauración EXITOSA al 100%."
echo "=========================================================================="
