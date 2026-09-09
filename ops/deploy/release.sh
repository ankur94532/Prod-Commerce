#!/usr/bin/env bash
# Ordered release: roles -> schema jobs -> progressive service rollout. On rollout failure,
# undo every deployment changed by this release. Requires backward-compatible migrations.
set -euo pipefail

environment="${1:-}"
case "$environment" in
  staging)
    namespace=gocommerce-staging
    service_overlay=deploy/overlays/staging
    migration_overlay=deploy/overlays/staging-migrations
    ;;
  production)
    namespace=gocommerce
    service_overlay=deploy/overlays/production
    migration_overlay=deploy/overlays/production-migrations
    ;;
  *) echo "usage: $0 staging|production" >&2; exit 2 ;;
esac

: "${IMAGE_PREFIX:?set IMAGE_PREFIX, for example ghcr.io/acme/prod-commerce}"
: "${IMAGE_TAG:?set immutable IMAGE_TAG, for example sha-0123456789abcdef}"
[[ "$IMAGE_PREFIX" =~ ^[a-z0-9./_-]+$ ]] || { echo "invalid IMAGE_PREFIX" >&2; exit 2; }
[[ "$IMAGE_TAG" =~ ^sha-[0-9a-f]{7,40}$ ]] || { echo "IMAGE_TAG must be an immutable sha-* tag" >&2; exit 2; }

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"
kubectl_bin="${KUBECTL_BIN:-kubectl}"
role_migration="${ROLE_MIGRATION_BIN:-ops/migrations/apply-database-roles.sh}"

render() {
  "$kubectl_bin" kustomize "$1" | sed \
    -e "s#ghcr.io/OWNER/REPOSITORY#${IMAGE_PREFIX}#g" \
    -e "s#sha-REPLACE_ME#${IMAGE_TAG}#g"
}

echo "1/4 ensuring per-service database roles exist"
"$role_migration"

echo "2/4 applying schema migrations"
"$kubectl_bin" -n "$namespace" delete job -l app.kubernetes.io/component=migration --ignore-not-found
render "$migration_overlay" | "$kubectl_bin" apply -f -
"$kubectl_bin" -n "$namespace" wait --for=condition=complete job \
  -l app.kubernetes.io/component=migration --timeout=10m

echo "3/4 applying ${environment} services with zero-unavailable rolling updates"
render "$service_overlay" | "$kubectl_bin" apply -f -

echo "4/4 waiting for rollouts"
if ! "$kubectl_bin" -n "$namespace" rollout status deployment --all --timeout=10m; then
  echo "Rollout failed; undoing changed deployments" >&2
  while IFS= read -r deployment; do
    "$kubectl_bin" -n "$namespace" rollout undo "$deployment" || true
  done < <("$kubectl_bin" -n "$namespace" get deployment -o name)
  exit 1
fi
echo "Release ${IMAGE_TAG} completed in ${namespace}"
