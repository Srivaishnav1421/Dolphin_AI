#!/usr/bin/env python3
"""Step 1: Check dolphin user privileges and attempt CREATEDB grant."""
import subprocess, os, sys

def psql(sql, user="dolphin", password="dolphin123", db="postgres"):
    e = dict(os.environ)
    e["PGPASSWORD"] = password
    r = subprocess.run(
        ["psql", "-h", "127.0.0.1", "-p", "5432", "-U", user, "-d", db,
         "-t", "-A", "-c", sql],
        capture_output=True, text=True, timeout=15, env=e
    )
    return r.stdout.strip(), r.stderr.strip(), r.returncode

print("=== Step 1: Check dolphin user attributes ===")
out, err, rc = psql("SELECT usename, usecreatedb, usesuper FROM pg_user WHERE usename='dolphin';")
print("Result:", out if rc == 0 else err)

print("\n=== Step 2: Try to create dolphin_empty_verify as dolphin ===")
psql("DROP DATABASE IF EXISTS dolphin_empty_verify;")
out, err, rc = psql("CREATE DATABASE dolphin_empty_verify OWNER dolphin;")
if rc == 0:
    print("SUCCESS: Created dolphin_empty_verify")
else:
    print("FAILED:", err)
    # Try alternate: see if postgres peer auth works
    print("\n=== Step 2b: Try as postgres superuser (peer auth) ===")
    e2 = dict(os.environ)
    r2 = subprocess.run(
        ["sudo", "-u", "postgres", "psql", "-c",
         "ALTER USER dolphin CREATEDB; CREATE DATABASE dolphin_empty_verify OWNER dolphin;"],
        capture_output=True, text=True, timeout=10, env=e2
    )
    print("sudo stdout:", r2.stdout)
    print("sudo stderr:", r2.stderr)
    print("sudo rc:", r2.returncode)
