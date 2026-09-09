#!/usr/bin/env python3
"""Fail on skipped/stale reliability reports and save a small, reproducible evidence record."""
import datetime
import hashlib
import json
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

repo = pathlib.Path(__file__).resolve().parents[2]
started = float(sys.argv[1])
suites = []
for module in ('catalog-service', 'order-service', 'analytics-service', 'recommendation-service'):
    reports = list((repo / 'backend' / module / 'target/surefire-reports').glob('TEST-*ReliabilityPostgresTest.xml'))
    if not reports:
        raise SystemExit(f'Missing reliability report: {module}')
    for report in reports:
        if report.stat().st_mtime < started:
            raise SystemExit(f'Stale report: {report}')
        root = ET.parse(report).getroot()
        result = {key: int(root.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
        if not result['tests'] or any(result[k] for k in ('failures', 'errors', 'skipped')):
            raise SystemExit(f'Incomplete reliability run: {module}: {result}')
        suites.append({'module': module, 'suite': root.get('name'), **result,
                       'cases': [case.get('name') for case in root.findall('testcase')]})

sources = []
for module in ('catalog-service', 'order-service', 'analytics-service', 'recommendation-service'):
    sources.extend((repo / 'backend' / module / 'src').rglob('*.java'))
    sources.extend((repo / 'backend' / module / 'src').rglob('*.sql'))
    sources.extend((repo / 'backend' / module / 'src').rglob('*.yml'))
sources.extend((repo / 'frontend/src/checkout').glob('*.js'))
sources += [repo / 'frontend/src/pages/Checkout.jsx', repo / 'frontend/src/pages/OrdersPage.jsx', repo / 'frontend/src/api/orders.js']
digest = hashlib.sha256()
for source in sorted(sources):
    digest.update(str(source.relative_to(repo)).encode())
    digest.update(b'\0')
    digest.update(source.read_bytes())
    digest.update(b'\0')
evidence = {
    'recorded_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'command': 'ops/testing/checkout-reliability.sh -q',
    'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip(),
    'source_sha256': digest.hexdigest(),
    'source_scope': 'Four affected backend modules src/**/*.java,sql,yml; frontend checkout helper/tests, Checkout.jsx, OrdersPage.jsx, api/orders.js; paths and bytes separated by NUL',
    'working_tree_changes_included': True,
    'postgres_image': 'postgres:16',
    'tests': sum(s['tests'] for s in suites),
    'failures': 0, 'errors': 0, 'skipped': 0,
    'suites': suites,
    'limits': 'Real PostgreSQL migrations, transactions and locks; order tests inject catalog/payment faults. No live Kafka or OS process-kill test. This is correctness evidence, not a load benchmark or production guarantee.',
}
output = repo / 'backend/target/checkout-reliability-evidence.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(evidence, indent=2) + '\n')
print(f"Verified {evidence['tests']} PostgreSQL tests with no skips; evidence: {output}")
