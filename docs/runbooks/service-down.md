# Runbook: service unreachable or circuit breaker open

**Alerts:** `ServiceDown`, `CircuitBreakerOpen`

## Diagnose

```bash
kubectl -n gocommerce get pods -l app=<service> -o wide
kubectl -n gocommerce describe pod <pod>
kubectl -n gocommerce logs <pod> --previous --tail=200
```

Probes hit `/actuator/health/readiness` and `/actuator/health/liveness` on the management
port (9090), which is not reachable from the public ingress.

## Common causes

1. **Missing configuration.** Services fail fast when a required secret is absent:
   `SECURITY_JWT_SECRET` and `INTERNAL_SERVICE_TOKEN` have no defaults, by design. A pod
   that never becomes ready after a secret change is usually this. The error names the
   property.
2. **Database credentials.** Each service now connects with its own least-privilege role.
   A pod that starts and then fails on the first query is authenticating as the wrong role,
   or the role migration has not been applied to that database.
3. **CrashLoopBackOff after a deploy.** Compare the running image tag to what was intended.
   The base manifests carry `:latest`; production should be deployed through
   `deploy/overlays/production`, which pins an immutable tag.
4. **OOM.** `ExitOnOutOfMemoryError` is set, so an out-of-memory condition exits rather than
   limping. Raise the memory limit or find the leak; the heap follows the container limit.

## Circuit breaker open

An open breaker means the service is protecting itself from a failing dependency: it is a
symptom, not the fault. Find the dependency named in the `name` label and work that.
Breakers transition to half-open automatically; do not restart pods to "clear" them.

## Escalate

If more than one service is down at once, check the shared PostgreSQL instance, Kafka, and
Redis before investigating the services individually.
