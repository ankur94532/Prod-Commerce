"""Deterministic fixtures for graded_eval tests.

Every label, query, and catalog row here is SYNTHETIC_FIXTURE data that exists only to
exercise the harness. None of it is a shopper query, a human judgment, or evidence of
retrieval quality. Scores produced from these fixtures must never be reported as
evaluation results.
"""
from __future__ import annotations

import http.server
import json
import pathlib
import sys
import threading
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import graded_eval  # noqa: E402

MODE_INDEX = {mode: index for index, mode in enumerate(graded_eval.MODES)}
RETRIEVAL = {
    'text': {'mode': 'text', 'algorithm': 'lexical_with_rules', 'totalRelation': 'exact',
             'candidateWindow': 0, 'rrfRankConstant': 0, 'keywordWeight': 1.0, 'vectorWeight': 0.0,
             'collapseField': None},
    'vector': {'mode': 'vector', 'algorithm': 'hnsw_cosine', 'totalRelation': 'ann_candidates',
               'candidateWindow': 0, 'rrfRankConstant': 0, 'keywordWeight': 0.0, 'vectorWeight': 1.0,
               'collapseField': None},
    'hybrid': {'mode': 'hybrid', 'algorithm': 'lexically_gated_weighted_cosine', 'totalRelation': 'exact',
               'candidateWindow': 0, 'rrfRankConstant': 0, 'keywordWeight': 1.0, 'vectorWeight': 1.5,
               'collapseField': None},
    'hybrid_rrf': {'mode': 'hybrid_rrf', 'algorithm': 'rrf_union_hnsw_vector', 'totalRelation': 'candidate_union',
                   'candidateWindow': 100, 'rrfRankConstant': 60, 'keywordWeight': 1.0, 'vectorWeight': 1.5,
                   'collapseField': None},
}

# The same server with result collapsing switched on. One result is then a product family
# rather than a document, and the total counts families, so the harness has to accept it as a
# different -- and separately pinned -- configuration rather than as drift.
COLLAPSED_TOTAL_RELATION = {'text': 'collapsed_groups_approximate',
                            'vector': 'collapsed_ann_groups_approximate',
                            'hybrid': 'collapsed_groups_approximate',
                            'hybrid_rrf': 'collapsed_candidate_union'}
COLLAPSED_RETRIEVAL = {
    mode: {**info, 'totalRelation': COLLAPSED_TOTAL_RELATION[mode], 'collapseField': 'productFamily'}
    for mode, info in RETRIEVAL.items()
}


def product(pid, name, category, price, stock, brand='acme', **extra):
    return {'id': pid, 'slug': f'{pid}-slug', 'name': name,
            'description': f'Fixture description for {name}', 'brand': brand,
            'categorySlug': category, 'price': price, 'currency': 'INR',
            'stockQuantity': stock, **extra}


CATALOG = [
    product('p1', 'Quiet commuter headset', 'earbuds-headphones', 40, 5, color='black'),
    product('p2', 'Passive ear protectors', 'earbuds-headphones', 20, 5, color='black'),
    product('p3', 'Premium studio headset', 'earbuds-headphones', 90, 5, color='silver'),
    product('p4', 'Unavailable travel headset', 'earbuds-headphones', 15, 0, color='black'),
    product('p5', 'Quiet journeys journal', 'books-stationery', 12, 5, color='white'),
    product('p6', 'Quiet journeys diary', 'books-stationery', 18, 2, color='white'),
]

CONFIG = {
    'index_identity': 'fixture-index-not-a-real-deployment',
    'embedding_model': 'fixture-deterministic',
    'embedding_revision': 'unknown_unverified',
    'cache_regime': 'disabled_for_fixture',
    'corpus_provenance': 'synthetic_fixture_catalog',
}


def query(qid, text, family, split, kind, intent, **filters):
    return {'query_id': qid, 'query': text, 'intent': intent, 'kind': kind,
            'family_id': family, 'split': split, 'filters': filters, 'sort': 'relevance'}


QUERIES = {
    'schema_version': 1,
    'provenance': {'author_type': 'synthetic_fixture',
                   'source': 'unit-test fixtures; not shopper queries and not an evaluation set'},
    'queries': [
        query('q1', 'something quiet for a noisy commute', 'f1', 'test', 'semantic_intent',
              'wants noise isolation without naming it'),
        query('q2', 'quiet headset', 'f1', 'test', 'semantic_intent',
              'same need, lexical phrasing', category='earbuds-headphones'),
        query('q3', 'affordable in stock headset', 'f2', 'test', 'constraint',
              'hard price and availability constraints', maxPrice=50, inStock=True),
        query('q4', 'headset over ten', 'f2', 'test', 'constraint',
              'lower price bound only', minPrice=10),
        query('q5', 'qiuet jurnal', 'f3', 'dev', 'typo', 'misspelled stationery need'),
        query('q6', 'quiet journal', 'f3', 'dev', 'typo', 'corrected stationery need'),
    ],
}


class StubError(Exception):
    def __init__(self, status):
        super().__init__(f'stub HTTP {status}')
        self.status = status


