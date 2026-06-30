#!/usr/bin/env python3
"""Direct DB connection auth cleanup using psycopg2."""
import psycopg2

conn = psycopg2.connect(
    host="localhost", port=5432, dbname="dolphindb",
    user="dolphin", password="dolphin123"
)
conn.autocommit = True
cur = conn.cursor()

# Delete test user
cur.execute("DELETE FROM users WHERE email = %s", ("test@dolphin.ai",))
print(f"Deleted {cur.rowcount} test user(s)")

# Restore admin password  
new_hash = "$2a$10$uKMMNT9YUUk5YWZjQyffzON.GyVH6Y7sfpuvQKoaMKSCKaBnsQpnW"
cur.execute("UPDATE users SET password = %s WHERE email = %s", (new_hash, "admin@dolphin.ai"))
print(f"Updated {cur.rowcount} admin password(s)")

# Verify
cur.execute("SELECT id, email, role, active, created_at FROM users ORDER BY created_at DESC")
for row in cur.fetchall():
    print(row)

cur.close()
conn.close()
print("Auth cleanup complete.")
