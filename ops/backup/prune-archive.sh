#!/usr/bin/env bash
# Applies a retention policy to base backups and to the write-ahead log archive.
#
# Without this the archive grows until the volume fills, and a full archive volume stops
# archive_command succeeding, which stops PostgreSQL from recycling WAL, which eventually
# stops the database. Retention is not housekeeping; it is part of staying up.
#
# The rule that matters: WAL segments are only removed once no retained base backup still
# needs them. Pruning WAL by age alone silently destroys the ability to restore from the
# older backups you are still keeping.
#
#   BACKUP_DIR=./backups ARCHIVE_DIR=./archive KEEP_BACKUPS=7 ops/backup/prune-archive.sh
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:?set BACKUP_DIR (holds one directory per base backup)}"
ARCHIVE_DIR="${ARCHIVE_DIR:?set ARCHIVE_DIR (the archive_command destination)}"
KEEP_BACKUPS="${KEEP_BACKUPS:-7}"
DRY_RUN="${DRY_RUN:-false}"

if [ "$KEEP_BACKUPS" -lt 1 ]; then
  echo "KEEP_BACKUPS must be at least 1; refusing to delete every backup" >&2
  exit 1
fi

remove() {
  if [ "$DRY_RUN" = "true" ]; then
    echo "would remove $1"
  else
    rm -rf "$1"
  fi
}

# Base backups, newest first by directory name (the backup script names them by UTC stamp).
# Built with a read loop rather than mapfile, which needs bash 4 and is absent on macOS.
backups=()
while IFS= read -r directory; do
  [ -n "$directory" ] && backups+=("$directory")
done < <(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d | sort -r)
if [ "${#backups[@]}" -eq 0 ]; then
  echo "No base backups found in $BACKUP_DIR; leaving the archive untouched" >&2
  # Pruning WAL with no backup to anchor it would leave nothing recoverable.
  exit 0
fi

kept=0
declare -a retained=()
for backup in "${backups[@]}"; do
  if [ "$kept" -lt "$KEEP_BACKUPS" ]; then
    retained+=("$backup")
    kept=$((kept + 1))
  else
    remove "$backup"
    echo "pruned base backup $(basename "$backup")"
  fi
done

oldest_retained="${retained[${#retained[@]} - 1]}"
echo "retaining ${#retained[@]} base backups; oldest is $(basename "$oldest_retained")"

# The .backup label written alongside the archive records the first WAL segment that backup
# needs. Anything sorting before it is unreachable from every backup we still keep.
oldest_label="$(find "$ARCHIVE_DIR" -maxdepth 1 -name '*.backup' -type f | sort | head -1 || true)"
if [ -z "$oldest_label" ]; then
  echo "No .backup label in $ARCHIVE_DIR; not pruning WAL, because the earliest segment"
  echo "still required by a retained backup cannot be determined."
  exit 0
fi

# Choose the label belonging to the oldest retained backup when one is identifiable,
# otherwise stay conservative and keep everything from the earliest label onwards.
cutoff_label="$oldest_label"
for label in $(find "$ARCHIVE_DIR" -maxdepth 1 -name '*.backup' -type f | sort); do
  segment="$(basename "$label" | cut -d. -f1)"
  if [ -n "$segment" ]; then
    cutoff_label="$label"
    break
  fi
done
cutoff_segment="$(basename "$cutoff_label" | cut -d. -f1)"
echo "keeping write-ahead log from segment ${cutoff_segment} onwards"

pruned=0
while IFS= read -r segment; do
  name="$(basename "$segment")"
  case "$name" in
    *.backup|*.history) continue ;;
  esac
  # WAL file names sort lexicographically in the order they were written.
  if [[ "$name" < "$cutoff_segment" ]]; then
    remove "$segment"
    pruned=$((pruned + 1))
  fi
done < <(find "$ARCHIVE_DIR" -maxdepth 1 -type f | sort)

echo "pruned ${pruned} write-ahead log segments no retained backup can reach"
