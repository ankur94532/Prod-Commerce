#!/usr/bin/env bash
# Checks the retention policy, in particular the property that makes it safe: write-ahead
# log segments still needed by a base backup we are keeping must survive pruning. Getting
# that wrong destroys recoverability quietly -- the backups are still there, and they no
# longer restore.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

backups="$work/backups"
archive="$work/archive"
mkdir -p "$backups" "$archive"

# Ten base backups, oldest first.
for day in 01 02 03 04 05 06 07 08 09 10; do
  mkdir -p "$backups/202609${day}T000000Z"
  echo "dump" > "$backups/202609${day}T000000Z/ecom_order.dump"
done

# Segments spanning the whole period, with a backup label anchoring the fourth.
for n in $(seq -w 1 20); do
  printf 'wal' > "$archive/0000000100000000000000${n}"
done
printf 'label' > "$archive/000000010000000000000004.00000028.backup"

checks=0
expect_present() {
  [ -e "$1" ] || { echo "FAIL: $2 was removed but is still needed: $1" >&2; exit 1; }
  checks=$((checks + 1))
}
expect_absent() {
  [ ! -e "$1" ] || { echo "FAIL: $2 should have been pruned: $1" >&2; exit 1; }
  checks=$((checks + 1))
}

BACKUP_DIR="$backups" ARCHIVE_DIR="$archive" KEEP_BACKUPS=7 \
  "$repo_root/ops/backup/prune-archive.sh" > "$work/output.txt"

# Seven newest backups kept, three oldest removed.
expect_absent "$backups/20260901T000000Z" "the oldest backup beyond the retention count"
expect_absent "$backups/20260902T000000Z" "the second oldest backup"
expect_absent "$backups/20260903T000000Z" "the third oldest backup"
expect_present "$backups/20260904T000000Z" "the oldest retained backup"
expect_present "$backups/20260910T000000Z" "the newest backup"

# The label anchors segment 4: everything before it is unreachable, everything from it on
# is still required by a backup we kept.
expect_absent "$archive/000000010000000000000001" "a segment older than every retained backup"
expect_absent "$archive/000000010000000000000003" "a segment older than every retained backup"
expect_present "$archive/000000010000000000000004" "the first segment the oldest retained backup needs"
expect_present "$archive/000000010000000000000005" "a segment a retained backup needs"
expect_present "$archive/000000010000000000000020" "the newest segment"
expect_present "$archive/000000010000000000000004.00000028.backup" "the backup label itself"

# Refusing to act is the correct behaviour when the anchor is missing: pruning by age alone
# would delete segments that retained backups still depend on.
empty="$work/empty-archive"
mkdir -p "$empty"
printf 'wal' > "$empty/000000010000000000000001"
BACKUP_DIR="$backups" ARCHIVE_DIR="$empty" KEEP_BACKUPS=7 \
  "$repo_root/ops/backup/prune-archive.sh" > "$work/no-label.txt"
expect_present "$empty/000000010000000000000001" "a segment with no backup label to anchor pruning"
grep -q "not pruning" "$work/no-label.txt" || {
  echo "FAIL: pruning without a backup label was not reported" >&2; exit 1; }
checks=$((checks + 1))

# Never delete everything, however the policy is configured.
if BACKUP_DIR="$backups" ARCHIVE_DIR="$archive" KEEP_BACKUPS=0 \
    "$repo_root/ops/backup/prune-archive.sh" > "$work/zero.txt" 2>&1; then
  echo "FAIL: a retention count of zero was accepted" >&2
  exit 1
fi
checks=$((checks + 1))

# With no backups at all, the archive must be left alone rather than emptied.
lonely="$work/lonely"
mkdir -p "$lonely"
BACKUP_DIR="$lonely" ARCHIVE_DIR="$archive" KEEP_BACKUPS=7 \
  "$repo_root/ops/backup/prune-archive.sh" > "$work/lonely.txt"
expect_present "$archive/000000010000000000000020" "the archive when no base backup exists"

echo "Verified backup retention with ${checks} checks: old backups pruned, and no segment"
echo "a retained backup still needs was removed."
