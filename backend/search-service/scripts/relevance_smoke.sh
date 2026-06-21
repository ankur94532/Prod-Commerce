#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COUNT="${COUNT:-500}"
FAIL_UNDER="${FAIL_UNDER:-0.95}"

python3 "$(dirname "$0")/relevance_eval.py" \
  --base-url "$BASE_URL" \
  --count "$COUNT" \
  --workers 4 \
  --fail-under "$FAIL_UNDER"
