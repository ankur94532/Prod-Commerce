#!/usr/bin/env bash
# Takes a consistent logical backup of every service database and records a manifest with
# checksums. Logical dumps give you a restore point, not point-in-time recovery: continuous
# archiving (WAL shipping) is what bounds data loss to seconds, and this repository does
# not configure it. See docs/runbooks/backup-restore.md.
set -euo pipefail

: "${PGHOST:?set PGHOST}"
: "${PGUSER:?set PGUSER}"
: "${PGPASSWORD:?set PGPASSWORD}"
PGPORT="${PGPORT:-5432}"
DESTINATION="${1:-./backups/$(date -u +%Y%m%dT%H%M%SZ)}"
DATABASES="${DATABASES:-ecom_auth ecom_catalog ecom_order ecom_analytics ecom_recommendation}"

# Use the local client when there is one, otherwise a pinned image, so the same script
# runs on a workstation and on a jump host without a PostgreSQL client installed.
if command -v pg_dump >/dev/null 2>&1; then
  run_pg_dump() { pg_dump "$@"; }
  run_psql() { psql "$@"; }
else
  DUMP_HOST="$PGHOST"
  [ "$DUMP_HOST" = "127.0.0.1" ] || [ "$DUMP_HOST" = "localhost" ] && DUMP_HOST="host.docker.internal"
  run_pg_dump() {
    docker run --rm -i --add-host=host.docker.internal:host-gateway \
      -e PGPASSWORD="$PGPASSWORD" postgres:16 pg_dump "$@"
  }
  run_psql() {
    docker run --rm -i --add-host=host.docker.internal:host-gateway \
      -e PGPASSWORD="$PGPASSWORD" postgres:16 psql "$@"
  }
fi

mkdir -p "$DESTINATION"
manifest="$DESTINATION/manifest.txt"
: > "$manifest"
{
  echo "taken_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "host=$PGHOST:$PGPORT"
  echo "format=pg_dump custom (-Fc)"
  echo "scope=logical dump per database; not point-in-time recovery"
} >> "$manifest"

for database in $DATABASES; do
  archive="$DESTINATION/${database}.dump"
  # Stream to stdout so the container variant needs no shared volume.
  run_pg_dump --host "${DUMP_HOST:-$PGHOST}" --port "$PGPORT" --username "$PGUSER" \
    --format=custom --compress=6 "$database" > "$archive"
  checksum="$(shasum -a 256 "$archive" | awk '{print $1}')"
  size="$(wc -c < "$archive" | tr -d ' ')"
  # Recorded so the restore drill can prove the data arrived, not merely that pg_restore
  # exited zero. An empty database legitimately has zero tables.
  tables="$(run_psql --host "${DUMP_HOST:-$PGHOST}" --port "$PGPORT" --username "$PGUSER" \
    --dbname "$database" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" | tr -d '[:space:]')"
  echo "${database} sha256=${checksum} bytes=${size} tables=${tables}" >> "$manifest"
  echo "Backed up ${database} (${size} bytes, ${tables} tables)"
done

echo "Wrote $DESTINATION"
echo "A backup you have never restored is not a backup: run ops/backup/pg-restore-drill.sh"
