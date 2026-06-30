#!/usr/bin/env python3
"""Auth cleanup: delete test user and restore admin password."""
import subprocess, sys

DB_CMD = ["psql", "-h", "localhost", "-U", "dolphin", "-d", "dolphindb", "-t", "-A"]
env = {"PGPASSWORD": "dolphin123"}

def run_sql(sql):
    r = subprocess.run(DB_CMD + ["-c", sql], capture_output=True, text=True, env={**__import__('os').environ, **env})
    print(r.stdout.strip())
    if r.stderr.strip():
        print(r.stderr.strip(), file=sys.stderr)
    return r.returncode

print("=== Delete test@dolphin.ai ===")
run_sql("DELETE FROM users WHERE email = 'test@dolphin.ai';")

print("\n=== Restore admin password to dolphin123 ===")
run_sql("UPDATE users SET password = '$2a$10$uKMMNT9YUUk5YWZjQyffzON.GyVH6Y7sfpuvQKoaMKSCKaBnsQpnW' WHERE email = 'admin@dolphin.ai';")

print("\n=== Verify ===")
run_sql("SELECT id, email, role, active, created_at FROM users ORDER BY created_at DESC;")
