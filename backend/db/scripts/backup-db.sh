#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  DolphinAI — Automated PostgreSQL Database Backup Script
#  RPO Target: 24 Hours
# ═══════════════════════════════════════════════════════════════════════

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/dolphin}"
DB_HOST="${SPRING_DATASOURCE_URL:-localhost}"
DB_PORT="5432"
DB_NAME="dolphindb"
DB_USER="dolphin"
PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-dolphin123}"
export PGPASSWORD

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TIMESTAMP}.sql.gz"

echo "⏳ Starting database backup for [${DB_NAME}]..."
mkdir -p "${BACKUP_DIR}"

# Run pg_dump
if pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -F p | gzip > "${BACKUP_FILE}"; then
    echo "✅ Backup successfully created: ${BACKUP_FILE}"
    
    # ── Retention Policy: Keep last 7 backups ──────────────────────────
    echo "🧹 Cleaning up backups older than 7 days..."
    find "${BACKUP_DIR}" -name "${DB_NAME}_backup_*.sql.gz" -mtime +7 -exec rm {} \;
    echo "✅ Retention cleanup completed."
else
    echo "❌ Database backup failed!" >&2
    exit 1
fi
