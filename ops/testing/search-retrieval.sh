#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
container_id="$(docker run --rm -d -e discovery.type=single-node -e xpack.security.enabled=false -e ES_JAVA_OPTS=-Xms512m\ -Xmx512m -p 127.0.0.1::9200 docker.elastic.co/elasticsearch/elasticsearch:8.15.2)"
trap 'docker stop "$container_id" >/dev/null' EXIT
es_port="$(docker port "$container_id" 9200/tcp | cut -d: -f2)"
export SEARCH_TEST_ES_ADDRESS="127.0.0.1:${es_port}"
for attempt in {1..60}; do
  if curl --silent --fail "http://${SEARCH_TEST_ES_ADDRESS}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null; then break; fi
  sleep 1
done
curl --silent --fail "http://${SEARCH_TEST_ES_ADDRESS}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null
test_started="$(python3 -c 'import time; print(time.time())')"
mvn -f "$repo_root/backend/pom.xml" -pl search-service -am \
  -Dtest='SearchRetrievalElasticsearchTest,SearchIndexAliasElasticsearchTest' -Dsurefire.failIfNoSpecifiedTests=false test "$@"
python3 - "$repo_root" "$test_started" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
total = 0
for name in ('SearchRetrievalElasticsearchTest', 'SearchIndexAliasElasticsearchTest'):
    p = root / f'backend/search-service/target/surefire-reports/TEST-com.gocommerce.search.service.{name}.xml'
    r = ET.parse(p).getroot()
    assert p.stat().st_mtime >= float(sys.argv[2]), f'Stale report: {name}'
    assert int(r.get('tests')) > 0 and all(int(r.get(key)) == 0 for key in ('failures', 'errors', 'skipped')), \
        f'Incomplete run: {name}'
    total += int(r.get('tests'))
print('Verified', total, 'Elasticsearch contract tests with no skips')
PY
