#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
#  DolphinAI — Backend Runner
# ═══════════════════════════════════════════════════════════════════

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "  🐬 DolphinAI — Backend"
echo "  ════════════════════════════════"

# ── Check Java ─────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo "  ❌ Java not found. Install: sudo apt install openjdk-21-jdk -y"
  exit 1
fi
JAVA_VER=$(java --version 2>&1 | head -n1)
echo "  ✅ Java: $JAVA_VER"

# ── Check / Install Maven ──────────────────────────────────────────
if ! command -v mvn &>/dev/null; then
  echo "  📦 Maven not found. Installing..."
  sudo apt-get install -y maven -q
fi
MVN_VER=$(mvn --version 2>&1 | head -n1)
echo "  ✅ Maven: $MVN_VER"

echo ""
echo "  🚀 Starting backend on http://localhost:8000"
echo "  📊 H2 Console: http://localhost:8000/h2-console"
echo "  💚 Health: http://localhost:8000/actuator/health"
echo ""

mvn spring-boot:run -q
