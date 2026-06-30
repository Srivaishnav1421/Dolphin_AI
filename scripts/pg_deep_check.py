#!/usr/bin/env python3
"""
DolphinAI PostgreSQL Deep Investigation
"""
import subprocess, os

DB_HOST = "127.0.0.1"; DB_PORT = "5432"; DB_USER = "dolphin"; DB_PASS = "dolphin123"

def sql(q, db="dolphindb"):
    e = dict(os.environ); e["PGPASSWORD"] = DB_PASS
    r = subprocess.run(["psql","-h",DB_HOST,"-p",DB_PORT,"-U",DB_USER,"-d",db,"-c",q],
                       capture_output=True, text=True, timeout=15, env=e)
    return r.stdout, r.stderr, r.returncode

print("=== users ===")
o,e,rc = sql("SELECT name, email, role, created_at FROM users;")
print(o or e)

print("=== organizations ===")
o,e,rc = sql("SELECT id, name, plan FROM organizations;")
print(o or e)

print("=== workspaces ===")
o,e,rc = sql("SELECT id, name FROM workspaces;")
print(o or e)

print("=== leads (all rows) ===")
o,e,rc = sql("SELECT id, name, email, status, source, created_at FROM leads ORDER BY created_at;")
print(o or e)

print("=== campaigns (all rows) ===")
o,e,rc = sql("SELECT id, name, status, budget, spent, roas, account_id FROM campaigns;")
print(o or e)

print("=== wallets ===")
o,e,rc = sql("SELECT account_id, balance, total_spent FROM wallets;")
print(o or e)

print("=== subscription_plans ===")
o,e,rc = sql("SELECT name, base_price_inr, active FROM subscription_plans;")
print(o or e)

print("=== workflow_templates count ===")
o,e,rc = sql("SELECT COUNT(*) FROM workflow_templates;")
print(o or e)

print("=== audit_logs (last 10) ===")
o,e,rc = sql("SELECT action, entity_type, created_at FROM audit_logs ORDER BY created_at DESC LIMIT 10;")
print(o or e)

print("=== All table names in public schema ===")
o,e,rc = sql("SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;")
print(o or e)
