# Service level objectives

These are the promises the alerts defend. They are chosen so that a breach means a
customer noticed, not that a graph moved.

Measurement window: 30 days, rolling. Error budget is the complement of the objective.

| Objective | Target | Measured as | Budget per 30 days |
| --- | --- | --- | --- |
| Gateway availability | 99.9% of requests non-5xx | `http_server_requests_seconds_count` at `api-gateway`, status `5xx` over total | ~43 minutes fully down |
| Search latency | 95% of searches under 800ms | `http_server_requests_seconds_bucket` on `/api/v1/search*` | 5% of searches slower |
| Checkout completion | 99.5% of accepted checkouts reach PAID or CANCELLED within 15 minutes | `order_recovery_oldest_seconds`, `order_recovery_backlog` | 0.5% needing manual recovery |
| Event freshness | 99% of order events projected within 5 minutes | `order_outbox_oldest_seconds` | 1% of events late |

## Burn-rate alerting

`GatewayErrorBudgetBurningFast` fires at 14.4x the sustainable burn rate, which exhausts a
30-day budget in roughly two days. That multiplier is the standard fast-burn threshold: it
catches an outage in minutes without paging for a brief blip.

`GatewayErrorBudgetBurningSlowly` covers the other half: 6x burn over 6 hours, paired with a
30-minute window so it clears once the incident does. A persistent low-grade error rate eats
the month without ever tripping the fast threshold.

Both alerts route through Alertmanager, whose receivers read their destination from a mounted
secret. **Until that secret exists, no alert reaches anyone.**

## What is deliberately not an objective

- **Payment success rate.** The payment provider is a mock. Any number computed from it
  describes the mock, not a payment processor.
- **Search relevance.** AI-judged exploratory runs exist, including a second-AI agreement
  check. They use a generated catalog and AI-authored queries and are not shopper relevance
  or human judgments.
- **Throughput.** A first local application benchmark now exists, but one replica per service,
  a 600-product generated catalog, a laptop, and synthetic queries are not a capacity model.
  The warm-regime measurements are optimistic cache-hit bounds, not a production target.

## Honest limits of these numbers

Nothing here has been measured against production traffic, because there is none. The
objectives are stated so the alerts have a defensible threshold and so the gaps above are
explicit. Treat them as the intended contract, not as an observed track record.
