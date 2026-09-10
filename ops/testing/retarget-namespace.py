"""Retargets rendered Kubernetes manifests at a scratch namespace.

ops/testing/data-tier-deploy.sh renders k8s/ exactly as a real apply would and then points
the result somewhere disposable. Doing that with a blanket search-and-replace is how a drill
ends up modifying the namespace people actually use, so this is deliberate about what it
touches:

  * an object's own `namespace:` field, and nothing that merely contains the same word --
    `kubernetes.io/metadata.name: ingress-nginx` in a NetworkPolicy selector is a selector,
    not an address;
  * the Namespace object's own name;
  * Deployment replica counts, because this drill asks whether the tier comes up, not how
    much of it fits.

Usage: retarget-namespace.py <rendered.yaml> <out.yaml> <namespace>
"""
from __future__ import annotations

import re
import sys


def retarget(document: str, namespace: str) -> str:
    document = re.sub(r'(?m)^(\s*)namespace: gocommerce\s*$', r'\1namespace: ' + namespace, document)
    if re.search(r'(?m)^kind: Namespace\s*$', document):
        document = re.sub(r'(?m)^(\s*)name: gocommerce\s*$', r'\1name: ' + namespace, document)
    if re.search(r'(?m)^kind: Deployment\s*$', document):
        document = re.sub(r'(?m)^(\s*)replicas: \d+\s*$', r'\1replicas: 1', document)
    return document


def main(argv: list[str]) -> int:
    source, target, namespace = argv[1], argv[2], argv[3]
    documents = open(source).read().split('\n---\n')
    rewritten = [retarget(document, namespace) for document in documents]
    joined = '\n---\n'.join(rewritten)
    if re.search(r'(?m)^\s*namespace: gocommerce\s*$', joined):
        raise SystemExit('refusing to emit manifests that still target gocommerce')
    open(target, 'w').write(joined)
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
