#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  DolphinAI — Production Environment Secrets Audit Script
# ═══════════════════════════════════════════════════════════════════════

errors=0

verify_secret() {
    local name="$1"
    local val="${!name}"
    if [ -z "$val" ]; then
        echo "❌ MISSING SECRET: ${name}" >&2
        errors=$((errors + 1))
    else
        echo "✅ VERIFIED SECRET: ${name} (Length: ${#val} chars)"
    fi
}

echo "🛡️  Running production environment secrets audit..."

# Standard variables from docker-compose/production setup
verify_secret "JWT_SECRET"
verify_secret "META_ENCRYPTION_KEY"
verify_secret "SPRING_DATASOURCE_PASSWORD"

if [ $errors -gt 0 ]; then
    echo "❌ Secrets validation failed with $errors missing credentials!" >&2
    exit 1
else
    echo "✅ All required production env secrets are present."
fi
