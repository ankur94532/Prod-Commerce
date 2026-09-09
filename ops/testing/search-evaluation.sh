#!/usr/bin/env bash
# Runs the graded-evaluation harness tests. No services required: the end-to-end tests
# drive an in-process stub catalog/search server. Nothing here measures relevance.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root/backend/search-service/evaluation/tests"
python3 - <<'PY'
import sys, unittest
loader = unittest.TestLoader()
suite = loader.discover('.')
if loader.errors:
    print('\n'.join(str(error) for error in loader.errors), file=sys.stderr)
    raise SystemExit('Test discovery failed')
result = unittest.TextTestRunner(verbosity=1, buffer=True).run(suite)
if not result.wasSuccessful() or result.testsRun == 0 or result.skipped:
    raise SystemExit('Incomplete or failing evaluation-harness run')
print(f'Verified {result.testsRun} evaluation-harness tests with no skips')
PY