class Stub:
    """Deterministic catalog/search server. Behavior flags drive failure-path tests."""

    def __init__(self, catalog=None):
        self.catalog = [dict(row) for row in (catalog or CATALOG)]
        self.catalog_reads = 0
        self.search_calls = 0
        self.behavior = {
            'search_status': None,      # int -> respond with that HTTP error
            'fail_after': None,         # int -> start failing once this many searches have run
            'drop_retrieval': False,    # simulate an older server with no retrieval metadata
            'wrong_algorithm': False,   # simulate silent algorithm fallback
            'mutate_catalog': False,    # change the catalog between snapshot reads
            'inflate_total': False,     # report a total that does not match returned items
            'shift_config': False,      # change retrieval config partway through a run
            'stale_price': False,       # return prices that disagree with the snapshot
            'extra_product_field': None,  # str -> a field the catalog did not previously publish
            'reprice': None,            # (product_id, price) -> a genuine product data change
        }

    def rows(self):
        """The catalog this server currently holds.

        A later snapshot of the same catalog: a field the API did not used to publish, and a
        product whose own details actually changed. Carrying judgments across the two has to
        treat those differently. Search reads the same rows, so a repriced product stays
        internally consistent and the run is not refused for a stale-price mismatch instead.
        """
        rows = [dict(row) for row in self.catalog]
        if self.behavior['extra_product_field']:
            for row in rows:
                row[self.behavior['extra_product_field']] = 'group-' + str(row['id'])
        if self.behavior['reprice']:
            product_id, price = self.behavior['reprice']
            for row in rows:
                if row['id'] == product_id:
                    row['price'] = price
        return rows

    def products(self, params):
        page, size = int(params.get('page', 0)), int(params.get('size', 200))
        if page == 0:
            self.catalog_reads += 1
        rows = self.rows()
        if self.behavior['mutate_catalog'] and self.catalog_reads > 1:
            rows[0]['price'] = rows[0]['price'] + 1
        window = rows[page * size:(page + 1) * size]
        return {'page': page, 'size': size, 'data': window,
                'totalElements': len(rows), 'totalPages': max(1, -(-len(rows) // size))}

    def search(self, params):
        self.search_calls += 1
        status = self.behavior['search_status']
        limit = self.behavior['fail_after']
        if status and (limit is None or self.search_calls > limit):
            raise StubError(status)
        mode, depth = params['mode'], int(params['size'])
        filters = {}
        for key, value in params.items():
            if key in ('minPrice', 'maxPrice'):
                filters[key] = float(value)
            elif key == 'inStock':
                filters[key] = value == 'true'
            elif key in graded_eval.FILTERS:
                filters[key] = value
        matched = [row for row in self.rows() if not graded_eval.violates_filters(row, filters)]
        rotation = (len(params['q']) + MODE_INDEX[mode]) % max(1, len(matched))
        ranked = matched[rotation:] + matched[:rotation]
        drift = 1 if self.behavior['stale_price'] else 0
        items = [{'id': row['id'], 'slug': row['slug'], 'name': row['name'], 'price': row['price'] + drift}
                 for row in ranked[:depth]]
        retrieval = dict(RETRIEVAL[mode])
        if self.behavior['wrong_algorithm']:
            retrieval['algorithm'] = 'silently_downgraded'
        if self.behavior['shift_config'] and self.search_calls > 4:
            retrieval['keywordWeight'] = 9.0
        total = len(matched) + (5 if self.behavior['inflate_total'] else 0)
        response = {'items': items, 'total': total, 'page': 0, 'size': depth,
                    'totalPages': max(1, -(-total // depth))}
        if not self.behavior['drop_retrieval']:
            response['retrieval'] = retrieval
        return response


class _Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        params = dict(urllib.parse.parse_qsl(parsed.query))
        stub = self.server.stub
        try:
            if parsed.path == '/api/v1/products':
                payload = stub.products(params)
            elif parsed.path == '/api/v1/search':
                payload = stub.search(params)
            else:
                self.send_error(404)
                return
        except StubError as error:
            self.send_response(error.status)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"error":"stub failure"}')
            return
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class StubServer:
    def __init__(self, stub=None):
        self.stub = stub or Stub()

    def __enter__(self):
        self.httpd = http.server.ThreadingHTTPServer(('127.0.0.1', 0), _Handler)
        self.httpd.stub = self.stub
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f'http://127.0.0.1:{self.httpd.server_address[1]}'
        return self

    def __exit__(self, *exc):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=5)
        return False


def write_inputs(directory):
    """Write frozen query/config inputs; returns their paths."""
    directory = pathlib.Path(directory)
    queries = directory / 'queries.json'
    config = directory / 'config.json'
    graded_eval.write_json(queries, QUERIES)
    graded_eval.write_json(config, CONFIG)
    return queries, config


def fixture_judgments(pool_path, manifest, assessor='fixture-assessor-a', offset=0):
    """Deterministic SYNTHETIC_FIXTURE grades. Not human labels; never report these scores."""
    pool = graded_eval.read_json(pool_path)
    rows = []
    for pair in pool['pairs']:
        grade = (int(pair['product_id'][1:]) + offset) % 4
        rows.append({'query_id': pair['query_id'], 'product_id': pair['product_id'], 'grade': grade,
                     'assessor_id': assessor, 'assessor_type': 'synthetic_fixture',
                     'rationale': 'deterministic fixture grade, not a judgment',
                     'pool_id': pool['pool_id'], 'rubric_version': graded_eval.RUBRIC,
                     'query_sha256': manifest['query_sha256'],
                     'catalog_sha256': manifest['catalog_sha256']})
    return rows
