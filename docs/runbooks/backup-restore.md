# Runbook: backup and restore

## What exists

`ops/backup/pg-backup.sh` takes a `pg_dump` custom-format archive of every service database
and writes a manifest with a SHA-256 checksum and table count for each.

`ops/backup/pg-restore-drill.sh` restores a backup into a **disposable** PostgreSQL
container, verifies each archive against its checksum, and compares the restored table count
to the count recorded at backup time. It never touches a live database.

```bash
export PGHOST=... PGPORT=5432 PGUSER=... PGPASSWORD=...
ops/backup/pg-backup.sh ./backups/$(date -u +%Y%m%dT%H%M%SZ)
ops/backup/pg-restore-drill.sh ./backups/<timestamp>
```

Both scripts use a local `pg_dump`/`psql` when present and a pinned `postgres:16` container
otherwise, so they run the same way on a workstation and on a jump host.

## Point-in-time recovery

The Compose PostgreSQL now runs with `wal_level=replica` and `archive_mode=on`, archiving
segments to the `pgarchive` volume, so recovery is no longer limited to the last logical dump.

`ops/backup/pitr-drill.sh` proves it end to end on disposable containers: it takes a base
backup, writes more rows, records a target time, then writes a row and drops the table, and
restores to the target. The dropped table comes back, the row written before the target
survives, and the row written after it does not.

Restoring for real needs a base backup (`pg_basebackup`), the archived segments, and a
`recovery_target_time`; the drill script is the worked example.

## What does not exist

- **No schedule.** Nothing runs these automatically. A CronJob or a managed backup policy is
  required before this counts as a backup strategy.
- **No archive retention or pruning.** The archive volume grows without bound until someone
  configures `pg_archivecleanup` or an object-store lifecycle policy.
- **No offsite copy.** Archives are written wherever you point them. A backup on the same
  disk as the database is not a backup.
- **No RPO/RTO commitment.** None can honestly be stated until the above exist.

## Restoring for real

1. Stop writes to the affected service, or take the service down.
2. Run the restore drill against the chosen backup **first**. Never restore an archive you
   have not just proven restorable.
3. Restore into a new database, then repoint the service, rather than overwriting in place:
   an in-place restore destroys the evidence you may still need.
4. Reconcile afterwards. Orders are the source of truth; analytics and recommendation
   projections can be rebuilt by replaying order events, which consumers deduplicate.

## Ordering note

`ecom_order` and `ecom_catalog` must be restored to a consistent point relative to each
other, because inventory reservations reference orders. These are separate logical dumps
taken at slightly different instants, so a restore can leave a reservation without its order.
The recovery path treats an unknown reservation as releasable, but verify the reservation
table after any restore.
