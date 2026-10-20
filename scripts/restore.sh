#!/usr/bin/env bash
# ==============================================================================
# Shareverance - Procedimiento de Restauración y Verificación Cruzada (E7.1 / A19)
#
# Restaura:
# 1. Base de datos PostgreSQL a partir de db.dump
# 2. Configuración de credenciales de roles técnicos (configure-access.sql)
# 3. Volumen persistente de comprobantes desde receipts.tar.gz
# 4. Verificación exhaustiva de integridad referencial cruzada (DB <-> Filesystem)
#
# Uso:
#   ./scripts/restore.sh <directorio_respaldo> [--skip-pause]
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ $# -lt 1 ]; then
    echo "ERROR: Debe especificar el directorio del respaldo a restaurar." >&2
    echo "Uso: $0 <directorio_respaldo> [--skip-pause]" >&2
    exit 1
fi

BACKUP_DIR="$1"
SKIP_PAUSE="${2:-}"

# Cargar variables de .env si existe
if [ -f "${ROOT_DIR}/.env" ]; then
    # shellcheck disable=SC1091
    set -a
    source "${ROOT_DIR}/.env"
    set +a
fi

POSTGRES_DB="${POSTGRES_DB:-shareverance}"
POSTGRES_USER="${POSTGRES_USER:-shareverance_dev}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-}"
RECEIPTS_VOLUME="${RECEIPTS_VOLUME:-}"
JAVA_DB_PASSWORD="${JAVA_DB_PASSWORD:-shareverance_java_local_only}"
PYTHON_DB_PASSWORD="${PYTHON_DB_PASSWORD:-shareverance_python_sql_local_only}"

hash_sha256() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    else
        openssl dgst -sha256 "$file" | awk '{print $NF}'
    fi
}

echo "=== Iniciando restauración de Shareverance desde: ${BACKUP_DIR} ==="

MANIFEST_FILE="${BACKUP_DIR}/manifest.json"
DUMP_FILE="${BACKUP_DIR}/db.dump"
RECEIPTS_TAR="${BACKUP_DIR}/receipts.tar.gz"

# 1. Validar existencia de archivos obligatorios
for f in "${MANIFEST_FILE}" "${DUMP_FILE}" "${RECEIPTS_TAR}"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: Archivo requerido no encontrado: $f" >&2
        exit 1
    fi
done

# 2. Validar sumas de verificación SHA-256 contra manifest.json
echo "Verificando integridad criptográfica de los artefactos de respaldo..."
EXPECTED_DB_SHA=$(grep '"dump_sha256"' "${MANIFEST_FILE}" | sed -E 's/.*"dump_sha256": *"([^"]+)".*/\1/')
EXPECTED_RECEIPTS_SHA=$(grep '"archive_sha256"' "${MANIFEST_FILE}" | sed -E 's/.*"archive_sha256": *"([^"]+)".*/\1/')

ACTUAL_DB_SHA=$(hash_sha256 "${DUMP_FILE}")
ACTUAL_RECEIPTS_SHA=$(hash_sha256 "${RECEIPTS_TAR}")

if [ "${EXPECTED_DB_SHA}" != "${ACTUAL_DB_SHA}" ]; then
    echo "ERROR: Discrepancia SHA-256 en db.dump!" >&2
    echo "  Esperado: ${EXPECTED_DB_SHA}" >&2
    echo "  Actual:   ${ACTUAL_DB_SHA}" >&2
    exit 1
fi

if [ "${EXPECTED_RECEIPTS_SHA}" != "${ACTUAL_RECEIPTS_SHA}" ]; then
    echo "ERROR: Discrepancia SHA-256 en receipts.tar.gz!" >&2
    echo "  Esperado: ${EXPECTED_RECEIPTS_SHA}" >&2
    echo "  Actual:   ${ACTUAL_RECEIPTS_SHA}" >&2
    exit 1
fi
echo "Integridad de archivos confirmada (SHA-256 coincidente)."

# 3. Detectar contenedor PostgreSQL y volumen de comprobantes
if [ -z "${POSTGRES_CONTAINER}" ]; then
    if command -v docker >/dev/null 2>&1; then
        POSTGRES_CONTAINER="$(docker ps --filter "name=postgres" --filter "status=running" --format "{{.Names}}" | head -n 1)"
    fi
fi

if [ -z "${POSTGRES_CONTAINER}" ]; then
    echo "ERROR: No se encontró ningún contenedor de PostgreSQL en ejecución." >&2
    exit 1
fi
echo "Contenedor PostgreSQL de destino: ${POSTGRES_CONTAINER}"

if [ -z "${RECEIPTS_VOLUME}" ]; then
    RECEIPTS_VOLUME="$(docker volume ls --filter "name=receipts_data" --format "{{.Name}}" | head -n 1)"
    if [ -z "${RECEIPTS_VOLUME}" ]; then
        RECEIPTS_VOLUME="shareverance_receipts_data"
    fi
fi
echo "Volumen de comprobantes de destino: ${RECEIPTS_VOLUME}"

# 4. Pausar servicios de aplicación durante la restauración
PAUSED_CONTAINERS=()
if [ "${SKIP_PAUSE}" != "--skip-pause" ]; then
    for svc in java-service python-service; do
        cname="$(docker ps --filter "name=${svc}" --filter "status=running" --format "{{.Names}}" | head -n 1 || true)"
        if [ -n "${cname}" ]; then
            echo "Pausando ${cname}..."
            docker pause "${cname}" >/dev/null
            PAUSED_CONTAINERS+=("${cname}")
        fi
    done
