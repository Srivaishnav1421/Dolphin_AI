#!/bin/bash
# Rebuild backend with auth cleanup and restart
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "  🐬 DolphinAI — Auth Cleanup Build & Restart"
echo "  ════════════════════════════════════════════"
echo ""

# 1. Stop existing backend
echo "  ⏹️  Stopping existing backend..."
pkill -f 'spring-boot:run' 2>/dev/null || true
pkill -f 'dolphin-ai' 2>/dev/null || true
sleep 2

# 2. Rebuild
echo "  🔨 Rebuilding backend..."
mvn compile -DskipTests -B -q

# 3. Restart with auth cleanup
echo "  🚀 Starting backend with auth cleanup..."
echo "  📌 DataSeeder.authCleanup() will:"
echo "     - Remove test@dolphin.ai"
echo "     - Restore admin@dolphin.ai password to DEMO_PASSWORD (dolphin123)"
echo ""

mvn spring-boot:run -DskipTests &
echo "  ⏳ Waiting for startup..."
sleep 30

# 4. Verify
echo "  🔍 Verifying health..."
curl -s http://localhost:8000/actuator/health
echo ""

# 5. Test login
echo "  🔑 Testing admin login..."
LOGIN_RESULT=$(curl -s -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@dolphin.ai","password":"dolphin123"}')
echo "  Login result: $LOGIN_RESULT"
echo ""
echo "  ✅ Done! Backend restarted with auth cleanup."
