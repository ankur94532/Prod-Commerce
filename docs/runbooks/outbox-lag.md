# Runbook: outbox not draining

**Alerts:** `OutboxNotDraining`, `OutboxBacklogLarge`

## What it means

Order events are committed to `outbox_events` in the same transaction as the order, so no
event is lost. But they are not reaching Kafka, which means analytics totals and
recommendations are diverging from what customers actually bought. Orders themselves are
unaffected: this is a freshness problem, not a correctness one.

## Diagnose

```sql
SELECT count(*) FILTER (WHERE published_at IS NULL) AS pending,
       min(created_at) FILTER (WHERE published_at IS NULL) AS oldest,
       max(attempts)   FILTER (WHERE published_at IS NULL) AS worst_attempts
FROM outbox_events;

-- Events that keep failing
SELECT id, event_type, attempts, next_attempt_at
FROM outbox_events
WHERE published_at IS NULL AND attempts > 3
ORDER BY attempts DESC LIMIT 20;
```

Then check the broker: `kafka-broker-api-versions --bootstrap-server <broker>`, and whether
`order-service` logs show producer timeouts.

## Act

1. **Broker unreachable.** Fix Kafka. The publisher retries with backoff and drains on its
   own; the backlog is bounded by disk, not by a deadline.
2. **Publisher not running.** The publisher is a scheduled task in `order-service`. Confirm
   at least one replica is healthy and that `ORDERS_OUTBOX_PUBLISHER_DELAY_MS` was not set
   to something absurd. It uses `FOR UPDATE SKIP LOCKED`, so replicas do not collide.
3. **A single event failing repeatedly.** Inspect its payload. If it can never be published
   (malformed, references a deleted aggregate), record the id in the incident, then mark it
   published deliberately — and note that its projection will need a manual backfill.
4. **Backlog draining but large.** Leave it. Publication is at-least-once and consumers
   deduplicate by event id, so a burst is safe.

## Do not

Do not truncate `outbox_events` to clear the alert. That silently discards purchases from
every downstream projection.
