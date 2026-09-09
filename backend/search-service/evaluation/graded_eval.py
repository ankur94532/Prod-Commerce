#!/usr/bin/env python3
"""Frozen-run, pooled graded evaluation. No catalog-derived or automatic relevance labels."""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import itertools
import json
import math
import pathlib
import random
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MODES = ('text', 'vector', 'hybrid', 'hybrid_rrf')
FILTERS = {'category', 'brand', 'minPrice', 'maxPrice', 'inStock', 'color', 'type', 'fit', 'storage', 'memory', 'material'}
TYPES = {'human', 'ai', 'synthetic_fixture'}
RUBRIC = 'shopper-graded-v1'
# Fields that describe how a product was retrieved or ranked, never what it is.
# They must never reach a reviewer, or judgments become a vote on the ranker.
DERIVED = {'score', '_score', 'relevanceScore', 'rank', 'position', 'embedding', 'searchEmbedding',
           'vector', 'highlight', 'highlights', 'sortValues', 'explanation'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def numeric(value, message, integral=False):
    require(type(value) in (int, float) or isinstance(value, str), message)
    try:
        number = float(value)
    except ValueError:
        raise ValueError(message)
    require(math.isfinite(number) and number >= 0, message)
    require(not integral or number == int(number), message)
    return number


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def read_json(path):
    return json.loads(pathlib.Path(path).read_text())


def write_json(path, value):
    # Exclusive creation prevents accidental replacement of frozen evidence or judgments.
    with pathlib.Path(path).open('x') as out:
        json.dump(value, out, ensure_ascii=False, indent=2, allow_nan=False)
        out.write('\n')


def read_jsonl(path):
    return [json.loads(line) for line in pathlib.Path(path).read_text().splitlines() if line.strip()]


def write_jsonl(path, rows):
    with pathlib.Path(path).open('x') as out:
        for row in rows:
            out.write(canonical(row).decode() + '\n')


def validate_queries(dataset):
    require(dataset.get('schema_version') == 1, 'Unsupported query schema')
    provenance = dataset.get('provenance', {})
    require(provenance.get('author_type') in TYPES and provenance.get('source'), 'Query provenance is required')
    queries = dataset.get('queries', [])
    require(queries, 'No queries')
    ids, families = set(), {}
    for q in queries:
        require(q.get('query_id') and q['query_id'] not in ids, 'Duplicate/missing query ID')
        ids.add(q['query_id'])
        require(q.get('query', '').strip() and q.get('intent') and q.get('kind') and q.get('family_id'), 'Query/intent/kind/family required')
        require(q.get('split') in ('dev', 'test'), 'Split must be dev or test')
        family = q['family_id']
        require(family not in families or families[family] == q['split'], 'Query family leaks across dev/test splits')
        families[family] = q['split']
        filters = q.get('filters', {})
        require(isinstance(filters, dict) and not (set(filters) - FILTERS), 'Unknown filter')
        for field, value in filters.items():
            if field in ('minPrice', 'maxPrice'):
                require(type(value) in (int, float) and math.isfinite(value) and value >= 0, 'Invalid price filter')
            elif field == 'inStock':
                require(type(value) is bool, 'inStock must be a boolean')
            else:
                require(isinstance(value, str) and value.strip(), 'Invalid term filter')
        require(filters.get('minPrice', 0) <= filters.get('maxPrice', math.inf), 'Invalid price range')
        require(q.get('sort', 'relevance') == 'relevance', 'Relevance ablations require relevance sort')
    return queries


def validate_catalog(rows):
    ids = set()
    for row in rows:
        require(row.get('id') is not None and str(row['id']) not in ids, 'Duplicate/missing catalog ID')
        ids.add(str(row['id']))
        require(row.get('name') and row.get('currency') and row.get('price') is not None, 'Catalog name/currency/price required')
        require(row.get('description') is not None, 'Snapshot must include descriptions for judgment')
        require(row.get('stockQuantity') is not None, 'Snapshot must include stockQuantity')
        numeric(row['price'], 'Catalog price must be a finite non-negative number')
        numeric(row['stockQuantity'], 'Catalog stockQuantity must be a non-negative whole number', integral=True)
    require(rows, 'Catalog snapshot is empty')
    return {str(row['id']): row for row in rows}


def violates_filters(product, filters):
    for key, expected in filters.items():
        if key == 'minPrice' and float(product['price']) < expected:
            return True
        if key == 'maxPrice' and float(product['price']) > expected:
            return True
        if key == 'inStock' and expected and product['stockQuantity'] <= 0:
            return True
        if key not in ('minPrice', 'maxPrice', 'inStock'):
            actual = product.get('categorySlug', product.get('category')) if key == 'category' else product.get(key, product.get('attributes', {}).get(key))
            if str(actual).casefold() != str(expected).casefold():
                return True
    return False


def validate_response(response, mode, depth):
    require(isinstance(response, dict) and isinstance(response.get('items'), list), 'Invalid search response')
    info = response.get('retrieval')
    require(isinstance(info, dict) and info.get('mode') == mode, 'Missing/mismatched retrieval metadata (old server or fallback)')
    algorithms = {'text': 'lexical_with_rules', 'vector': 'exact_cosine', 'hybrid': 'lexically_gated_weighted_cosine', 'hybrid_rrf': 'rrf_union_exact_vector'}
    require(info.get('algorithm') == algorithms[mode], 'Unexpected algorithm')
    require(info.get('totalRelation') == ('candidate_union' if mode == 'hybrid_rrf' else 'exact'), 'Unknown total relation')
    if mode == 'hybrid_rrf':
        require(type(info.get('candidateWindow')) is int and info['candidateWindow'] >= depth, 'RRF candidate window smaller than collection depth')
        require(type(info.get('rrfRankConstant')) is int and info['rrfRankConstant'] > 0, 'Invalid RRF constant')
    require(response.get('page') == 0 and response.get('size') == depth, 'Server changed page/depth')
    ids = [str(item['id']) for item in response['items']]
    require(len(ids) == len(set(ids)) and len(ids) <= depth, 'Duplicate results or excess depth')
    require(type(response.get('total')) is int and response['total'] >= len(ids), 'Invalid total')
    require(len(ids) == min(response['total'], depth), 'Truncated response')
    return ids


def cross_check_items(items, catalog):
    # A result whose fields disagree with the snapshot means reviewers would judge stale
    # product data, so refuse the run instead of scoring it.
    for item in items:
        pid = str(item['id'])
        require(pid in catalog, 'Result ID absent from frozen catalog')
        product = catalog[pid]
        for field in ('name', 'slug'):
            if item.get(field) is not None and product.get(field) is not None:
                require(str(item[field]) == str(product[field]), f'Result {field} disagrees with frozen catalog; resnapshot')
        if item.get('price') is not None:
            require(abs(numeric(item['price'], 'Invalid result price') - float(product['price'])) < 1e-9,
                    'Result price disagrees with frozen catalog; resnapshot')


def search_params(query, mode, depth):
    params = {'q': query['query'], **query.get('filters', {}), 'mode': mode,
              'sort': 'relevance', 'page': 0, 'size': depth}
    return {key: str(value).lower() if isinstance(value, bool) else value for key, value in params.items()}


def fetch(url, timeout):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.load(response)


def snapshot(args):
    require(args.timeout > 0, 'Timeout must be positive')
    def read_all():
        rows, page, pages, total = [], 0, 1, None
        while page < pages:
            response = fetch(args.base_url.rstrip('/') + f'/api/v1/products?page={page}&size=200&sort=newest', args.timeout)
            require(response.get('page') == page and isinstance(response.get('data'), list), 'Invalid catalog pagination')
            require(type(response.get('totalPages')) is int and type(response.get('totalElements')) is int, 'Missing catalog totals')
            require(total is None or total == response['totalElements'], 'Catalog changed during export')
            total, pages = response['totalElements'], response['totalPages']
            require(page == 0 or response['data'], 'Empty intermediate catalog page')
            rows.extend(response['data'])
            page += 1
        require(len(rows) == total, 'Catalog export incomplete')
        validate_catalog(rows)
        return sorted(rows, key=lambda row: str(row['id']))
    first, second = read_all(), read_all()
    require(canonical(first) == canonical(second), 'Catalog changed between reads; freeze writes and retry')
    write_jsonl(args.out, first)
    write_json(str(args.out) + '.metadata.json', {
        'products': len(first), 'sha256': sha(pathlib.Path(args.out).read_bytes()),
        'consistency': 'two_equal_paginated_reads_not_a_database_snapshot',
        'scope': 'active products exposed by catalog API', 'corpus_provenance': args.provenance,
        'recorded_at_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
    })
    return 0


def collect(args):
    dataset = read_json(args.queries)
    queries = validate_queries(dataset)
    catalog_rows = read_jsonl(args.catalog)
    catalog = validate_catalog(catalog_rows)
    config = read_json(args.config)
    for key in ('index_identity', 'embedding_model', 'embedding_revision', 'cache_regime', 'corpus_provenance'):
        require(config.get(key), f'Run config requires {key}; use explicit unknown if unverified')
    require(1 <= args.depth <= 100 and args.timeout > 0, 'Depth must be 1–100; timeout must be positive')
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=False)
    write_json(out / 'queries.json', dataset)
    write_jsonl(out / 'catalog.jsonl', catalog_rows)
    write_json(out / 'config.json', config)
    rng = random.Random(args.seed)
    order = list(queries)
    rng.shuffle(order)
    rows, metadata = [], {}
    for q in order:
        modes = list(MODES)
        rng.shuffle(modes)
        for mode in modes:
            params = search_params(q, mode, args.depth)
            url = args.base_url.rstrip('/') + '/api/v1/search?' + urllib.parse.urlencode(params)
            row = {'query_id': q['query_id'], 'mode': mode, 'params': params, 'http_status': None}
            started = time.perf_counter()
            response = None
            try:
                response = fetch(url, args.timeout)
                row['http_status'] = 200
                ids = validate_response(response, mode, args.depth)
                cross_check_items(response['items'], catalog)
                require(all(not violates_filters(catalog[pid], q.get('filters', {})) for pid in ids), 'Result violates explicit filter against frozen catalog')
                require(mode not in metadata or metadata[mode] == response['retrieval'], 'Retrieval configuration changed during run')
                metadata[mode] = response['retrieval']
                row.update(response=response, status='ok')
            except urllib.error.HTTPError as error:
                row.update(status='error', http_status=error.code, error_type='http_error')
            except Exception as error:
                row.update(status='error', error_type=type(error).__name__, error=str(error))
            if row['status'] == 'error' and response is not None:
                # Keep the rejected body: diagnosing a contract break needs the response itself.
                row['invalid_response'] = response
            row['elapsed_ms'] = (time.perf_counter() - started) * 1000
            rows.append(row)
            # Append each completed request so interrupted runs retain diagnostic evidence.
            with (out / 'requests.jsonl').open('a') as log:
                log.write(canonical(row).decode() + '\n')
    manifest = {
        'schema_version': 1, 'recorded_at_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
        'query_sha256': sha((out / 'queries.json').read_bytes()),
        'catalog_sha256': sha((out / 'catalog.jsonl').read_bytes()),
        'config_sha256': sha((out / 'config.json').read_bytes()),
        'requests_sha256': sha((out / 'requests.jsonl').read_bytes()),
        'collector_sha256': sha(pathlib.Path(__file__).read_bytes()),
        'depth': args.depth, 'seed': args.seed, 'modes': list(MODES), 'retrieval': metadata,
        'attempted': len(rows), 'errors': sum(row['status'] != 'ok' for row in rows),
        'index_and_model_identity': 'operator_declared_not_verified_by_collector',
        'cache_regime': config['cache_regime'],
        'corpus_provenance': config['corpus_provenance'],
        'operator_config': config,
        'latency_scope': 'single-pass serial HTTP observations; randomized mode order; not a throughput benchmark',
        'query_provenance': dataset['provenance'],
    }
    write_json(out / 'manifest.json', manifest)
    print(json.dumps({'run': str(out), 'attempted': manifest['attempted'], 'errors': manifest['errors']}))
    return 1 if manifest['errors'] else 0


