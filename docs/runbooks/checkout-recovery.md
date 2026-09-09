# Runbook: checkout recovery backlog

**Alerts:** `CheckoutRecoveryBacklogGrowing`, `CheckoutRecoveryStuck`, `OrderDatabasePoolSaturated`

## What it means

An order is in `PENDING_PAYMENT` or `COMPENSATING`. Either a shopper may have been charged
without receiving a confirmation, or stock is reserved against an order that will never
complete and cannot be sold to anyone else. Both are customer-visible; neither resolves on
its own beyond the retry budget.

## Diagnose

```sql
-- What is stuck, for how long, and how hard we have tried
SELECT id, status, recovery_attempts, next_recovery_at, updated_at, now() - updated_at AS age
FROM orders
WHERE status IN ('PENDING_PAYMENT', 'COMPENSATING')
ORDER BY updated_at
LIMIT 50;
```

Then read `order-service` logs for the affected order ids. Release failures log the order
id, line ids, attempt count, and exception class.

Check whether the cause is downstream:

- `resilience4j_circuitbreaker_state{name="catalogClient",state="open"}` — catalog is failing,
  so reservations cannot be released.
- `hikaricp_connections_active / hikaricp_connections_max` on `order-service` — checkout holds
  a connection across catalog calls, so a saturated pool stalls recovery itself.

## Act

1. **Catalog is down or erroring.** Fix catalog first. Recovery retries with backoff and
   drains on its own once catalog recovers; do not intervene per order.
2. **Pool saturated (>90%).** Scale `order-service` replicas, or raise `DB_POOL_MAX_SIZE`
   only if the database has headroom. The pool is deliberately sized: raising it moves the
   bottleneck to the database rather than removing it.
3. **A single poison order.** Confirm with the payment provider whether the charge settled,
   then move the order to its terminal state by hand and record why in the incident notes.
   Never delete the row: the idempotency key is what stops a duplicate charge on retry.
4. **Backlog after a deploy.** Check whether `workflow_version` differs from what the
   running code expects; recovery only picks up rows at the version it understands.

## Escalate

If a shopper was charged and the order cannot be completed, this is a money-affecting
incident: involve whoever owns payments before issuing a refund or a manual fulfilment.

## Payment reconciliation

Recovery no longer cancels an order without asking the provider what happened. Before
cancelling it looks the charge up by the order's idempotency key (`order:<id>:charge`):

- **a successful charge exists** — the order is refunded (`order:<id>:refund`, also
  idempotent) and only then cancelled. `payment_refund_id` records that it was given back.
- **no charge exists** — the order is cancelled as before.
- **the provider cannot be reached** — the order stays in `COMPENSATING` and is retried.
  Not knowing is not the same as knowing there was no charge.

An unconfirmed refund keeps the order in compensation rather than marking it cancelled, so
`CheckoutRecoveryStuck` firing alongside provider errors usually means refunds are failing.
Check `payment_refund_id`: null with a non-null `payment_transaction_id` means money is
still with the provider.

## Known limitation

The provider is still a mock. It models idempotency and lookup faithfully, but its memory is
a map in one JVM rather than a shared durable ledger, and no money moves. A real integration
also needs settlement reconciliation against provider webhooks or reports, which does not
exist here.
