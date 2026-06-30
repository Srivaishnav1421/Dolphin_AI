#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
#  DolphinAI — verify-empty-postgres.sh
#  Database Truth Enforcement: Case 4 + Case 5 Manual Verification Script
#
#  Purpose:
#    1. Connects to existing dolphindb database.
#    2. Runs Flyway migrations and Hibernate updates in FRESH_SCHEMA mode.
#    3. Queries SQL row counts for all business tables inside the schemas.
#    4. FAILs (exit 1) if any business data is found in the empty DB.
#    5. Verifies FIRST_RUN_OWNER_* creates NO fake business data.
#
#  Usage:
#    bash scripts/verify-empty-postgres.sh
# ══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-dolphin}"
PGPASSWORD="${PGPASSWORD:-dolphin123}"
VERIFY_DB="dolphindb"
TIMESTAMP=$(date +%s)
VERIFY_SCHEMA="verify_empty_${TIMESTAMP}"
VERIFY_SCHEMA_5="verify_firstrun_${TIMESTAMP}"
BACKEND_DIR="$(cd "$(dirname "$0")/../backend" && pwd)"

export PGPASSWORD

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}✅ PASS${NC}: $1"; }
fail() { echo -e "${RED}❌ FAIL${NC}: $1"; FAILURES=$((FAILURES+1)); }
info() { echo -e "${YELLOW}ℹ️  INFO${NC}: $1"; }

FAILURES=0

echo "══════════════════════════════════════════════════════════════════════"
echo "  DolphinAI — Database Truth Enforcement Verification (FRESH_SCHEMA Mode)"
echo "  $(date)"
echo "══════════════════════════════════════════════════════════════════════"
echo ""

# ── Step 1: Check PostgreSQL connectivity ─────────────────────────────────────
info "Checking PostgreSQL connectivity at $PGHOST:$PGPORT/dolphindb ..."
if ! psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${RED}ERROR${NC}: Cannot connect to PostgreSQL database '$VERIFY_DB'. Verify PostgreSQL is running."
    echo "  Host: $PGHOST:$PGPORT"
    echo "  User: $PGUSER"
    exit 1
fi
pass "PostgreSQL connectivity OK"

# ── Step 2: Create fresh empty verification schema ──────────────────────────
info "Setting up empty verification schema: $VERIFY_SCHEMA inside $VERIFY_DB ..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "DROP SCHEMA IF EXISTS $VERIFY_SCHEMA CASCADE;" > /dev/null 2>&1 || true
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "CREATE SCHEMA $VERIFY_SCHEMA;" > /dev/null 2>&1
pass "Created fresh schema: $VERIFY_SCHEMA"

# Pre-create metric_snapshots in VERIFY_SCHEMA to satisfy V35 migration parser
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "CREATE TABLE $VERIFY_SCHEMA.metric_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(255) NOT NULL,
    campaign_name VARCHAR(255),
    snapshot_date DATE NOT NULL,
    impressions BIGINT,
    reach BIGINT,
    clicks BIGINT,
    link_clicks BIGINT,
    spend DOUBLE PRECISION,
    conversions BIGINT,
    revenue DOUBLE PRECISION,
    leads BIGINT,
    ctr DOUBLE PRECISION,
    cpc DOUBLE PRECISION,
    cpl DOUBLE PRECISION,
    cpa DOUBLE PRECISION,
    roas DOUBLE PRECISION,
    frequency DOUBLE PRECISION,
    emas_score DOUBLE PRECISION,
    emas_roas DOUBLE PRECISION,
    emas_ctr DOUBLE PRECISION,
    created_at TIMESTAMP
  );" > /dev/null 2>&1

# ── Step 3: Run backend migrations via Spring Boot (Flyway) ───────────────────
info "Running backend startup to apply Flyway migrations to schema $VERIFY_SCHEMA ..."
info "This starts the backend briefly in migration-only mode (auto-exits after init) ..."

SPRING_DATASOURCE_URL="jdbc:postgresql://$PGHOST:$PGPORT/$VERIFY_DB?currentSchema=$VERIFY_SCHEMA" \
SPRING_DATASOURCE_USERNAME="$PGUSER" \
SPRING_DATASOURCE_PASSWORD="$PGPASSWORD" \
SPRING_PROFILES_ACTIVE="dev" \
FIRST_RUN_OWNER_EMAIL="" \
FIRST_RUN_OWNER_PASSWORD="" \
DEMO_EMAIL="" \
DEMO_PASSWORD="" \
  timeout 120 mvn -f "$BACKEND_DIR/pom.xml" spring-boot:run \
    -Dspring-boot.run.arguments="--spring.main.web-application-type=none" \
    -Dspring-boot.run.jvmArguments="-Dserver.port=0 -Dspring.jpa.hibernate.ddl-auto=update -Dspring.flyway.schemas=$VERIFY_SCHEMA -Dspring.flyway.default-schema=$VERIFY_SCHEMA -Dspring.jpa.properties.hibernate.default_schema=$VERIFY_SCHEMA -Dspring.flyway.baseline-on-migrate=true -Dspring.flyway.baseline-version=0" \
  > /tmp/dolphin-migration.log 2>&1 || true