def load_run(directory):
    directory = pathlib.Path(directory)
    manifest = read_json(directory / 'manifest.json')
    for field, name in [('query_sha256', 'queries.json'), ('catalog_sha256', 'catalog.jsonl'), ('config_sha256', 'config.json'), ('requests_sha256', 'requests.jsonl')]:
        require(sha((directory / name).read_bytes()) == manifest[field], f'Frozen file changed: {name}')
    dataset = read_json(directory / 'queries.json')
    queries = validate_queries(dataset)
    catalog = validate_catalog(read_jsonl(directory / 'catalog.jsonl'))
    require(set(manifest['modes']) == set(MODES) and len(manifest['modes']) == len(MODES), 'Run must contain all ablations')
    rows = read_jsonl(directory / 'requests.jsonl')
    require(manifest['errors'] == 0 and all(row['status'] == 'ok' for row in rows), 'Run contains failed requests; fix and recollect, do not score errors as zero hits')
    require(len(rows) == len(queries) * len(MODES) == manifest['attempted'], 'Run is incomplete')
    keys = [(row['query_id'], row['mode']) for row in rows]
    require(len(set(keys)) == len(keys), 'Duplicate query/mode run')
    require(set(keys) == set(itertools.product([q['query_id'] for q in queries], MODES)), 'Query/mode mismatch')
    by_query = {q['query_id']: q for q in queries}
    for row in rows:
        ids = validate_response(row['response'], row['mode'], manifest['depth'])
        require(row['response']['retrieval'] == manifest['retrieval'][row['mode']], 'Configuration changed')
        q = by_query[row['query_id']]
        require(row['params'] == search_params(q, row['mode'], manifest['depth']),
                'Recorded request parameters do not match the frozen query')
        cross_check_items(row['response']['items'], catalog)
        require(all(not violates_filters(catalog[pid], q.get('filters', {})) for pid in ids), 'Result/catalog contract mismatch')
    return manifest, queries, catalog, rows


