#!/usr/bin/env bash
set -euo pipefail
# Isolated, disposable PostgreSQL. No project volumes, Compose services, or app seeders.
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
container_id="$(docker run --rm -d -e POSTGRES_USER=checkout_test -e POSTGRES_PASSWORD=checkout_test -e POSTGRES_DB=checkout_test -p 127.0.0.1::5432 postgres:16)"
trap 'docker stop "$container_id" >/dev/null' EXIT
for attempt in {1..60}; do
  if docker exec "$container_id" pg_isready -U checkout_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container_id" pg_isready -U checkout_test >/dev/null
pg_port="$(docker port "$container_id" 5432/tcp | cut -d: -f2)"
export CHECKOUT_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${pg_port}/checkout_test"
test_started="$(python3 -c 'import time; print(time.time())')"
# -am pulls the shared platform-security module into the reactor; the modules it builds
# on the way have no matching tests, which is not a failure.
mvn -f "$repo_root/backend/pom.xml" -pl catalog-service,order-service,analytics-service,recommendation-service -am \
  -Dtest='*ReliabilityPostgresTest' -Dsurefire.failIfNoSpecifiedTests=false test "$@"
python3 "$repo_root/ops/testing/summarize-checkout-tests.py" "$test_started"
(cd "$repo_root/frontend" && node --test src/checkout/*.test.js)
