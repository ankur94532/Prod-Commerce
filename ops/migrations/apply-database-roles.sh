#!/usr/bin/env bash
# Applies the per-service database roles to a PostgreSQL instance that was already
# initialised with the single shared role. docker/postgres/init-databases.sh only runs on a
# first-time initialisation, so an existing data directory needs this.
#
# Deploy order matters: run this BEFORE rolling out the manifests that use the new roles,
# or every pod will start and fail to authenticate.
#
# Idempotent: roles that already exist are updated rather than recreated, so a partially
# applied run can be finished by running it again.
#
# Usage:
#   PGHOST=... PGPORT=5432 PGUSER=postgres PGPASSWORD=... \
#   AUTH_DB_PASSWORD=... CATALOG_DB_PASSWORD=... ORDER_DB_PASSWORD=... \
#   ANALYTICS_DB_PASSWORD=... RECOMMENDATION_DB_PASSWORD=... \
#   ops/migrations/apply-database-roles.sh
set -euo pipefail

: "${PGHOST:?set PGHOST}"
: "${PGUSER:?set PGUSER (a superuser)}"
: "${PGPASSWORD:?set PGPASSWORD}"
PGPORT="${PGPORT:-5432}"
LEGACY_ROLE="${LEGACY_ROLE:-ecom_user}"

for variable in AUTH_DB_PASSWORD CATALOG_DB_PASSWORD ORDER_DB_PASSWORD ANALYTICS_DB_PASSWORD RECOMMENDATION_DB_PASSWORD; do
  if [ -z "${!variable:-}" ]; then
    echo "$variable must be set; refusing to create a role with a blank password" >&2
    exit 1
  fi
done

if command -v psql >/dev/null 2>&1; then
  run_psql() { psql -v ON_ERROR_STOP=1 --host "$PGHOST" --port "$PGPORT" --username "$PGUSER" "$@"; }
else
  DOCKER_HOST_ALIAS="$PGHOST"
  case "$PGHOST" in
    127.0.0.1|localhost) DOCKER_HOST_ALIAS="host.docker.internal" ;;
  esac
  run_psql() {
    docker run --rm -i --add-host=host.docker.internal:host-gateway -e PGPASSWORD="$PGPASSWORD" \
      "${POSTGRES_IMAGE:-postgres:16}" \
      psql -v ON_ERROR_STOP=1 --host "$DOCKER_HOST_ALIAS" --port "$PGPORT" --username "$PGUSER" "$@"
  }
fi

declare -a PAIRS=(
  "auth_service:ecom_auth:AUTH_DB_PASSWORD"
  "catalog_service:ecom_catalog:CATALOG_DB_PASSWORD"
  "order_service:ecom_order:ORDER_DB_PASSWORD"
  "analytics_service:ecom_analytics:ANALYTICS_DB_PASSWORD"
  "recommendation_service:ecom_recommendation:RECOMMENDATION_DB_PASSWORD"
)

echo "Creating or updating the per-service roles"
for pair in "${PAIRS[@]}"; do
  IFS=: read -r role database password_variable <<< "$pair"
  password_value="${!password_variable}"

  run_psql --dbname postgres <<SQL
DO \$\$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role}') THEN
    EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${role}', '${password_value}');
  ELSE
    EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${role}', '${password_value}');
  END IF;
END
\$\$;

ALTER DATABASE ${database} OWNER TO ${role};
REVOKE CONNECT ON DATABASE ${database} FROM PUBLIC;
GRANT CONNECT ON DATABASE ${database} TO ${role};
SQL
done

echo "Handing existing objects to their new owners"
for pair in "${PAIRS[@]}"; do
  IFS=: read -r role database _ <<< "$pair"
  run_psql --dbname "$database" <<SQL
ALTER SCHEMA public OWNER TO ${role};

-- Ownership is transferred object by object rather than with REASSIGN OWNED, which fails
-- outright when the legacy role also owns objects the database system requires (it usually
-- does, because the legacy role is often the bootstrap superuser).
DO \$\$
DECLARE
  target CONSTANT text := '${role}';
  item record;
BEGIN
  FOR item IN SELECT tablename AS name FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I OWNER TO %I', item.name, target);
  END LOOP;
  FOR item IN SELECT sequencename AS name FROM pg_sequences WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER SEQUENCE public.%I OWNER TO %I', item.name, target);
  END LOOP;
  FOR item IN SELECT viewname AS name FROM pg_views WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER VIEW public.%I OWNER TO %I', item.name, target);
  END LOOP;
  FOR item IN SELECT matviewname AS name FROM pg_matviews WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER MATERIALIZED VIEW public.%I OWNER TO %I', item.name, target);
  END LOOP;
END
\$\$;

GRANT ALL ON ALL TABLES IN SCHEMA public TO ${role};
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO ${role};
SQL
done

echo "Done. Verify with ops/testing/database-isolation.sh against a copy before"
echo "pointing services at the new credentials."
