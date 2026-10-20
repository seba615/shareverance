#!/usr/bin/env bash
# ==============================================================================
# Shareverance - Procedimiento de Respaldo Consistente (E7.1 / A19)
#
# Genera una copia consistente de:
# 1. Base de datos PostgreSQL (pg_dump formato personalizado .dump)
# 2. Volumen persistente de comprobantes (tarball comprimido .tar.gz)
# 3. Manifiesto de integridad (manifest.json con SHA-256 y conteos de negocio)
#
# Uso:
#   ./scripts/backup.sh [directorio_destino]
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

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
TIMESTAMP="$(date -u +"%Y%m%d_%H%M%S")"
ISO_TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
BACKUP_DIR="${1:-${ROOT_DIR}/backups/backup_${TIMESTAMP}}"

# Función para calcular SHA-256
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

echo "=== Iniciando respaldo consistente de Shareverance (${ISO_TIMESTAMP}) ==="
echo "Directorio de destino: ${BACKUP_DIR}"

# 1. Detectar o verificar contenedor PostgreSQL
if [ -z "${POSTGRES_CONTAINER}" ]; then
    # Intentar resolver mediante docker compose o nombre estándar
    if command -v docker >/dev/null 2>&1; then
        POSTGRES_CONTAINER="$(docker ps --filter "name=postgres" --filter "status=running" --format "{{.Names}}" | head -n 1)"
    fi
fi

if [ -z "${POSTGRES_CONTAINER}" ]; then
    echo "ERROR: No se encontró ningún contenedor de PostgreSQL en ejecución." >&2
    exit 1
fi
echo "Contenedor PostgreSQL: ${POSTGRES_CONTAINER}"

# 2. Detectar o verificar volumen de comprobantes
if [ -z "${RECEIPTS_VOLUME}" ]; then
    RECEIPTS_VOLUME="$(docker volume ls --filter "name=receipts_data" --format "{{.Name}}" | head -n 1)"
    if [ -z "${RECEIPTS_VOLUME}" ]; then
        RECEIPTS_VOLUME="shareverance_receipts_data"
    fi
fi
echo "Volumen de comprobantes: ${RECEIPTS_VOLUME}"

# Crear directorio de destino
mkdir -p "${BACKUP_DIR}"

# 3. Ventana consistente: pausar java-service si está corriendo para evitar escrituras concurrentes
JAVA_CONTAINER="$(docker ps --filter "name=java-service" --filter "status=running" --format "{{.Names}}" | head -n 1 || true)"
WAS_PAUSED=0
if [ -n "${JAVA_CONTAINER}" ]; then
    echo "Pausando ${JAVA_CONTAINER} durante la ventana de copia consistente..."
    docker pause "${JAVA_CONTAINER}" >/dev/null
    WAS_PAUSED=1
fi

cleanup() {
    if [ "${WAS_PAUSED}" -eq 1 ] && [ -n "${JAVA_CONTAINER}" ]; then
        echo "Reanudando ${JAVA_CONTAINER}..."
        docker unpause "${JAVA_CONTAINER}" >/dev/null || true
    fi
}
trap cleanup EXIT

# 4. Volcado de la base de datos con pg_dump
echo "Realizando volcado de PostgreSQL (formato custom -Fc)..."
DUMP_FILE="${BACKUP_DIR}/db.dump"
docker exec -i "${POSTGRES_CONTAINER}" pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Fc > "${DUMP_FILE}"

# 5. Conteo de entidades de negocio para auditoría
echo "Extrayendo métricas de entidades de negocio..."
COUNT_USERS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM users;" 2>/dev/null || echo 0)
COUNT_ORGS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM organizations;" 2>/dev/null || echo 0)
COUNT_MEMBERSHIPS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM user_organization;" 2>/dev/null || echo 0)
COUNT_CATEGORIES=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM categories;" 2>/dev/null || echo 0)
COUNT_EXPENSES=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM expenses;" 2>/dev/null || echo 0)
COUNT_SPLITS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM expense_splits;" 2>/dev/null || echo 0)
COUNT_RECEIPTS=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM receipts WHERE is_active = TRUE;" 2>/dev/null || echo 0)
COUNT_COVERAGE=$(docker exec -i "${POSTGRES_CONTAINER}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -t -A -c "SELECT COUNT(*) FROM analytics_month_coverage WHERE is_confirmed = TRUE;" 2>/dev/null || echo 0)

# 6. Empaquetado comprimido del volumen de comprobantes
echo "Empaquetando volumen de comprobantes (${RECEIPTS_VOLUME})..."
RECEIPTS_TAR="${BACKUP_DIR}/receipts.tar.gz"

# Si el volumen existe, empaquetar con un contenedor efímero
if docker volume inspect "${RECEIPTS_VOLUME}" >/dev/null 2>&1; then
    docker run --rm \
        -v "${RECEIPTS_VOLUME}:/data:ro" \
        -v "${BACKUP_DIR}:/backup" \
        alpine:3.21 sh -c "tar -czf /backup/receipts.tar.gz -C /data . 2>/dev/null || tar -czf /backup/receipts.tar.gz -C /data --exclude='lost+found' ."
else
    echo "AVISO: Volumen ${RECEIPTS_VOLUME} no encontrado; generando archivo vacío."
    tar -czf "${RECEIPTS_TAR}" -T /dev/null
fi

TOTAL_RECEIPT_FILES=$(tar -tzf "${RECEIPTS_TAR}" 2>/dev/null | grep -v '/$' | grep -v 'lost+found' | wc -l | tr -d ' ' || echo 0)

# 7. Cálculo de sumas de verificación SHA-256
echo "Calculando sumas de verificación SHA-256..."
DB_SHA256=$(hash_sha256 "${DUMP_FILE}")
RECEIPTS_SHA256=$(hash_sha256 "${RECEIPTS_TAR}")

# 8. Generación del manifiesto manifest.json
MANIFEST_FILE="${BACKUP_DIR}/manifest.json"
cat <<EOF > "${MANIFEST_FILE}"
{
  "version": "1.0",
  "created_at": "${ISO_TIMESTAMP}",
  "database": {
    "name": "${POSTGRES_DB}",
    "dump_file": "db.dump",
    "dump_sha256": "${DB_SHA256}",
    "entities": {
      "users": ${COUNT_USERS},
      "organizations": ${COUNT_ORGS},
      "user_organization": ${COUNT_MEMBERSHIPS},
      "categories": ${COUNT_CATEGORIES},
      "expenses": ${COUNT_EXPENSES},
      "expense_splits": ${COUNT_SPLITS},
      "receipts_active": ${COUNT_RECEIPTS},
      "analytics_month_coverage": ${COUNT_COVERAGE}
    }
  },
  "receipts": {
    "volume_name": "${RECEIPTS_VOLUME}",
    "archive_file": "receipts.tar.gz",
    "archive_sha256": "${RECEIPTS_SHA256}",
    "total_files": ${TOTAL_RECEIPT_FILES}
  }
}
EOF

# 9. Conclusión y resumen
echo "=== Respaldo completado exitosamente ==="
echo "Archivos generados en: ${BACKUP_DIR}"
echo "  - db.dump          (SHA256: ${DB_SHA256})"
echo "  - receipts.tar.gz  (SHA256: ${RECEIPTS_SHA256}, ${TOTAL_RECEIPT_FILES} archivos)"
echo "  - manifest.json    (Auditoría y conteos de negocio)"
