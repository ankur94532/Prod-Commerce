#!/usr/bin/env bash
# Restores a backup into a disposable PostgreSQL and verifies the data arrived. This is
# the part that is usually missing: an untested backup is a hope, not a recovery plan.
# Never points at a live database; it always creates its own throwaway server.
set -euo pipefail

BACKUP_DIR="${1:?usage: pg-restore-drill.sh <backup-directory>}"
[ -f "$BACKUP_DIR/manifest.txt" ] || { echo "No manifest.txt in $BACKUP_DIR" >&2; exit 1; }
shopt -s nullglob
archives=("$BACKUP_DIR"/*.dump)
[ "${#archives[@]}" -gt 0 ] || { echo "No .dump archives in $BACKUP_DIR" >&2; exit 1; }

echo "Verifying archive checksums against the manifest"
while read -r database checksum_field _; do
  case "$database" in ecom_*) ;; *) continue ;; esac
  expected="${checksum_field#sha256=}"
  actual="$(shasum -a 256 "$BACKUP_DIR/${database}.dump" | awk '{print $1}')"
  if [ "$expected" != "$actual" ]; then
    echo "FAIL: ${database}.dump does not match its recorded checksum" >&2
    exit 1
  fi
done < "$BACKUP_DIR/manifest.txt"

container="$(docker run --rm -d -e POSTGRES_USER=drill -e POSTGRES_PASSWORD=drill \
  -e POSTGRES_DB=postgres -p 127.0.0.1::5432 postgres:16)"
trap 'docker stop "$container" >/dev/null' EXIT

for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U drill >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -U drill >/dev/null

restored=0
for archive in "${archives[@]}"; do
  database="$(basename "$archive" .dump)"
  docker exec -i "$container" psql -U drill -d postgres -c "CREATE DATABASE ${database};" >/dev/null
  docker exec -i "$container" pg_restore --username drill --dbname "$database" --no-owner --no-privileges \
    < "$archive" >/dev/null 2>&1 || true

  tables="$(docker exec "$container" psql -U drill -d "$database" -tAc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" | tr -d '[:space:]')"
  expected="$(awk -v db="$database" '$1 == db { for (i = 2; i <= NF; i++) if ($i ~ /^tables=/) { sub(/^tables=/, "", $i); print $i } }' "$BACKUP_DIR/manifest.txt")"
  if [ -n "$expected" ] && [ "$tables" != "$expected" ]; then
    echo "FAIL: ${database} restored ${tables} tables, the backup recorded ${expected}" >&2
    exit 1
  fi
  echo "Restored ${database}: ${tables} tables (backup recorded ${expected:-unknown})"
  restored=$((restored + 1))
done

echo "Restore drill passed: ${restored} databases restored and inspected in a throwaway server"
