# Runbook: gateway error budget burning

**Alert:** `GatewayErrorBudgetBurningFast`

## What it means

More than 1.44% of requests through the gateway are returning 5xx, which burns the 99.9%
availability budget about 14 times faster than sustainable. Shoppers are seeing failures now.

## Diagnose

Find which route and which downstream:

```promql
sum by (uri, status) (rate(http_server_requests_seconds_count{service="api-gateway",status=~"5.."}[5m]))
sum by (service, status) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
```

Then check whether the gateway is shedding rather than failing:

- `resilience4j_circuitbreaker_state{name="searchRoute",state="open"}` — the search route is
  open and serving its fallback. That is degradation by design, not a bug.
- 401 spikes rather than 5xx point at the edge auth filter: a rotated `SECURITY_JWT_SECRET`
  invalidates every issued token at once.

## Act

1. **One downstream failing.** Work that service's own alert and runbook. The gateway is
   reporting, not causing.
2. **All downstreams failing.** Suspect shared infrastructure: the database instance (all
   five logical databases share one), Kafka, or Redis. Check those before the services.
3. **Gateway itself failing.** Check gateway pod restarts and memory. `JAVA_TOOL_OPTIONS`
   sets `ExitOnOutOfMemoryError`, so an OOM shows as a restart loop, not a hang.
4. **Rate limiting.** 429s are not 5xx and do not burn this budget. If legitimate traffic is
   being limited, adjust the route's `redis-rate-limiter` settings rather than removing them.

## Escalate

If the cause is the shared PostgreSQL instance, treat it as a full-site incident: every
service depends on it, and it is a single failure domain today.
