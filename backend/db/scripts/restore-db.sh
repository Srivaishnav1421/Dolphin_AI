#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  DolphinAI — Automated PostgreSQL Database Restore Script
#  RTO Target: 2 Hours
# ═══════════════════════════════════════════════════════════════════════

set -e

# Configuration
DB_HOST="${SPRING_DATASOURCE_URL:-localhost}"
DB_PORT="5432"
DB_NAME="dolphindb"
DB_USER="dolphin"
PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-dolphin123}"
export PGPASSWORD

if [ -z "$1" ]; then
    echo "❌ Usage: $0 <path_to_backup_file.sql.gz>" >&2
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "❌ Backup file not found: ${BACKUP_FILE}" >&2
    exit 1
fi

echo "⏳ Starting database restoration from [${BACKUP_FILE}]..."

# Drop existing connections and recreate/clean target schema
echo "🧹 Recreating clean public schema..."
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO dolphin;"

# Restore schema and data
echo "🌀 Importing backup SQL content..."
gunzip -c "${BACKUP_FILE}" | psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}"

echo "✅ Database restore completed successfully."
