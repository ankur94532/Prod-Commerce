#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COUNT="${COUNT:-10000}"
MODE="${MODE:-}"
WORKERS="${WORKERS:-8}"
FAIL_UNDER="${FAIL_UNDER:-}"
OUTPUT_DIR="${OUTPUT_DIR:-backend/search-service/scripts}"

mkdir -p "${OUTPUT_DIR}"

args=(
  python3 backend/search-service/scripts/relevance_eval.py
  --base-url "${BASE_URL}"
  --count "${COUNT}"
  --workers "${WORKERS}"
  --dump-queries "${OUTPUT_DIR}/generated-queries.jsonl"
  --output-json "${OUTPUT_DIR}/relevance-results.json"
  --output-md "${OUTPUT_DIR}/relevance-results.md"
)

if [[ -n "${MODE}" ]]; then
  args+=(--mode "${MODE}")
fi

if [[ -n "${FAIL_UNDER}" ]]; then
  args+=(--fail-under "${FAIL_UNDER}")
fi

"${args[@]}"
