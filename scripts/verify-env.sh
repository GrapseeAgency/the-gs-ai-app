#!/usr/bin/env bash
# Verify the sandbox environment for the-gs-ai-app monorepo.
# Run: bash scripts/verify-env.sh
set -euo pipefail

pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; FAILED=1; }
FAILED=0

echo "== GS AI App environment check =="

echo "[core]"
command -v git >/dev/null && pass "git $(git --version | awk '{print $3}')" || fail "git missing"
command -v bun  >/dev/null && pass "bun $(bun --version)" || fail "bun missing"
command -v node >/dev/null && pass "node $(node --version)" || fail "node missing"

echo "[git identity]"
NAME=$(git -C "$(dirname "$0")/.." config user.name || true)
EMAIL=$(git -C "$(dirname "$0")/.." config user.email || true)
[ "$NAME" = "Grapsee-Official" ] && pass "user.name=$NAME" || fail "user.name is '$NAME' (expected Grapsee-Official)"
[ "$EMAIL" = "graphesee@gmail.com" ] && pass "user.email=$EMAIL" || fail "user.email is '$EMAIL'"

echo "[remote]"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL=$(git -C "$ROOT" remote get-url origin 2>/dev/null || echo "")
[[ "$URL" == *"GrapseeAgency/the-gs-ai-app"* ]] && pass "origin -> GrapseeAgency/the-gs-ai-app" || fail "origin remote wrong: $URL"

echo "[toolchains]"
command -v java >/dev/null && pass "java $(java -version 2>&1 | head -1 | awk '{print $3}' | tr -d '"')" || fail "java missing (CI provides JDK 17)"
command -v xcodegen >/dev/null && pass "xcodegen" || echo "  ⚠️  xcodegen not on Linux — expected, generated in GitHub Actions (macOS)"
[ -d "$ROOT/android" ] && pass "android/ scaffold present" || fail "android/ missing"
[ -d "$ROOT/ios" ] && pass "ios/ scaffold present" || fail "ios/ missing"
[ -f "$ROOT/shared-contracts/openapi.yaml" ] && pass "openapi contract present" || fail "openapi.yaml missing"

echo
if [ "$FAILED" -eq 0 ]; then echo "All critical checks passed."; else echo "Some checks failed — see above."; exit 1; fi
