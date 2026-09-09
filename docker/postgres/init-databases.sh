#!/bin/bash
# Every service used to connect as the same role, so a flaw in any one service reached
# every other service's data: analytics could read password hashes, the recommendation
# projector could write orders. Each service now owns exactly one database and cannot
# connect to the others.
#
# This is still a single PostgreSQL instance: one failure domain, one backup, one upgrade
# window for all five databases. Separate instances are the production shape; the
# per-service URLs in the ConfigMap already make that a configuration change.
# See docs/PRODUCTION-READINESS.md.
set -euo pipefail

required() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "init-databases.sh: $name must be set; refusing to create a role with a blank password" >&2
    exit 1
  fi
}
for variable in AUTH_DB_PASSWORD CATALOG_DB_PASSWORD ORDER_DB_PASSWORD ANALYTICS_DB_PASSWORD RECOMMENDATION_DB_PASSWORD; do
  required "$variable"
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE DATABASE ecom_catalog;
CREATE DATABASE ecom_order;
CREATE DATABASE ecom_analytics;
CREATE DATABASE ecom_recommendation;

CREATE ROLE auth_service LOGIN PASSWORD '${AUTH_DB_PASSWORD}';
CREATE ROLE catalog_service LOGIN PASSWORD '${CATALOG_DB_PASSWORD}';
CREATE ROLE order_service LOGIN PASSWORD '${ORDER_DB_PASSWORD}';
CREATE ROLE analytics_service LOGIN PASSWORD '${ANALYTICS_DB_PASSWORD}';
CREATE ROLE recommendation_service LOGIN PASSWORD '${RECOMMENDATION_DB_PASSWORD}';

-- One owner per database. Flyway needs DDL, so each role owns its own schema and nothing
-- else. PUBLIC connect rights are revoked so a role cannot open another database at all.
ALTER DATABASE ecom_auth OWNER TO auth_service;
ALTER DATABASE ecom_catalog OWNER TO catalog_service;
ALTER DATABASE ecom_order OWNER TO order_service;
ALTER DATABASE ecom_analytics OWNER TO analytics_service;
ALTER DATABASE ecom_recommendation OWNER TO recommendation_service;

REVOKE CONNECT ON DATABASE ecom_auth FROM PUBLIC;
REVOKE CONNECT ON DATABASE ecom_catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE ecom_order FROM PUBLIC;
REVOKE CONNECT ON DATABASE ecom_analytics FROM PUBLIC;
REVOKE CONNECT ON DATABASE ecom_recommendation FROM PUBLIC;

GRANT CONNECT ON DATABASE ecom_auth TO auth_service;
GRANT CONNECT ON DATABASE ecom_catalog TO catalog_service;
GRANT CONNECT ON DATABASE ecom_order TO order_service;
GRANT CONNECT ON DATABASE ecom_analytics TO analytics_service;
GRANT CONNECT ON DATABASE ecom_recommendation TO recommendation_service;
SQL

for pair in "ecom_auth:auth_service" "ecom_catalog:catalog_service" "ecom_order:order_service" \
            "ecom_analytics:analytics_service" "ecom_recommendation:recommendation_service"; do
  database="${pair%%:*}"
  role="${pair#*:}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" \
    -c "ALTER SCHEMA public OWNER TO ${role};"
done

echo "init-databases.sh: five databases created, each owned by its own least-privilege role"
