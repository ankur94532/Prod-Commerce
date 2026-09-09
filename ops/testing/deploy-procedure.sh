#!/usr/bin/env bash
# Executes the deploy orchestrator against a recording kubectl, proving ordering and that a
# mutable/non-SHA image tag is rejected before any external action.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
export DEPLOY_TEST_LOG="$work/actions"
export REAL_KUBECTL="$(command -v kubectl)"

cat > "$work/roles" <<'SH'
#!/usr/bin/env bash
echo roles >> "$DEPLOY_TEST_LOG"
SH
cat > "$work/kubectl" <<'SH'
#!/usr/bin/env bash
if [ "${1:-}" = kustomize ]; then exec "$REAL_KUBECTL" "$@"; fi
args=" $* "
if [[ "$args" == *" delete job "* ]]; then echo delete-jobs >> "$DEPLOY_TEST_LOG"; exit 0; fi
if [[ "$args" == *" wait "* ]]; then echo wait-migrations >> "$DEPLOY_TEST_LOG"; exit 0; fi
if [[ "$args" == *" apply -f - "* ]]; then
  payload="$(mktemp)"
  tee "$payload" >/dev/null
  if grep -q 'kind: Job' "$payload"; then echo apply-migrations >> "$DEPLOY_TEST_LOG";
  else echo apply-services >> "$DEPLOY_TEST_LOG"; fi
  rm -f "$payload"
  exit 0
fi
if [[ "$args" == *" rollout status "* ]]; then echo rollout-status >> "$DEPLOY_TEST_LOG"; exit 0; fi
echo "unexpected kubectl call: $*" >&2
exit 1
SH
chmod +x "$work/roles" "$work/kubectl"

cd "$repo_root"
IMAGE_PREFIX=ghcr.io/example/prod-commerce IMAGE_TAG=sha-0123456789abcdef \
  KUBECTL_BIN="$work/kubectl" ROLE_MIGRATION_BIN="$work/roles" \
  ops/deploy/release.sh staging >/dev/null

expected=$'roles\ndelete-jobs\napply-migrations\nwait-migrations\napply-services\nrollout-status'
[ "$(cat "$DEPLOY_TEST_LOG")" = "$expected" ] || {
  echo "FAIL: release actions ran out of order" >&2
  cat "$DEPLOY_TEST_LOG" >&2
  exit 1
}

: > "$DEPLOY_TEST_LOG"
if IMAGE_PREFIX=ghcr.io/example/prod-commerce IMAGE_TAG=latest \
    KUBECTL_BIN="$work/kubectl" ROLE_MIGRATION_BIN="$work/roles" \
    ops/deploy/release.sh staging >/dev/null 2>&1; then
  echo "FAIL: mutable image tag was accepted" >&2
  exit 1
fi
[ ! -s "$DEPLOY_TEST_LOG" ] || { echo "FAIL: invalid release performed external actions" >&2; exit 1; }

echo "Verified ordered deployment and pre-action rejection of mutable tags"
