#!/bin/bash
# Auth cleanup script for CRM API proof
# Removes test@dolphin.ai test user and restores admin password

export PGPASSWORD=dolphin123

echo "=== Step 1: Delete test user ==="
psql -h localhost -U dolphin -d dolphindb -c "DELETE FROM users WHERE email = 'test@dolphin.ai';"

echo ""
echo "=== Step 2: Restore admin password to dolphin123 ==="
psql -h localhost -U dolphin -d dolphindb -c "UPDATE users SET password = '\$2a\$10\$uKMMNT9YUUk5YWZjQyffzON.GyVH6Y7sfpuvQKoaMKSCKaBnsQpnW' WHERE email = 'admin@dolphin.ai';"

echo ""
echo "=== Step 3: Verify users table ==="
psql -h localhost -U dolphin -d dolphindb -c "SELECT id, email, role, active, created_at FROM users ORDER BY created_at DESC;"

echo ""
echo "=== Done ==="
