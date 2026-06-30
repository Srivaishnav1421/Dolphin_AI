#!/usr/bin/env python3
"""
Attempt all possible routes to gain superuser access to PostgreSQL.
Try: ident/peer auth with current OS user, postgres password, etc.
"""
import subprocess, os, getpass

current_user = getpass.getuser()
print(f"Current OS user: {current_user}")

def try_psql(user, db, sql, password=None, use_socket=False):
    e = dict(os.environ)
    if password:
        e["PGPASSWORD"] = password
    if use_socket:
        cmd = ["psql", "-U", user, "-d", db, "-t", "-A", "-c", sql]
    else:
        cmd = ["psql", "-h", "127.0.0.1", "-p", "5432", "-U", user, "-d", db, "-t", "-A", "-c", sql]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=10, env=e)
    return r.stdout.strip(), r.stderr.strip(), r.returncode

print("\n--- Try connecting as current OS user via peer auth (socket) ---")
out, err, rc = try_psql(current_user, "postgres", "SELECT current_user, usesuper FROM pg_user WHERE usename=current_user;", use_socket=True)
print(f"  rc={rc} out={out!r} err={err!r}")

print("\n--- Try connecting as 'postgres' via socket (peer auth) ---")
out, err, rc = try_psql("postgres", "postgres", "SELECT current_user;", use_socket=True)
print(f"  rc={rc} out={out!r} err={err!r}")

print("\n--- Try connecting as 'postgres' via TCP with no password ---")
out, err, rc = try_psql("postgres", "postgres", "SELECT current_user;")
print(f"  rc={rc} out={out!r} err={err!r}")

print("\n--- Try connecting as 'postgres' with password 'postgres' ---")
out, err, rc = try_psql("postgres", "postgres", "SELECT current_user;", password="postgres")
print(f"  rc={rc} out={out!r} err={err!r}")

print("\n--- Check pg_hba.conf location ---")
out, err, rc = try_psql("dolphin", "postgres", "SHOW hba_file;", password="dolphin123")
print(f"  hba_file: {out} err={err!r}")

print("\n--- Check if current user is in pg_user ---")
out, err, rc = try_psql("dolphin", "postgres", f"SELECT usename, usesuper, usecreatedb FROM pg_user WHERE usename='{current_user}';", password="dolphin123")
print(f"  result: {out}")

print("\n--- Try ALTER USER dolphin CREATEDB as dolphin (will fail, but let's confirm) ---")
out, err, rc = try_psql("dolphin", "postgres", "ALTER USER dolphin CREATEDB;", password="dolphin123")
print(f"  rc={rc} err={err!r}")

print("\n--- Check if we can create database in dolphindb as dolphin ---")
out, err, rc = try_psql("dolphin", "dolphindb", "CREATE DATABASE dolphin_empty_verify OWNER dolphin;", password="dolphin123")
print(f"  rc={rc} err={err!r}")
