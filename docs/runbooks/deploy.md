# Ordered deployment and rollback

The release command is `ops/deploy/release.sh staging|production`. It deliberately performs
one order only: create/update least-privilege database roles, run and wait for all schema
migration Jobs, apply Deployments, then wait for their zero-unavailable rolling updates.
Never contract a schema in the same release that stops reading it.

Required inputs are the PostgreSQL administrator variables documented by
`ops/migrations/apply-database-roles.sh`, the five service database passwords, and:

```bash
export IMAGE_PREFIX=ghcr.io/owner/repository
export IMAGE_TAG=sha-0123456789abcdef
ops/deploy/release.sh staging
```

Promote the identical immutable tag to production only after staging verification:

```bash
ops/deploy/release.sh production
```

If Kubernetes reports a failed rollout, the command runs `kubectl rollout undo` for every
Deployment and exits non-zero. For a later operational rollback, set `IMAGE_TAG` to the last
known-good digest-derived tag and run the same command. Database migrations are not rolled
back; this is why expand/contract compatibility is mandatory.

This repository supplies the process but not a cluster. A staging environment and its
credentials still have to be provisioned before this can be exercised end to end. The
`Deploy Staging` workflow remains inert until the staging environment secrets are present
and the repository variable `STAGING_DEPLOY_ENABLED` is exactly `true`; after that, every
successful image workflow deploys its verified commit SHA serially.
