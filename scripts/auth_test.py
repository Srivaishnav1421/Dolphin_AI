import subprocess
import os
e = dict(os.environ)
e["PGPASSWORD"] = "dolphin123"
sqls = [
    "SELECT usename,usesuper,usecreatedb FROM pg_user WHERE usename='dolphin'",
    "SHOW hba_file",
]
for s in sqls:
    r = subprocess.run(["psql","-h","127.0.0.1","-p","5432","-U","dolphin","-d","postgres","-t","-A","-c",s], capture_output=True, text=True, timeout=10, env=e)
    print(s[:40], "->", r.stdout.strip() or r.stderr.strip()[:60])

r2 = subprocess.run(["psql","-U","srivan","-d","postgres","-t","-A","-c","SELECT current_user,usesuper FROM pg_user WHERE usename=current_user"], capture_output=True, text=True, timeout=5)
print("srivan peer:", r2.stdout.strip(), r2.stderr.strip()[:60], r2.returncode)

r3 = subprocess.run(["psql","-U","postgres","-d","postgres","-t","-A","-c","SELECT 1"], capture_output=True, text=True, timeout=5)
print("postgres sock:", r3.stdout.strip(), r3.stderr.strip()[:60], r3.returncode)
