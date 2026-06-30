#!/usr/bin/env python3
import subprocess, os, sys

host="127.0.0.1"; port="5432"; user="dolphin"; pwd="dolphin123"; db="dolphindb"

def q(sql):
    e=dict(os.environ); e["PGPASSWORD"]=pwd
    r=subprocess.run(["psql","-h",host,"-p",port,"-U",user,"-d",db,"-c",sql],
                     capture_output=True,text=True,timeout=15,env=e)
    return r.stdout, r.stderr

queries=[
    ("users","SELECT name,email,role,created_at FROM users"),
    ("organizations","SELECT id,name,plan FROM organizations"),
    ("workspaces","SELECT id,name FROM workspaces"),
    ("leads","SELECT id,name,email,status,source,created_at FROM leads ORDER BY created_at"),
    ("campaigns","SELECT id,name,status,budget,spent,roas,account_id FROM campaigns"),
    ("wallets","SELECT account_id,balance,total_spent FROM wallets"),
    ("subscription_plans","SELECT name,base_price_inr,active FROM subscription_plans"),
    ("workflow_templates","SELECT COUNT(*) FROM workflow_templates"),
    ("audit_logs_recent","SELECT action,entity_type,created_at FROM audit_logs ORDER BY created_at DESC LIMIT 5"),
    ("all_tables","SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name"),
]

for label,sql in queries:
    print(f"\n=== {label} ===")
    out,err=q(sql)
    print(out or err)