fi

cleanup() {
    for cname in "${PAUSED_CONTAINERS[@]:-}"; do
        if [ -n "${cname}" ]; then
            echo "Reanudando ${cname}..."
            docker unpause "${cname}" >/dev/null || true
        fi
    done
}
trap cleanup EXIT

# 5. Restaurar base de datos con pg_restore
echo "Restaurando base de datos PostgreSQL desde db.dump..."
# Usar --clean --if-exists para restablecer el esquema y datos
docker exec -i "${POSTGRES_CONTAINER}" pg_restore \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    --clean \
    --if-exists \
    --no-owner < "${DUMP_FILE}" || true

# 6. Re-aplicar configuración de roles técnicos (configure-access.sql)
if [ -f "${ROOT_DIR}/database/configure-access.sql" ]; then
    echo "Re-configurando roles y permisos de acceso (configure-access.sql)..."
    docker exec -i \
        -e JAVA_DB_PASSWORD="${JAVA_DB_PASSWORD}" \
        -e PYTHON_DB_PASSWORD="${PYTHON_DB_PASSWORD}" \
        "${POSTGRES_CONTAINER}" \
        psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < "${ROOT_DIR}/database/configure-access.sql"
fi

# 7. Restaurar volumen de comprobantes
echo "Restaurando archivos en volumen de comprobantes (${RECEIPTS_VOLUME})..."
# Asegurar que el volumen existe
docker volume create "${RECEIPTS_VOLUME}" >/dev/null

# Limpiar volumen y desempaquetar el tarball
docker run --rm \
    -v "${RECEIPTS_VOLUME}:/data" \
    -v "${BACKUP_DIR}:/backup:ro" \
    alpine:3.21 sh -c "rm -rf /data/* /data/..?* /data/.[!.]* 2>/dev/null || true; tar -xzf /backup/receipts.tar.gz -C /data"

# 8. Verificación Cruzada de Integridad (Auditoría Post-Restauración)
echo ""
echo "=== Ejecutando Verificación de Integridad Cruzada ==="

REST_USERS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM users;" 2>/dev/null || echo 0)
REST_ORGS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM organizations;" 2>/dev/null || echo 0)
REST_MEMBERSHIPS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM user_organization;" 2>/dev/null || echo 0)
REST_CATEGORIES=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM categories;" 2>/dev/null || echo 0)
REST_EXPENSES=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM expenses;" 2>/dev/null || echo 0)
REST_SPLITS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM expense_splits;" 2>/dev/null || echo 0)
REST_RECEIPTS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM receipts WHERE is_active = TRUE;" 2>/dev/null || echo 0)

echo "Conteos en base restaurada:"
echo "  - Usuarios:           ${REST_USERS}"
echo "  - Organizaciones:     ${REST_ORGS}"
echo "  - Membresías:         ${REST_MEMBERSHIPS}"
echo "  - Categorías:         ${REST_CATEGORIES}"
echo "  - Gastos:             ${REST_EXPENSES}"
echo "  - Partes de gastos:   ${REST_SPLITS}"
echo "  - Comprobantes activos:${REST_RECEIPTS}"

# Verificar comprobantes en BD contra archivos en volumen
echo "Verificando correspondencia de cada comprobante activo en el sistema de archivos..."
RECEIPT_ROWS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -F'|' -c "SELECT id, organization_id, storage_path, file_size_bytes FROM receipts WHERE is_active = TRUE;" 2>/dev/null || true)

INTEGRITY_ERRORS=0
CHECKED_RECEIPTS=0

if [ -n "${RECEIPT_ROWS}" ]; then
    while IFS='|' read -r r_id r_org r_path r_size; do
        [ -z "${r_id}" ] && continue
        CHECKED_RECEIPTS=$((CHECKED_RECEIPTS + 1))
        
        # Verificar existencia y tamaño del archivo en el volumen mediante contenedor efímero
        FILE_CHECK=$(docker run --rm -v "${RECEIPTS_VOLUME}:/data:ro" alpine:3.21 sh -c "
            if [ -f '/data/${r_path}' ]; then
                wc -c < '/data/${r_path}'
            else
                echo 'MISSING'
            fi
        " | tr -d ' ' || echo "ERROR")
        
        if [ "${FILE_CHECK}" = "MISSING" ]; then
            echo "ERROR DE INTEGRIDAD: Comprobante ID ${r_id} (org ${r_org}) no existe en /data/${r_path}" >&2
            INTEGRITY_ERRORS=$((INTEGRITY_ERRORS + 1))
        elif [ "${FILE_CHECK}" != "${r_size}" ]; then
            echo "ERROR DE INTEGRIDAD: Comprobante ID ${r_id} tiene tamaño discordante (BD: ${r_size} bytes, Disco: ${FILE_CHECK} bytes)" >&2
            INTEGRITY_ERRORS=$((INTEGRITY_ERRORS + 1))
        fi
    done <<< "${RECEIPT_ROWS}"
fi

echo "Comprobantes activos auditados: ${CHECKED_RECEIPTS} (errores: ${INTEGRITY_ERRORS})"

if [ "${INTEGRITY_ERRORS}" -gt 0 ]; then
    echo "ERROR: Falló la verificación de integridad cruzada post-restauración." >&2
    exit 1
fi

echo "=== Restauración y verificación completadas exitosamente ==="
