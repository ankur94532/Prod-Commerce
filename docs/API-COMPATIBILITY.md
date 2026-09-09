# API compatibility policy

The `/api/v1` prefix is a compatibility contract, not decoration. This policy applies to
HTTP APIs exposed through the gateway and to Kafka event payloads.

## Within a major version

Compatible changes may add optional request fields, response fields, endpoints, enum values
that clients are documented to treat as unknown, pagination links, and error detail.
Services must not remove or rename fields/endpoints, change a field's meaning or type,
tighten accepted input, change successful status semantics, or make an optional field
required within the same major version.

Security preconditions such as authentication, idempotency, and conditional mutation may
be tightened only before launch or through a documented deprecation window. The cart
`Idempotency-Key` and `If-Match` requirements are pre-launch safety corrections and are
listed in the release notes; future changes follow the normal policy.

Clients must ignore unknown response fields, tolerate documented enum expansion, send an
explicit `Accept: application/json`, and inspect the documented status field rather than
assuming every 2xx has identical business meaning.

## Breaking changes

A breaking HTTP change gets a new path major such as `/api/v2`. Old and new versions run in
parallel for at least one normal client release cycle and a published retirement date. The
gateway reports usage of deprecated routes; removal requires zero known supported-client
traffic and an owner-approved migration record. Emergency security removal is allowed only
through the incident process and must be called out as such.

Kafka events use a `schemaVersion` field. Consumers accept the current and immediately
previous schema. Producers add fields compatibly; incompatible meaning/type changes use a
new topic or event name. Consumer-driven contract tests in CI cover every synchronous
cross-service boundary; event schema compatibility is still a backlog item.

## Deprecation response

Deprecated HTTP endpoints return `Deprecation: true`, a `Sunset` timestamp, and a `Link`
to migration documentation. Deprecation starts only after the replacement is deployed.
OpenAPI descriptions and frontend clients are updated in the same change.

## Ownership

The service owning an endpoint owns its contract. A pull request with a contract change
must state whether it is compatible, include provider and consumer failure-path tests, and
update this policy if it introduces a new category. Deployment order follows
`docs/runbooks/deploy.md` when old and new versions overlap.
