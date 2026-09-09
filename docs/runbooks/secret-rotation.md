# Secret rotation and storage

`k8s/secrets.example.yaml` is a schema, not a production secret store. Production must use
an external secret manager plus a cluster integration that encrypts transport and limits
read access by service account. Kubernetes encryption at rest and audit logging must be
enabled and verified by the cluster owner; this repository cannot prove those controls.

Secrets are split by blast radius:

- `gocommerce-jwt-signer`: auth-service only
- `gocommerce-jwt-verifier`: public JWT verification material
- `gocommerce-internal-auth`: only services that call or expose internal APIs
- one database Secret for each database-owning service

Never use a namespace-wide `envFrom` Secret containing unrelated credentials.

## Rotation rules

- JWT keys: follow `jwt-key-rotation.md`; use an overlap key.
- Internal service token: deploy consumers that accept both old and new values before
  changing callers. The current application supports one value only, so this requires a
  coordinated maintenance window until dual-token support is implemented.
- Database passwords: create a second login credential with the same least-privilege
  grants, update the service Secret and roll the owning service, verify connections, then
  revoke the old credential. Do one database at a time.

For every rotation, record the secret name, owner, start/end time, affected deployment
revisions, verification evidence, and old-credential revocation—never the secret value.
Test each procedure in staging before production. A schedule, external-manager policy,
and named human owner remain environment-owned launch inputs.
