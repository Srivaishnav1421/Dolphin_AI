#!/usr/bin/env python3
import subprocess, os, getpass

user = getpass.getuser()
print("OS user:", user)

e = dict(os.environ)
e["PGPASSWORD"] = "dolphin123"

def q(sql, pguser="dolphin", db="postgres"):
    r = subprocess.run(["psql","-h","127.0.0.1","-p","5432","-U",pguser,"-d",db,"-t","-A","-c",sql],
                       capture_output=True, text=True, timeout=10, env=e)
    return r.stdout.strip(), r.stderr.strip(), r.returncode

print("dolphin attrs:", q("SELECT usename,usesuper,usecreatedb FROM pg_user WHERE usename='dolphin'"))
print("hba_file:", q("SHOW hba_file"))

# Try peer socket as current user
e2 = dict(os.environ)
r = subprocess.run(["psql","-U",user,"-d","postgres","-t","-A","-c","SELECT current_user, usesuper FROM pg_user WHERE usename=current_user"],
                   capture_output=True, text=True, timeout=5, env=e2)
print("peer-socket result:", r.stdout.strip(), r.stderr.strip()[:100])

# Try as postgres via socket
r2 = subprocess.run(["psql","-U","postgres","-d","postgres","-t","-A","-c","SELECT 1"],
                    capture_output=True, text=True, timeout=5, env=e2)
print("postgres-socket result:", r2.stdout.strip(), r2.stderr.strip()[:100])
