#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
URL="${SPRING_DATASOURCE_URL:-}"
USER_NAME="${SPRING_DATASOURCE_USERNAME:-}"
PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"

if [[ "${PROFILE}" != "dev" && "${PROFILE}" != "local" ]]; then
  echo "Refusing reset: SPRING_PROFILES_ACTIVE must be dev/local, got '${PROFILE}'."
  exit 1
fi

if [[ ! "${URL}" =~ ^jdbc:postgresql://([^:/]+)(:([0-9]+))?/([^?]+) ]]; then
  echo "Refusing reset: SPRING_DATASOURCE_URL must be a PostgreSQL JDBC URL."
  exit 1
fi

DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-5432}"
DB_NAME="${BASH_REMATCH[4]}"
DB_NAME_LOWER="$(printf '%s' "${DB_NAME}" | tr '[:upper:]' '[:lower:]')"

if [[ "${DB_HOST}" != "localhost" && "${DB_HOST}" != "127.0.0.1" ]]; then
  echo "Refusing reset: host must be localhost or 127.0.0.1, got '${DB_HOST}'."
  exit 1
fi

if [[ "${DB_NAME_LOWER}" == *prod* || "${DB_NAME_LOWER}" == *production* || "${DB_NAME_LOWER}" == *live* ]]; then
  echo "Refusing reset: database name '${DB_NAME}' looks production-like."
  exit 1
fi

if [[ -z "${USER_NAME}" || -z "${PASSWORD}" ]]; then
  echo "Refusing reset: SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD are required."
  exit 1
fi

echo "About to reset local development database:"
echo "  profile: ${PROFILE}"
echo "  host:    ${DB_HOST}"
echo "  port:    ${DB_PORT}"
echo "  db:      ${DB_NAME}"
echo
echo "Type exactly: RESET LOCAL DEV DATABASE"
read -r CONFIRMATION

if [[ "${CONFIRMATION}" != "RESET LOCAL DEV DATABASE" ]]; then
  echo "Confirmation did not match. No changes made."
  exit 1
fi

BACKUP_DIR="${ROOT_DIR}/backups"
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/backup_${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql"

echo "Creating backup: ${BACKUP_FILE}"
PGPASSWORD="${PASSWORD}" pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${USER_NAME}" "${DB_NAME}" > "${BACKUP_FILE}"

echo "Resetting public schema..."
PGPASSWORD="${PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${USER_NAME}" -d "${DB_NAME}" \
  -v ON_ERROR_STOP=1 \
  -c "DROP SCHEMA public CASCADE;" \
  -c "CREATE SCHEMA public;"

echo "Local development database reset complete. Restart the backend to run Flyway migrations."
