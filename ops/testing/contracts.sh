#!/usr/bin/env bash
# Consumer-driven compatibility checks for the four hand-written HTTP DTO boundaries.
# Java controllers/clients run in-process; the Python provider runs in a disposable
# container with a fake deterministic model. No live service, cluster, or project volume is
# modified.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

mvn -f backend/pom.xml \
  -pl order-service,catalog-service,search-service,recommendation-service -am \
  -Dtest='*ContractTest' -Dsurefire.failIfNoSpecifiedTests=false test -q

docker run --rm \
  -e PIP_ROOT_USER_ACTION=ignore \
  -v "$repo_root:/workspace:ro" \
  -w /workspace/backend/embedding-service \
  python:3.12.14-slim \
  sh -c 'python -m pip install --disable-pip-version-check --no-cache-dir -q -r requirements-contract.txt && python -m unittest -v test_contract.py'

echo "Verified 4 consumer/provider HTTP contracts (8 independently exercised sides)"