def pool_data(directory):
    manifest, queries, catalog, rows = load_run(directory)
    pairs = sorted({(row['query_id'], str(item['id'])) for row in rows for item in row['response']['items']})
    identity = {'query_sha256': manifest['query_sha256'], 'catalog_sha256': manifest['catalog_sha256'], 'pairs': pairs, 'rubric_version': RUBRIC}
    return sha(canonical(identity)), pairs, manifest, queries, catalog, rows


def export_pool(args):
    pool_id, pairs, manifest, queries, catalog, _ = pool_data(args.run)
    qmap = {q['query_id']: q for q in queries}
    rng = random.Random(args.seed)
    rng.shuffle(pairs)
    # No rank, score, mode, or split in reviewer rows. All judgments start blank.
    pool, template = [], []
    for qid, pid in pairs:
        q = qmap[qid]
        reviewed = {key: value for key, value in catalog[pid].items() if key not in DERIVED}
        pool.append({'query_id': qid, 'product_id': pid, 'query': q['query'], 'intent': q['intent'],
                     'filters': q.get('filters', {}), 'product': reviewed})
        template.append({'query_id': qid, 'product_id': pid, 'grade': None, 'assessor_id': None,
                         'assessor_type': None, 'rationale': '', 'pool_id': pool_id,
                         'query_sha256': manifest['query_sha256'], 'catalog_sha256': manifest['catalog_sha256'],
                         'rubric_version': RUBRIC})
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=False)
    write_json(out / 'pool.json', {'pool_id': pool_id, 'rubric_version': RUBRIC, 'pairs': pool})
    write_jsonl(out / 'judgments.template.jsonl', template)
    print(f'Exported {len(pool)} blinded pairs with blank judgments to {out}')
    return 0


