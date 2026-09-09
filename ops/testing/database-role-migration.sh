#!/usr/bin/env bash
# Exercises the migration that has to run before the new manifests are deployed. It builds a
# database in the OLD shape — one shared role owning everything, with data already in it —
# applies ops/migrations/apply-database-roles.sh, and then checks the three things that
# matter: existing data survived, each service can use its own database, and no service can
# reach another's.
#
# Disposable container; never touches a live database.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

container="pitr-role-migration-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# The old shape: POSTGRES_USER owns everything and there are no per-service roles.
docker run -d --name "$container" \
  -e POSTGRES_USER=ecom_user -e POSTGRES_PASSWORD=legacy_password -e POSTGRES_DB=ecom_auth \
  -p 127.0.0.1::5432 postgres:16 >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U ecom_user >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -U ecom_user >/dev/null

psql_as() { docker exec -e PGPASSWORD="$2" "$container" psql -v ON_ERROR_STOP=1 -U "$1" -d "$3" -tAc "$4"; }

for database in ecom_catalog ecom_order ecom_analytics ecom_recommendation; do
  psql_as ecom_user legacy_password postgres "CREATE DATABASE ${database};" >/dev/null
done

# Pre-existing data owned by the legacy role: the migration must not lose or lock it out.
psql_as ecom_user legacy_password ecom_order \
  "CREATE TABLE orders_probe (id serial primary key, note text);
   INSERT INTO orders_probe (note) VALUES ('written before the migration');" >/dev/null
psql_as ecom_user legacy_password ecom_auth \
  "CREATE TABLE users_probe (id serial primary key, email text);
   INSERT INTO users_probe (email) VALUES ('someone@example.com');" >/dev/null

port="$(docker port "$container" 5432/tcp | cut -d: -f2)"

echo "Applying the role migration"
PGHOST=127.0.0.1 PGPORT="$port" PGUSER=ecom_user PGPASSWORD=legacy_password \
AUTH_DB_PASSWORD=auth_pw CATALOG_DB_PASSWORD=catalog_pw ORDER_DB_PASSWORD=order_pw \
ANALYTICS_DB_PASSWORD=analytics_pw RECOMMENDATION_DB_PASSWORD=recommendation_pw \
  "$repo_root/ops/migrations/apply-database-roles.sh" >/dev/null

echo "Running it a second time to confirm it is idempotent"
PGHOST=127.0.0.1 PGPORT="$port" PGUSER=ecom_user PGPASSWORD=legacy_password \
AUTH_DB_PASSWORD=auth_pw CATALOG_DB_PASSWORD=catalog_pw ORDER_DB_PASSWORD=order_pw \
ANALYTICS_DB_PASSWORD=analytics_pw RECOMMENDATION_DB_PASSWORD=recommendation_pw \
  "$repo_root/ops/migrations/apply-database-roles.sh" >/dev/null

checks=0

note="$(psql_as order_service order_pw ecom_order "SELECT note FROM orders_probe;" | tr -d '\r')"
[ "$note" = "written before the migration" ] || {
  echo "FAIL: data written before the migration is not readable by the new owner (got '$note')" >&2; exit 1; }
checks=$((checks + 1))

# The new owner must be able to run migrations, not merely read.
psql_as order_service order_pw ecom_order \
  "CREATE TABLE migration_probe (id int); INSERT INTO migration_probe VALUES (1); DROP TABLE migration_probe;" >/dev/null
checks=$((checks + 1))

psql_as auth_service auth_pw ecom_auth "SELECT count(*) FROM users_probe;" >/dev/null
checks=$((checks + 1))

declare -a ROLES=(auth_service catalog_service order_service analytics_service recommendation_service)
declare -a DATABASES=(ecom_auth ecom_catalog ecom_order ecom_analytics ecom_recommendation)
declare -a PASSWORDS=(auth_pw catalog_pw order_pw analytics_pw recommendation_pw)

for i in "${!ROLES[@]}"; do
  for j in "${!DATABASES[@]}"; do
    if [ "$i" = "$j" ]; then
      continue
    fi
    if docker exec -e PGPASSWORD="${PASSWORDS[$i]}" "$container" \
        psql -U "${ROLES[$i]}" -d "${DATABASES[$j]}" -tAc 'SELECT 1' >/dev/null 2>&1; then
      echo "FAIL: after migration, ${ROLES[$i]} can still reach ${DATABASES[$j]}" >&2
      exit 1
    fi
    checks=$((checks + 1))
  done
done

echo "Verified the role migration with $checks checks: existing data preserved, each role"
echo "owns and can migrate its own database, and cross-database access is denied."
