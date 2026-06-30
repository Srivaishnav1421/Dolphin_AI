#!/usr/bin/env python3
"""Auth DB maintenance - User requested cleanup operations."""
import psycopg2
import sys

def main():
    try:
        conn = psycopg2.connect(
            host="localhost", port=5432, dbname="dolphindb",
            user="dolphin", password="dolphin123"
        )
        conn.autocommit = True
        cur = conn.cursor()

        # Remove test-only user
        test_email = "test@dolphin.ai"
        cur.execute("SELECT count(*) FROM users WHERE email = %s", (test_email,))
        cnt = cur.fetchone()[0]
        if cnt > 0:
            sql_rm = "DELETE FROM users WHERE email = %s"
            cur.execute(sql_rm, (test_email,))
            print(f"Removed {cur.rowcount} test user row(s)")
        else:
            print("No test user found - already clean")

        # Restore admin password to known .env value
        admin_email = "admin@dolphin.ai"
        new_pw_hash = "$2a$10$uKMMNT9YUUk5YWZjQyffzON.GyVH6Y7sfpuvQKoaMKSCKaBnsQpnW"
        sql_up = "UPDATE users SET password = %s WHERE email = %s"
        cur.execute(sql_up, (new_pw_hash, admin_email))
        print(f"Restored admin password: {cur.rowcount} row(s) affected")

        # Show final state
        cur.execute("SELECT id, email, role, active, created_at FROM users ORDER BY created_at DESC")
        print("\nFinal users table:")
        for row in cur.fetchall():
            print(f"  {row}")

        cur.close()
        conn.close()
        print("\nDone.")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