def load_judgments(path, pool_id, manifest, pairs):
    labels = {}
    assessor_types = {}
    allowed = set(pairs)
    for row in read_jsonl(path):
        require(row.get('pool_id') == pool_id and row.get('rubric_version') == RUBRIC, 'Judgment pool/rubric mismatch')
        require(all(row.get(key) == manifest[key] for key in ('query_sha256', 'catalog_sha256')), 'Judgment snapshot mismatch')
        require(type(row.get('grade')) is int and 0 <= row['grade'] <= 3, 'Every judgment requires an integer grade 0–3; blank is not zero')
        require(row.get('assessor_type') in TYPES and row.get('assessor_id') and row.get('rationale', '').strip(), 'Assessor provenance and rationale required')
        pair = (row['query_id'], str(row['product_id']))
        require(pair in allowed, 'Judgment pair outside frozen pool')
        assessor = row['assessor_id']
        require(assessor not in assessor_types or assessor_types[assessor] == row['assessor_type'], 'Assessor type changed')
        assessor_types[assessor] = row['assessor_type']
        key = (assessor, *pair)
        require(key not in labels, 'Duplicate judgment; adjudicate explicitly')
        labels[key] = row['grade']
    return labels, assessor_types


def dcg(grades):
    return sum((2 ** grade - 1) / math.log2(rank + 2) for rank, grade in enumerate(grades))


