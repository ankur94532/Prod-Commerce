# Incident response

This is the operating process for a customer-impacting or security incident. A real launch
still requires named people, a paging destination, and an approved rotation; repository
roles below are functions, not assigned humans.

## Severity and response

| Severity | Example | Acknowledge target | Coordination |
| --- | --- | --- | --- |
| SEV-1 | checkout unavailable, suspected breach, duplicate charging | 5 minutes | page primary and secondary; dedicated incident channel/bridge |
| SEV-2 | degraded search, delayed events, partial region/service outage | 15 minutes | page primary; secondary on request |
| SEV-3 | no active customer impact, capacity or maintenance risk | next business day | ticket |

Targets are objectives until a real pager test and rotation prove them.

## Roles

- Incident commander owns decisions, severity, timeline, and handoffs.
- Operations lead investigates and mitigates; they do not also run communications in SEV-1.
- Communications lead posts factual updates at least every 30 minutes for SEV-1.
- Scribe records timestamps, commands, observations, and decision rationale without secrets
  or customer PII.

## Procedure

1. Acknowledge, open an incident record, name the commander, and state known customer impact.
2. Preserve evidence and dashboards. Never delay containment to make diagnosis cleaner.
3. Stop the damage using the least risky reversible action: rollback, disable ingress,
   rotate a compromised credential, or pause a consumer. Follow the alert-linked runbook.
4. Validate recovery from the customer's path, not only from pod health. Watch error budget,
   payment ambiguity, order recovery, outbox, and DLT backlog.
5. Resolve only after metrics remain healthy for an agreed observation window and queued
   work is reconciled. Assign every follow-up an owner and due date.
6. For SEV-1/2, hold a blameless review within five business days and publish the completed
   template in the incident system. Security incidents also follow legal notification and
   evidence-retention requirements set by the organization.

Quarterly, run one game day from alert delivery through rollback/recovery and one restore
drill. The codebase proves the restore mechanics locally; it does not prove a human process.
