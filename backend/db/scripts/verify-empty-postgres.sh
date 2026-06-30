#!/usr/bin/env bash
set -euo pipefail

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-dolphin}"
PGDATABASE="${PGDATABASE:-dolphin_empty_verify}"

PSQL=(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 -At)

table_exists() {
  local table="$1"
  "${PSQL[@]}" -c "select to_regclass('public.${table}') is not null;"
}

count_table() {
  local table="$1"
  "${PSQL[@]}" -c "select count(*) from ${table};"
}

print_count() {
  local label="$1"
  local table="$2"
  if [[ "$(table_exists "$table")" == "t" ]]; then
    printf '%-32s %s\n' "$label" "$(count_table "$table")"
  else
    printf '%-32s N/A\n' "$label"
  fi
}

require_zero() {
  local table="$1"
  if [[ "$(table_exists "$table")" != "t" ]]; then
    return
  fi

  local count
  count="$(count_table "$table")"
  if [[ "$count" != "0" ]]; then
    echo "FAIL: ${table} contains ${count} rows; expected zero fake/business seed rows." >&2
    exit 1
  fi
}

echo "Database: ${PGDATABASE} (${PGHOST}:${PGPORT})"
echo
echo "Required counts"
print_count users users
print_count organizations organizations
print_count workspaces workspaces
print_count leads leads
print_count campaigns campaigns
print_count invoices invoices
print_count wallet_transactions wallet_transactions
print_count audit_logs audit_logs

echo
echo "Optional analytics/creative/automation counts"
print_count creative_assets creative_assets
print_count ad_creatives ad_creatives
print_count automation_workflows automation_workflows
print_count workflow_templates workflow_templates
print_count workflow_runs workflow_runs
print_count workflow_executions workflow_executions
print_count campaign_metrics campaign_metrics
print_count metric_snapshots metric_snapshots
print_count ai_outputs ai_outputs
print_count ai_requests ai_requests
print_count ai_usage_logs ai_usage_logs

require_zero leads
require_zero campaigns
require_zero invoices
require_zero wallet_transactions
require_zero creative_assets
require_zero ad_creatives
require_zero automation_workflows
require_zero workflow_runs
require_zero workflow_executions
require_zero campaign_metrics
require_zero metric_snapshots
require_zero ai_outputs
require_zero ai_requests
require_zero ai_usage_logs

echo
echo "PASS: no fake business data found in guarded tables."
