#!/usr/bin/env python3
import subprocess, os

e = dict(os.environ)
e["PGPASSWORD"] = "dolphin123"

results = {}

# Test 1: dolphin attrs
r = subprocess.run(["psql","-h","127.0.0.1","-p","5432","-U","dolphin","-d","postgres","-t","-A","-c",
                    "SELECT usename,usesuper,usecreatedb FROM pg_user WHERE usename='dolphin'"],
                   capture_output=True, text=True, timeout=10, env=e)
results["dolphin_attrs"] = r.stdout.strip()

# Test 2: hba_file
r2 = subprocess.run(["psql","-h","127.0.0.1","-p","5432","-U","dolphin","-d","postgres","-t","-A","-c","SHOW hba_file"],
                    capture_output=True, text=True, timeout=10, env=e)
results["hba_file"] = r2.stdout.strip()

# Test 3: peer socket as srivan
e3 = dict(os.environ)
r3 = subprocess.run(["psql","-U","srivan","-d","postgres","-t","-A","-c","SELECT current_user,usesuper FROM pg_user WHERE usename=current_user"],
                    capture_output=True, text=True, timeout=5, env=e3)
results["srivan_peer"] = (r3.stdout.strip(), r3.stderr.strip()[:80], r3.returncode)

# Test 4: postgres socket
r4 = subprocess.run(["psql","-U","postgres","-d","postgres","-t","-A","-c","SELECT 1"],
                    capture_output=True, text=True, timeout=5, env=e3)
results["postgres_socket"] = (r4.stdout.strip(), r4.stderr.strip()[:80], r4.returncode)

for k, v in results.items():
    print(f"{k}: {v}")
