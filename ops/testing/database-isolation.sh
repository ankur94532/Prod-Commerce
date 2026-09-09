#!/usr/bin/env bash
# Proves the per-service database roles actually isolate: each service can use its own
# database and cannot connect to any other. Uses a disposable PostgreSQL; touches no
# project volumes or Compose services.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

container="$(docker run --rm -d \
  -e POSTGRES_USER=ecom_user -e POSTGRES_PASSWORD=isolation_bootstrap -e POSTGRES_DB=ecom_auth \
  -e AUTH_DB_PASSWORD=auth_pw -e CATALOG_DB_PASSWORD=catalog_pw -e ORDER_DB_PASSWORD=order_pw \
  -e ANALYTICS_DB_PASSWORD=analytics_pw -e RECOMMENDATION_DB_PASSWORD=recommendation_pw \
  -v "$repo_root/docker/postgres/init-databases.sh:/docker-entrypoint-initdb.d/init-databases.sh:ro" \
  -p 127.0.0.1::5432 postgres:16)"
trap 'docker stop "$container" >/dev/null' EXIT

for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U ecom_user -d ecom_auth >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -U ecom_user -d ecom_auth >/dev/null

# The init script runs once, in the background, after the server first accepts connections.
for _ in $(seq 1 60); do
  if docker exec "$container" psql -U ecom_user -d postgres -tAc \
      "SELECT 1 FROM pg_roles WHERE rolname='recommendation_service'" 2>/dev/null | grep -q 1; then break; fi
  sleep 1
done

checks=0
expect_allowed() {
  local role="$1" database="$2" password="$3"
  if docker exec -e PGPASSWORD="$password" "$container" \
      psql -U "$role" -d "$database" -tAc 'SELECT 1' >/dev/null 2>&1; then
    checks=$((checks + 1))
  else
    echo "FAIL: $role cannot use its own database $database" >&2
    exit 1
  fi
}
expect_denied() {
  local role="$1" database="$2" password="$3"
  if docker exec -e PGPASSWORD="$password" "$container" \
      psql -U "$role" -d "$database" -tAc 'SELECT 1' >/dev/null 2>&1; then
    echo "FAIL: $role reached $database, which belongs to another service" >&2
    exit 1
  else
    checks=$((checks + 1))
  fi
}

declare -a ROLES=(auth_service catalog_service order_service analytics_service recommendation_service)
declare -a DATABASES=(ecom_auth ecom_catalog ecom_order ecom_analytics ecom_recommendation)
declare -a PASSWORDS=(auth_pw catalog_pw order_pw analytics_pw recommendation_pw)

for i in "${!ROLES[@]}"; do
  for j in "${!DATABASES[@]}"; do
    if [ "$i" = "$j" ]; then
      expect_allowed "${ROLES[$i]}" "${DATABASES[$j]}" "${PASSWORDS[$i]}"
    else
      expect_denied "${ROLES[$i]}" "${DATABASES[$j]}" "${PASSWORDS[$i]}"
    fi
  done
done

# Each role must still be able to run migrations in its own schema.
docker exec -e PGPASSWORD=order_pw "$container" psql -U order_service -d ecom_order \
  -c 'CREATE TABLE isolation_probe (id int); DROP TABLE isolation_probe;' >/dev/null
checks=$((checks + 1))

echo "Verified $checks database isolation checks across ${#ROLES[@]} roles and ${#DATABASES[@]} databases"
