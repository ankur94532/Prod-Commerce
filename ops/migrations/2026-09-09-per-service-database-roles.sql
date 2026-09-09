-- Applies the per-service database roles to a PostgreSQL instance that was already
-- initialised with the single shared ecom_user role. The init script in
-- docker/postgres/init-databases.sh only runs on a first-time initialisation, so an
-- existing volume needs this.
--
-- Run as a superuser, connected to the "postgres" database. Set the five passwords first:
--   psql -v auth_pw="'...'" -v catalog_pw="'...'" -v order_pw="'...'" \
--        -v analytics_pw="'...'" -v recommendation_pw="'...'" \
--        -f ops/migrations/2026-09-09-per-service-database-roles.sql
--
-- Deploy order matters: create the roles BEFORE rolling out the manifests that use them,
-- otherwise pods start and fail to authenticate.

BEGIN;

CREATE ROLE auth_service LOGIN PASSWORD :auth_pw;
CREATE ROLE catalog_service LOGIN PASSWORD :catalog_pw;
CREATE ROLE order_service LOGIN PASSWORD :order_pw;
CREATE ROLE analytics_service LOGIN PASSWORD :analytics_pw;
CREATE ROLE recommendation_service LOGIN PASSWORD :recommendation_pw;

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

COMMIT;

-- Then, connected to EACH database in turn, hand over the existing objects. Run these
-- separately; \connect cannot be used inside a transaction block above.
--
--   \connect ecom_auth
--   ALTER SCHEMA public OWNER TO auth_service;
--   REASSIGN OWNED BY ecom_user TO auth_service;
--
--   \connect ecom_catalog
--   ALTER SCHEMA public OWNER TO catalog_service;
--   REASSIGN OWNED BY ecom_user TO catalog_service;
--
--   \connect ecom_order
--   ALTER SCHEMA public OWNER TO order_service;
--   REASSIGN OWNED BY ecom_user TO order_service;
--
--   \connect ecom_analytics
--   ALTER SCHEMA public OWNER TO analytics_service;
--   REASSIGN OWNED BY ecom_user TO analytics_service;
--
--   \connect ecom_recommendation
--   ALTER SCHEMA public OWNER TO recommendation_service;
--   REASSIGN OWNED BY ecom_user TO recommendation_service;
--
-- Verify with ops/testing/database-isolation.sh against a copy before doing this in place.
