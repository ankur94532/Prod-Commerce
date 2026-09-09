#!/usr/bin/env bash
# Answers one question the manifests cannot: does this cluster actually enforce
# NetworkPolicy? Kubernetes accepts NetworkPolicy objects whether or not the CNI implements
# them, so a cluster without an enforcing CNI reports a perfectly healthy default-deny
# policy while every pod remains reachable from every other pod.
#
# Creates its own scratch namespace, tests connectivity with and without a deny-all policy,
# and removes everything. It does not touch the gocommerce namespace.
set -euo pipefail

namespace="netpol-check-$$"
cleanup() { kubectl delete namespace "$namespace" --wait=false >/dev/null 2>&1 || true; }
trap cleanup EXIT

kubectl create namespace "$namespace" >/dev/null

kubectl -n "$namespace" run target --image=nginx:1.27-alpine --labels=role=target \
  --port=80 --restart=Never >/dev/null
kubectl -n "$namespace" expose pod target --port=80 --name=target >/dev/null
kubectl -n "$namespace" wait --for=condition=Ready pod/target --timeout=120s >/dev/null

probe() {
  kubectl -n "$namespace" run probe-"$1" --image=curlimages/curl:8.10.1 --restart=Never --rm -i \
    --quiet --command -- curl --silent --show-error --max-time 5 -o /dev/null -w '%{http_code}' \
    http://target 2>/dev/null || true
}

baseline="$(probe baseline)"
if [ "$baseline" != "200" ]; then
  echo "INCONCLUSIVE: pods cannot reach each other even without a policy (got '${baseline}')." >&2
  echo "Something other than NetworkPolicy is blocking traffic; enforcement is unproven." >&2
  exit 2
fi

kubectl -n "$namespace" apply -f - >/dev/null <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-all-ingress
spec:
  podSelector: {}
  policyTypes:
    - Ingress
EOF
sleep 5

blocked="$(probe denied)"

if [ "$blocked" = "200" ]; then
  cat >&2 <<'MESSAGE'
NOT ENFORCED: a default-deny ingress policy was applied and traffic still flowed.

Every NetworkPolicy in k8s/networkpolicies.yaml is inert on this cluster. The internal
inventory and search-index endpoints are reachable from any pod in the cluster regardless
of what those manifests say. Install a CNI that enforces NetworkPolicy (Calico, Cilium) or
treat pod-to-pod isolation as absent and rely on the service tokens alone.
MESSAGE
  exit 1
fi

echo "ENFORCED: traffic was allowed without a policy and blocked with a default-deny policy"
echo "(probe returned '${blocked}' instead of 200). The NetworkPolicies in k8s/ are effective."