def metrics(ids, judgments, k):
    require(len(ids) == len(set(ids)), 'Duplicate result IDs')
    require(all(pid in judgments for pid in ids[:k]), 'Unjudged result; expand pool')
    grades = [judgments[pid] for pid in ids[:k]]
    ideal = dcg(sorted(judgments.values(), reverse=True)[:k])
    hits = sum(grade >= 2 for grade in grades)
    relevant = sum(grade >= 2 for grade in judgments.values())
    return {
        f'pooled_ndcg@{k}': dcg(grades) / ideal if ideal else None,
        f'precision@{k}': hits / k,
        f'pooled_recall@{k}': hits / relevant if relevant else None,
        f'mrr@{k}': next((1 / rank for rank, grade in enumerate(grades, 1) if grade >= 2), 0.0),
    }


def mean_present(values):
    values = [value for value in values if value is not None]
    return statistics.mean(values) if values else None


def interval(values):
    ordered = sorted(values)
    def quantile(p):
        position = (len(ordered) - 1) * p
        low = math.floor(position)
        return ordered[low] + (ordered[math.ceil(position)] - ordered[low]) * (position - low)
    return [quantile(.025), quantile(.975)]


def paired_bootstrap(deltas, seed=42, samples=2000):
    # Resample query families, keeping variants together; point estimate is the query macro mean.
    families = sorted(deltas)
    values = [value for family in families for value in deltas[family]]
    if not values:
        return {'mean_delta': None, 'ci95': None, 'families': 0, 'queries': 0}
    if len(families) < 2:
        return {'mean_delta': statistics.mean(values), 'ci95': None, 'families': len(families), 'queries': len(values)}
    rng = random.Random(seed)
    draws = []
    for _ in range(samples):
        selected = [value for family in rng.choices(families, k=len(families)) for value in deltas[family]]
        draws.append(statistics.mean(selected))
    return {'mean_delta': statistics.mean(values), 'ci95': interval(draws), 'families': len(families), 'queries': len(values)}


def evaluate(args):
    require(args.k > 0, 'k must be positive')
    pool_id, pairs, manifest, queries, catalog, rows = pool_data(args.run)
    require(args.k <= manifest['depth'], 'k exceeds collected depth')
    labels, types = load_judgments(args.judgments, pool_id, manifest, pairs)
    require(args.assessor in types, 'Unknown assessor')
    selected = {q['query_id']: q for q in queries if q['split'] == args.split}
    require(selected, 'Split has no queries')
    for qid, pid in pairs:
        if qid in selected:
            require((args.assessor, qid, pid) in labels, f'Incomplete judgment pool for {qid}/{pid}')
            require(not violates_filters(catalog[pid], selected[qid].get('filters', {})) or labels[args.assessor, qid, pid] == 0, 'Hard constraint violation must be grade 0')
    per_query = []
    for row in rows:
        qid = row['query_id']
        if qid not in selected:
            continue
        judgment = {pid: grade for (who, query_id, pid), grade in labels.items() if who == args.assessor and query_id == qid}
        ids = [str(item['id']) for item in row['response']['items']]
        per_query.append({'query_id': qid, 'family_id': selected[qid]['family_id'], 'kind': selected[qid]['kind'], 'mode': row['mode'],
                          **metrics(ids, judgment, args.k), 'elapsed_ms': row['elapsed_ms'], 'returned': len(ids)})
    ndcg_key = f'pooled_ndcg@{args.k}'
    def summarize(group):
        return {'queries': len(group), 'ndcg_scorable_queries': sum(r[ndcg_key] is not None for r in group),
                **{key: mean_present([r[key] for r in group]) for key in (ndcg_key, f'precision@{args.k}', f'pooled_recall@{args.k}', f'mrr@{args.k}')},
                'zero_result_queries': sum(r['returned'] == 0 for r in group), 'mean_observed_latency_ms': mean_present([r['elapsed_ms'] for r in group])}
    by_mode = {mode: summarize([r for r in per_query if r['mode'] == mode]) for mode in MODES}
    by_kind = {kind: {mode: summarize([r for r in per_query if r['kind'] == kind and r['mode'] == mode]) for mode in MODES} for kind in sorted({r['kind'] for r in per_query})}
    baseline = {r['query_id']: r for r in per_query if r['mode'] == 'text'}
    paired = {}
    for mode in MODES[1:]:
        deltas = collections.defaultdict(list)
        for r in per_query:
            if r['mode'] == mode and r[ndcg_key] is not None and baseline[r['query_id']][ndcg_key] is not None:
                deltas[r['family_id']].append(r[ndcg_key] - baseline[r['query_id']][ndcg_key])
        paired[mode + '_minus_text'] = paired_bootstrap(deltas, args.seed)
    report = {'schema_version': 1, 'evidence_class': types[args.assessor] + '_judged_pooled_evaluation',
              'assessor_id': args.assessor, 'assessor_type_is_declared_not_authenticated': True,
              'split': args.split, 'query_provenance': manifest['query_provenance'], 'pool_id': pool_id,
              'query_sha256': manifest['query_sha256'], 'catalog_sha256': manifest['catalog_sha256'],
              'judgments_sha256': sha(pathlib.Path(args.judgments).read_bytes()), 'manifest': manifest,
              'definitions': {'gain': '2^grade - 1', 'binary_relevant': 'grade >= 2', 'precision_denominator': args.k,
                              'ndcg_ideal': 'all judged products in the pooled candidate set, not the full catalog',
                              'no_positive_gain': 'NDCG is null and excluded with scorable count reported',
                              'recall': 'pool-bounded; not catalog recall', 'uncertainty': 'paired query-family bootstrap, 2000 resamples; small sets remain exploratory'},
              'by_mode': by_mode, 'by_kind': by_kind, 'paired_ndcg_differences': paired, 'per_query': per_query}
    write_json(args.out, report)
    print(f'Wrote {report["evidence_class"]}: {args.out}')
    return 0


