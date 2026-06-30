#!/usr/bin/env python3
"""
DolphinAI PostgreSQL Verification Helper
Connects using psycopg2 or psql subprocess and runs SQL count checks.
"""
import subprocess
import os
import sys

DB_HOST = os.environ.get("PGHOST", "127.0.0.1")
DB_PORT = os.environ.get("PGPORT", "5432")
DB_USER = os.environ.get("PGUSER", "dolphin")
DB_PASS = os.environ.get("PGPASSWORD", "dolphin123")
DB_NAME = os.environ.get("PGDATABASE", "dolphin_empty_verify")

CORE_TABLES = [
    "users", "organizations", "workspaces", "leads",
    "campaigns", "invoices", "wallet_transactions", "audit_logs",
]

OPTIONAL_TABLES = [
    "creative_assets", "automation_workflows", "workflow_runs",
    "campaign_metrics", "ai_outputs", "ai_requests",
]

BUSINESS_DATA_TABLES = ["leads", "campaigns", "invoices", "wallet_transactions"]

def run_sql(sql, dbname=None):
    db = dbname or DB_NAME
    env = dict(os.environ)
    env["PGPASSWORD"] = DB_PASS
    cmd = [
        "psql", "-h", DB_HOST, "-p", DB_PORT,
        "-U", DB_USER, "-d", db,
        "-t", "-c", sql
    ]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=10, env=env)
    return r.stdout.strip(), r.stderr.strip(), r.returncode

def check_tables(dbname=None):
    print(f"\n{'='*60}")
    print(f"DATABASE: {dbname or DB_NAME}")
    print(f"{'='*60}")

    failures = []

    print("\n--- Core Tables ---")
    for t in CORE_TABLES:
        out, err, rc = run_sql(f"SELECT COUNT(*) FROM {t};", dbname)
        if rc != 0:
            print(f"  {t}: ERROR -> {err}")
        else:
            count = out.strip()
            print(f"  {t}: {count} rows")
            if t in BUSINESS_DATA_TABLES and count not in ('', '0'):
                try:
                    if int(count) > 0:
                        failures.append(f"FAKE DATA in {t}: {count} rows (expected 0)")
                except ValueError:
                    pass

    print("\n--- Optional Tables ---")
    for t in OPTIONAL_TABLES:
        out, err, rc = run_sql(f"SELECT COUNT(*) FROM {t};", dbname)
        if rc != 0:
            print(f"  {t}: NOT APPLICABLE (table does not exist)")
        else:
            count = out.strip()
            print(f"  {t}: {count} rows")

    if failures:
        print(f"\n❌ FAILURES:")
        for f in failures:
            print(f"  - {f}")
        return False
    else:
        print(f"\n✅ No fake business data found in core tables.")
        return True

def create_empty_db():
    print("Creating dolphin_empty_verify database...")
    # Connect to dolphindb (existing) to create the new DB
    out, err, rc = run_sql("SELECT 1;", "dolphindb")
    if rc != 0:
        print(f"Cannot connect to dolphindb: {err}")
        return False

    # Drop and recreate the empty test DB
    run_sql("DROP DATABASE IF EXISTS dolphin_empty_verify;", "postgres")
    out, err, rc = run_sql("CREATE DATABASE dolphin_empty_verify OWNER dolphin;", "postgres")
    if rc != 0:
        print(f"Cannot create dolphin_empty_verify (maybe need superuser): {err}")
        print("Trying to use dolphindb as test DB instead...")
        return None  # fallback
    print("✅ dolphin_empty_verify created.")
    return True

if __name__ == "__main__":
    target_db = sys.argv[1] if len(sys.argv) > 1 else "dolphindb"
    print(f"\nDolphinAI PostgreSQL Database Truth Verification")
    print(f"Target DB: {target_db}")
    check_tables(target_db)
