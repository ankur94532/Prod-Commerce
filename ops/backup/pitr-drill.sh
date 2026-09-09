#!/usr/bin/env bash
# Proves point-in-time recovery end to end, which logical dumps cannot give you: a nightly
# pg_dump means losing everything since the dump ran. This takes a base backup, keeps
# archiving write-ahead log segments, then restores to a chosen instant and checks that the
# data present is exactly the data that existed at that instant.
#
# Uses disposable containers and a temporary directory. It never touches a live database.
set -euo pipefail

image="${POSTGRES_IMAGE:-postgres:16}"
work="$(mktemp -d)"
primary="pitr-primary-$$"
restored="pitr-restored-$$"

cleanup() {
  docker rm -f "$primary" "$restored" >/dev/null 2>&1 || true
  # The container writes as its own uid; remove through a container to avoid permission errors.
  docker run --rm -v "$work:/work" "$image" rm -rf /work/data /work/archive /work/basebackup >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

mkdir -p "$work/data" "$work/archive" "$work/basebackup"
chmod 777 "$work" "$work/data" "$work/archive" "$work/basebackup"

echo "1. Starting a primary with write-ahead log archiving enabled"
docker run -d --name "$primary" \
  -e POSTGRES_USER=pitr -e POSTGRES_PASSWORD=pitr -e POSTGRES_DB=pitr \
  -v "$work/archive:/archive" \
  "$image" \
  -c wal_level=replica \
  -c archive_mode=on \
  -c "archive_command=test ! -f /archive/%f && cp %p /archive/%f" \
  -c archive_timeout=5 \
  -c max_wal_senders=3 \
  -c wal_keep_size=64 >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$primary" pg_isready -U pitr >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$primary" pg_isready -U pitr >/dev/null

run_sql() { docker exec -e PGPASSWORD=pitr "$primary" psql -U pitr -d pitr -tAc "$1"; }

run_sql "CREATE TABLE orders_probe (id int primary key, note text);" >/dev/null
run_sql "INSERT INTO orders_probe VALUES (1, 'before the base backup');" >/dev/null

echo "2. Taking a base backup"
docker exec -e PGPASSWORD=pitr "$primary" \
  pg_basebackup -U pitr -h 127.0.0.1 -D /tmp/basebackup -Fp -Xs -P >/dev/null 2>&1
docker cp "$primary:/tmp/basebackup/." "$work/basebackup/" >/dev/null

echo "3. Writing more data, then recording the recovery target"
run_sql "INSERT INTO orders_probe VALUES (2, 'kept: written before the target');" >/dev/null
sleep 2
target="$(run_sql "SELECT now();" | tr -d '\r')"
sleep 2
run_sql "INSERT INTO orders_probe VALUES (3, 'discarded: written after the target');" >/dev/null
run_sql "DROP TABLE orders_probe;" >/dev/null   # the accident we are recovering from

# Force the segment holding those changes into the archive.
run_sql "SELECT pg_switch_wal();" >/dev/null
sleep 6
docker stop "$primary" >/dev/null

archived="$(find "$work/archive" -type f ! -name '*.backup' ! -name '*.history' | wc -l | tr -d ' ')"
if [ "$archived" -lt 1 ]; then
  echo "FAIL: no write-ahead log segments reached the archive; archive_command is not working" >&2
  exit 1
fi
echo "   ${archived} archived segments"

echo "4. Restoring to ${target}"
docker run --rm -v "$work/basebackup:/restore" -v "$work/archive:/archive" "$image" bash -c "
  set -e
  touch /restore/recovery.signal
  printf \"restore_command = 'cp /archive/%%f %%p'\nrecovery_target_time = '%s'\nrecovery_target_action = 'promote'\n\" '$target' >> /restore/postgresql.auto.conf
  chown -R postgres:postgres /restore
  chmod 700 /restore
" >/dev/null

docker run -d --name "$restored" \
  -v "$work/basebackup:/var/lib/postgresql/data" \
  -v "$work/archive:/archive" \
  -e POSTGRES_USER=pitr -e POSTGRES_PASSWORD=pitr \
  --user postgres \
  "$image" >/dev/null

recovered=0
for _ in $(seq 1 90); do
  if docker exec "$restored" pg_isready -U pitr -d pitr >/dev/null 2>&1; then recovered=1; break; fi
  sleep 1
done
if [ "$recovered" -ne 1 ]; then
  echo "FAIL: the restored server never finished recovery" >&2
  docker logs "$restored" 2>&1 | tail -25 >&2
  exit 1
fi

echo "5. Checking the recovered state"
rows="$(docker exec -e PGPASSWORD=pitr "$restored" psql -U pitr -d pitr -tAc \
  "SELECT string_agg(id::text, ',' ORDER BY id) FROM orders_probe;" | tr -d '[:space:]')"

if [ "$rows" != "1,2" ]; then
  echo "FAIL: recovered rows were '${rows}', expected '1,2'" >&2
  echo "      (row 3 and the DROP happened after the recovery target and must not be present)" >&2
  exit 1
fi

echo "Point-in-time recovery verified: the table dropped after the target is present again,"
echo "rows written before the target survived, and the row written after it did not."