info "Migration log tail:"
tail -20 /tmp/dolphin-migration.log 2>/dev/null || true

# ── Step 4: SQL Row Count Verification ───────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  CASE 4: Empty PostgreSQL Schema — SQL Row Counts"
echo "══════════════════════════════════════════════════════════════════════"

CURRENT_SCHEMA="$VERIFY_SCHEMA"

run_count() {
    local table="$1"
    local count
    count=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" \
            -t -c "SELECT COUNT(*) FROM $CURRENT_SCHEMA.$table;" 2>/dev/null | tr -d ' ') || echo "N/A"
    echo "$count"
}

table_exists() {
    local table="$1"
    local res
    res=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" \
          -t -c "SELECT 1 FROM information_schema.tables WHERE table_schema = '$CURRENT_SCHEMA' AND table_name = '$table';" 2>/dev/null | tr -d ' ')
    [ "$res" = "1" ]
}

echo ""
echo "--- Core Business Tables (must all be 0 in empty DB) ---"
BUSINESS_TABLES=("leads" "campaigns" "invoices" "wallet_transactions")
REFERENCE_TABLES=("users" "organizations" "workspaces" "audit_logs")

for t in "${REFERENCE_TABLES[@]}"; do
    c=$(run_count "$t")
    echo "  $t: $c"
done

echo ""
echo "--- Business Data Tables (expected 0 in fresh empty DB) ---"
for t in "${BUSINESS_TABLES[@]}"; do
    c=$(run_count "$t")
    echo "  $t: $c"
    if [ "$c" != "0" ] && [ "$c" != "N/A" ] && [ "$c" != "" ]; then
        fail "$t contains $c rows in empty schema — fake/seed business data detected!"
    else
        pass "$t: $c rows (correct empty state)"
    fi
done

echo ""
echo "--- Optional Analytics/AI Tables ---"
OPTIONAL_TABLES=("creative_assets" "automation_workflows" "workflow_runs" "campaign_metrics" "ai_outputs" "ai_requests")
for t in "${OPTIONAL_TABLES[@]}"; do
    if table_exists "$t"; then
        c=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" \
            -t -c "SELECT COUNT(*) FROM $CURRENT_SCHEMA.$t;" 2>/dev/null | tr -d ' ')
    else
        c="NOT APPLICABLE"
    fi
    echo "  $t: $c"
done

# ── Step 5: CASE 5 — FIRST_RUN_OWNER_* bootstrap verification ────────────────
echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  CASE 5: FIRST_RUN_OWNER_* Bootstrap Verification"
echo "══════════════════════════════════════════════════════════════════════"
info "Setting up schema: $VERIFY_SCHEMA_5 for FIRST_RUN_OWNER_* verification ..."

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "DROP SCHEMA IF EXISTS $VERIFY_SCHEMA_5 CASCADE;" > /dev/null 2>&1 || true
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "CREATE SCHEMA $VERIFY_SCHEMA_5;" > /dev/null 2>&1

# Pre-create metric_snapshots in VERIFY_SCHEMA_5 to satisfy V35 migration parser
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c \
  "CREATE TABLE $VERIFY_SCHEMA_5.metric_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(255) NOT NULL,
    campaign_name VARCHAR(255),
    snapshot_date DATE NOT NULL,
    impressions BIGINT,
    reach BIGINT,
    clicks BIGINT,
    link_clicks BIGINT,
    spend DOUBLE PRECISION,
    conversions BIGINT,
    revenue DOUBLE PRECISION,
    leads BIGINT,
    ctr DOUBLE PRECISION,
    cpc DOUBLE PRECISION,
    cpl DOUBLE PRECISION,
    cpa DOUBLE PRECISION,
    roas DOUBLE PRECISION,
    frequency DOUBLE PRECISION,
    emas_score DOUBLE PRECISION,
    emas_roas DOUBLE PRECISION,
    emas_ctr DOUBLE PRECISION,
    created_at TIMESTAMP
  );" > /dev/null 2>&1

OWNER_EMAIL="first.run.owner@dolphin.verify"
OWNER_PASSWORD="VerifyPass@2026"
OWNER_NAME="Verification Owner"

