# Runbook: order events on the dead-letter topic

**Alerts:** `DeadLetterTopicReceivingEvents`, `ConsumerStopped`

## What it means

A consumer failed to project an order event after its retry budget, and the event was
parked on `<topic>.DLT`. **A parked event is not a processed event.** Analytics and
recommendation totals are missing those orders until the event is replayed.

This behaviour changed deliberately: the consumer used to retry forever, which kept the
data correct but blocked its partition indefinitely, silently, for every later order.
Parking bounds the outage and raises this alert instead.

## Diagnose

```bash
# How many events are parked, and for which consumer
kafka-console-consumer --bootstrap-server <broker> \
  --topic order-events.DLT --from-beginning --max-messages 20 \
  --property print.headers=true
```

The dead-letter headers carry the original topic, partition, offset, exception class, and
message. Read the exception first: a deserialization failure is a contract break, an
application exception is usually a projection bug or a missing referenced row.

Also check consumer lag: `kafka-consumer-groups --bootstrap-server <broker> --describe
--group analytics-service`.

## Act

1. **Contract break (deserialization).** A producer changed the event shape. Roll the
   producer back, or ship the consumer change that understands it. Then replay.
2. **Projection bug.** Fix the consumer, deploy, then replay.
3. **Replay.** Consumers deduplicate by event id in a database inbox, in the same
   transaction as the aggregate update, so replaying a dead-letter topic is safe and will
   not double-count. Republish the parked records to the original topic and confirm the
   projections converge.
4. **`ConsumerStopped`.** The listener container is not consuming while events are pending.
   Check for a stopped container, a rebalance storm, or an unhandled error in the listener
   thread; restart the service and confirm lag falls.

## Verify afterwards

Compare `order_completed_total` in order-service against the analytics projection over the
same window. They should agree once the backlog clears.
