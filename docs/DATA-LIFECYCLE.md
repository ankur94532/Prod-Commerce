# Data inventory, retention, and subject rights

This is the repository policy baseline. Legal counsel and the production data owner must
approve jurisdiction-specific periods before launch. Enforcement jobs, a cross-service
export/erasure workflow, and a completed request drill are still required; this document
does not claim they exist.

| Store / owner | Personal or sensitive data | Proposed retention / disposition |
| --- | --- | --- |
| auth PostgreSQL | user ID, email, full name, password hash, role | account lifetime; erase profile and credential promptly after verified request unless a legal hold applies |
| cart Redis | user ID, product intent | 30 days since write; delete on checkout/account erasure |
| order PostgreSQL | user ID, product/price history, payment-provider references | retain financial record for the legally approved period; pseudonymize account link on erasure; never store card data |
| analytics/recommendation PostgreSQL | product/order aggregates and deduplication IDs | no direct profile fields; retain while operationally useful, then rebuild/delete by documented window |
| search Elasticsearch | catalog only | no shopper profile data permitted |
| logs/traces/audit | user subject IDs, request IDs, paths; potentially accidental input | redact query strings/secrets; 30-day operational logs and 1-year admin audit are proposed maxima |
| backups/WAL | copies of the above | proposed 35-day backup and WAL recovery window; expired media must be deleted in the backup provider |
| browser | JWTs and checkout attempt/cart fingerprint | session/account lifetime; payment card values never persist |

Cart records now expire after 30 days. Every other period above is policy-only until an
environment-owned lifecycle rule or tested application job enforces it.

## Export

A subject export must authenticate the requester again, create a tracked request ID, and
collect auth profile, cart, and orders from their owning services. It must not expose
password hashes, access/refresh tokens, internal audit about other people, raw payment
tokens, or another subject's data. Deliver through a short-lived encrypted download and
audit creation/download/expiry. There is no cross-service export endpoint yet.

## Erasure

Erasure is a workflow, not `DELETE users`: first revoke tokens and prevent new writes, then
delete auth/cart data, pseudonymize the order account link while preserving legally required
financial facts, remove any user-linked projections, and record completion without retaining
the erased identity in the audit record. Backups age out under the backup retention window;
restores must replay erasure tombstones before serving traffic. Legal holds pause only the
covered data and require an auditable reason. No tested orchestrator/tombstone flow exists
yet, so GDPR/CCPA erasure is not production-ready.

## Engineering controls

New fields require an owner, purpose, classification, retention period, export behavior,
and erasure behavior in this table. Logs must use identifiers rather than emails/names.
Search queries and URL query strings are excluded from client telemetry. Production needs
automated retention evidence and a quarterly export/erasure drill.
