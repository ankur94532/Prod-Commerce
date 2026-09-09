#!/usr/bin/env bash
# Emits the ConfigMap the backup CronJob mounts, built from the scripts that
# ops/testing/backup-retention.sh actually tests. Generating it means the scheduled job and
# the tested code cannot drift apart, which a hand-pasted ConfigMap guarantees they will.
#
#   ops/backup/render-scripts-configmap.sh | kubectl apply -f -
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

kubectl create configmap gocommerce-backup-scripts \
  --namespace gocommerce \
  --from-file="$repo_root/ops/backup/pg-backup.sh" \
  --from-file="$repo_root/ops/backup/prune-archive.sh" \
  --dry-run=client -o yaml