def agreement(args):
    pool_id, pairs, manifest, _, _, _ = pool_data(args.run)
    labels, types = load_judgments(args.judgments, pool_id, manifest, pairs)
    require(args.first != args.second and args.first in types and args.second in types, 'Two distinct known assessors required')
    shared = [(labels[args.first, *pair], labels[args.second, *pair]) for pair in pairs if (args.first, *pair) in labels and (args.second, *pair) in labels]
    require(shared, 'No double-judged pairs')
    n = len(shared)
    observed = sum(abs(a - b) / 3 for a, b in shared) / n
    left, right = collections.Counter(a for a, _ in shared), collections.Counter(b for _, b in shared)
    expected = sum(left[a] * right[b] * abs(a - b) / 3 for a in range(4) for b in range(4)) / n ** 2
    result = {'assessors': {args.first: types[args.first], args.second: types[args.second]}, 'pool_id': pool_id,
              'shared_pairs': n, 'pool_pairs': len(pairs), 'coverage': n / len(pairs),
              'exact_agreement': sum(a == b for a, b in shared) / n,
              'linear_weighted_cohen_kappa': 1 - observed / expected if expected else None,
              'note': 'Agreement measures consistency, not truth; degenerate kappa is null.'}
    write_json(args.out, result)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    command = sub.add_parser('validate')
    command.add_argument('--queries', required=True)
    command.set_defaults(func=lambda a: (validate_queries(read_json(a.queries)), print('Query schema/provenance/splits valid'), 0)[2])
    command = sub.add_parser('snapshot')
    command.add_argument('--base-url', default='http://localhost:8082')
    command.add_argument('--out', required=True)
    command.add_argument('--provenance', required=True, help='For example: synthetic_catalog')
    command.add_argument('--timeout', type=float, default=15)
    command.set_defaults(func=snapshot)
    command = sub.add_parser('collect')
    for key in ('queries', 'catalog', 'config', 'out'):
        command.add_argument('--' + key, required=True)
    command.add_argument('--base-url', default='http://localhost:8084')
    command.add_argument('--depth', type=int, default=100)
    command.add_argument('--timeout', type=float, default=15)
    command.add_argument('--seed', type=int, default=42)
    command.set_defaults(func=collect)
    command = sub.add_parser('pool')
    command.add_argument('--run', required=True)
    command.add_argument('--out', required=True)
    command.add_argument('--seed', type=int, default=42)
    command.set_defaults(func=export_pool)
    command = sub.add_parser('evaluate')
    for key in ('run', 'judgments', 'assessor', 'out'):
        command.add_argument('--' + key, required=True)
    command.add_argument('--split', choices=['dev', 'test'], default='test')
    command.add_argument('--k', type=int, default=10)
    command.add_argument('--seed', type=int, default=42)
    command.set_defaults(func=evaluate)
    command = sub.add_parser('agreement')
    for key in ('run', 'judgments', 'first', 'second', 'out'):
        command.add_argument('--' + key, required=True)
    command.set_defaults(func=agreement)
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f'Evaluation refused: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