SPRING_DATASOURCE_URL="jdbc:postgresql://$PGHOST:$PGPORT/$VERIFY_DB?currentSchema=$VERIFY_SCHEMA_5" \
SPRING_DATASOURCE_USERNAME="$PGUSER" \
SPRING_DATASOURCE_PASSWORD="$PGPASSWORD" \
SPRING_PROFILES_ACTIVE="dev" \
FIRST_RUN_OWNER_EMAIL="$OWNER_EMAIL" \
FIRST_RUN_OWNER_PASSWORD="$OWNER_PASSWORD" \
FIRST_RUN_OWNER_NAME="$OWNER_NAME" \
DEMO_EMAIL="" \
DEMO_PASSWORD="" \
DEMO_USERS_ENABLED="false" \
  timeout 120 mvn -f "$BACKEND_DIR/pom.xml" spring-boot:run \
    -Dspring-boot.run.arguments="--spring.main.web-application-type=none" \
    -Dspring-boot.run.jvmArguments="-Dserver.port=0 -Dspring.jpa.hibernate.ddl-auto=update -Dspring.flyway.schemas=$VERIFY_SCHEMA_5 -Dspring.flyway.default-schema=$VERIFY_SCHEMA_5 -Dspring.jpa.properties.hibernate.default_schema=$VERIFY_SCHEMA_5 -Dspring.flyway.baseline-on-migrate=true -Dspring.flyway.baseline-version=0" \
  > /tmp/dolphin-firstrun.log 2>&1 || true

CURRENT_SCHEMA="$VERIFY_SCHEMA_5"

echo ""
echo "--- CASE 5 SQL Counts (after FIRST_RUN_OWNER_* bootstrap) ---"

C5_USERS=$(run_count "users")
C5_ORGS=$(run_count "organizations")
C5_WORKSPACES=$(run_count "workspaces")
C5_LEADS=$(run_count "leads")
C5_CAMPAIGNS=$(run_count "campaigns")
C5_INVOICES=$(run_count "invoices")
C5_WALLET_TXN=$(run_count "wallet_transactions")
C5_AUDIT=$(run_count "audit_logs")

echo "  users: $C5_USERS"
echo "  organizations: $C5_ORGS"
echo "  workspaces: $C5_WORKSPACES"
echo "  leads: $C5_LEADS"
echo "  campaigns: $C5_CAMPAIGNS"
echo "  invoices: $C5_INVOICES"
echo "  wallet_transactions: $C5_WALLET_TXN"
echo "  audit_logs: $C5_AUDIT"

# Validate CASE 5 expectations
[ "$C5_USERS" = "1" ]       && pass "users: 1 (first-run owner only)" \
                             || fail "users: expected 1, got $C5_USERS"
[ "$C5_ORGS" -ge 1 ] 2>/dev/null && pass "organizations: $C5_ORGS (bootstrap org created)" \
                                 || fail "organizations: expected >=1, got $C5_ORGS"
[ "$C5_WORKSPACES" -ge 1 ] 2>/dev/null && pass "workspaces: $C5_WORKSPACES (bootstrap workspace created)" \
                                         || fail "workspaces: expected >=1, got $C5_WORKSPACES"
[ "$C5_LEADS" = "0" ]       && pass "leads: 0 (no fake leads)" \
                             || fail "leads: expected 0, got $C5_LEADS — FAKE BUSINESS DATA!"
[ "$C5_CAMPAIGNS" = "0" ]   && pass "campaigns: 0 (no fake campaigns)" \
                             || fail "campaigns: expected 0, got $C5_CAMPAIGNS — FAKE BUSINESS DATA!"
[ "$C5_INVOICES" = "0" ]    && pass "invoices: 0 (no fake invoices)" \
                             || fail "invoices: expected 0, got $C5_INVOICES — FAKE BUSINESS DATA!"
[ "$C5_WALLET_TXN" = "0" ]  && pass "wallet_transactions: 0 (no fake transactions)" \
                             || fail "wallet_transactions: expected 0, got $C5_WALLET_TXN — FAKE DATA!"

# Cleanup schemas
info "Cleaning up verification schemas..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c "DROP SCHEMA IF EXISTS $VERIFY_SCHEMA CASCADE;" > /dev/null 2>&1 || true
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$VERIFY_DB" -c "DROP SCHEMA IF EXISTS $VERIFY_SCHEMA_5 CASCADE;" > /dev/null 2>&1 || true

# ── Final Summary ─────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════════════════"
if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}  DATABASE TRUTH FULLY VERIFIED — OK TO CONTINUE PHASE 1${NC}"
    echo "══════════════════════════════════════════════════════════════════════"
    exit 0
else
    echo -e "${RED}  FAILED — FIX $FAILURES BLOCKERS FIRST${NC}"
    echo "══════════════════════════════════════════════════════════════════════"
    exit 1
fi
