#!/usr/bin/env bash
# Proves the migrate profile behaves the way a Kubernetes Job needs it to: it applies the
# schema, reports what it applied, and exits. It also proves the other half of the change --
# that a service started with Flyway disabled against an un-migrated database refuses to
# start rather than serving against a schema its entities do not match.
#
# Disposable PostgreSQL; never touches a live database.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

container="migration-job-check-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$container" \
  -e POSTGRES_USER=order_service -e POSTGRES_PASSWORD=order_pw -e POSTGRES_DB=ecom_order \
  -p 127.0.0.1::5432 postgres:16 >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U order_service >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -U order_service >/dev/null

port="$(docker port "$container" 5432/tcp | cut -d: -f2)"
url="jdbc:postgresql://127.0.0.1:${port}/ecom_order"

jar="$(ls backend/order-service/target/order-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -z "$jar" ]; then
  mvn -f backend/pom.xml -pl order-service -am -o -q package -DskipTests
  jar="$(ls backend/order-service/target/order-service-*.jar | grep -v sources | head -1 || true)"
fi

# Exported rather than passed through env(1), because the runner below is a shell function.
export SPRING_DATASOURCE_URL="$url"
export SPRING_DATASOURCE_USERNAME=order_service
export SPRING_DATASOURCE_PASSWORD=order_pw
export SECURITY_JWT_SECRET=migration_check_secret_of_at_least_32_chars
export INTERNAL_SERVICE_TOKEN=migration_check_internal_token_value
export SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:1
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0

# GNU timeout is not present on macOS, and a missing command exits 127, which a naive
# "non-zero means it refused" check would read as a pass. Run it ourselves instead.
run_limited() {
  local seconds="$1"
  shift
  ( "$@" ) &
  local pid=$!
  local waited=0
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge "$seconds" ]; then
      kill -9 "$pid" 2>/dev/null
      wait "$pid" 2>/dev/null
      return 124
    fi
    sleep 1
    waited=$((waited + 1))
  done
  wait "$pid"
}

table_count() {
  docker exec "$container" psql -U order_service -d ecom_order -tAc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" | tr -d '[:space:]'
}

echo "1. A service starting with migrations disabled against an empty schema must refuse"
set +e
export SPRING_FLYWAY_ENABLED=false
run_limited 150 java -jar "$jar" --server.port=0 --spring.main.web-application-type=none \
  > /tmp/migration-check-unmigrated.log 2>&1
unmigrated_status=$?
set -e
if [ "$unmigrated_status" -eq 0 ]; then
  echo "FAIL: the service started cleanly against an un-migrated database" >&2
  exit 1
fi
# Assert WHY it failed. Any non-zero exit would otherwise pass this step, including a
# missing command or a typo in the datasource settings.
if ! grep -qiE "Schema-validation|missing table|SchemaManagementException" /tmp/migration-check-unmigrated.log; then
  echo "FAIL: the service failed, but not because the schema was missing" >&2
  tail -25 /tmp/migration-check-unmigrated.log >&2
  exit 1
fi
if [ "$(table_count)" != "0" ]; then
  echo "FAIL: a service with Flyway disabled created tables anyway" >&2
  exit 1
fi
echo "   refused with a schema-validation error, and created nothing (exit ${unmigrated_status})"

echo "2. The migrate profile applies the schema and exits"
start=$(date +%s)
export SPRING_FLYWAY_ENABLED=true
run_limited 240 java -jar "$jar" --spring.profiles.active=migrate \
  > /tmp/migration-check-migrate.log 2>&1
elapsed=$(( $(date +%s) - start ))
echo "   exited 0 after ${elapsed}s"

applied="$(docker exec "$container" psql -U order_service -d ecom_order -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success" | tr -d '[:space:]')"
if [ "${applied:-0}" -lt 1 ]; then
  echo "FAIL: no migrations were recorded" >&2
  tail -30 /tmp/migration-check-migrate.log >&2
  exit 1
fi
if ! grep -q "Schema is at version" /tmp/migration-check-migrate.log; then
  echo "FAIL: the job did not report the schema version it left behind" >&2
  exit 1
fi
echo "   ${applied} migrations applied; version reported in the job log"

echo "3. Re-running the job is a no-op rather than an error"
export SPRING_FLYWAY_ENABLED=true
run_limited 240 java -jar "$jar" --spring.profiles.active=migrate \
  > /tmp/migration-check-repeat.log 2>&1
repeat="$(docker exec "$container" psql -U order_service -d ecom_order -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success" | tr -d '[:space:]')"
if [ "$repeat" != "$applied" ]; then
  echo "FAIL: a repeated migration job changed the schema history ($applied -> $repeat)" >&2
  exit 1
fi
echo "   history unchanged at ${repeat} migrations"

echo "4. With the schema in place, the service starts with migrations still disabled"
set +e
export SPRING_FLYWAY_ENABLED=false
java -jar "$jar" --server.port=0 \
  > /tmp/migration-check-started.log 2>&1 &
app_pid=$!
started=0
for _ in $(seq 1 60); do
  if grep -q "Started OrderServiceApplication" /tmp/migration-check-started.log 2>/dev/null; then started=1; break; fi
  if ! kill -0 "$app_pid" 2>/dev/null; then break; fi
  sleep 1
done
kill "$app_pid" 2>/dev/null
wait "$app_pid" 2>/dev/null
set -e
if [ "$started" -ne 1 ]; then
  echo "FAIL: the service did not start against the migrated schema" >&2
  tail -30 /tmp/migration-check-started.log >&2
  exit 1
fi
echo "   started against the migrated schema"

echo "Verified the migration job: schema applied out of band, repeatable, and a service"
echo "started against an un-migrated database refuses rather than limping."
