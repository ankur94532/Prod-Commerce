#!/usr/bin/env bash
# Emits the ConfigMap the PostgreSQL StatefulSet mounts at /docker-entrypoint-initdb.d,
# built from the same docker/postgres/init-databases.sh that Compose uses and that
# ops/testing/database-isolation.sh tests.
#
# Generated rather than pasted, for the reason the backup renderer exists: two copies of a
# schema-bootstrapping script drift, and the copy that drifts is the one nobody runs until
# a cluster is being rebuilt under pressure. One source, two consumers.
#
#   ops/k8s/render-postgres-init-configmap.sh | kubectl apply -f -
#   kubectl apply -k k8s/data-tier
#
# The script creates five databases and five least-privilege roles and reads their passwords
# from the environment; the StatefulSet supplies those from the per-service Secrets.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
namespace="${NAMESPACE:-gocommerce}"

source_script="$repo_root/docker/postgres/init-databases.sh"
[ -f "$source_script" ] || { echo "missing $source_script" >&2; exit 1; }

kubectl create configmap gocommerce-postgres-init \
  --namespace "$namespace" \
  --from-file="$source_script" \
  --dry-run=client -o yaml
