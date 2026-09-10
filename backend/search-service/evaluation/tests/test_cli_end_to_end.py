"""End-to-end CLI tests: snapshot -> collect -> pool -> judge -> evaluate/agreement.

The judgments used here are deterministic SYNTHETIC_FIXTURE labels produced by the test
support module. The resulting metric values are meaningless as relevance evidence and
must never be quoted as evaluation results; only the harness behavior is under test.
"""
from __future__ import annotations

import json
import pathlib
import tempfile
import unittest

import support
from support import graded_eval, StubServer, Stub


class EndToEndCase(unittest.TestCase):
    depth = 5
    k = 5

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = pathlib.Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        self.queries, self.config = support.write_inputs(self.tmp)

    def snapshot(self, server, out='catalog.jsonl'):
        target = self.tmp / out
        return graded_eval.main(['snapshot', '--base-url', server.base_url, '--out', str(target),
                                 '--provenance', 'synthetic_fixture_catalog']), target

    def collect(self, server, catalog, out='run', depth=None):
        run = self.tmp / out
        code = graded_eval.main(['collect', '--base-url', server.base_url, '--queries', str(self.queries),
                                 '--catalog', str(catalog), '--config', str(self.config),
                                 '--out', str(run), '--depth', str(depth or self.depth)])
        return code, run

    def pool(self, run, out='pool'):
        target = self.tmp / out
        code = graded_eval.main(['pool', '--run', str(run), '--out', str(target)])
        return code, target

    def collected_run(self, stub=None, name='run'):
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server, out=f'catalog-{name}.jsonl')
            self.assertEqual(code, 0)
            code, run = self.collect(server, catalog, out=name)
            self.assertEqual(code, 0)
        return run

    def judged_run(self, offsets=((0, 'fixture-assessor-a'),)):
        run = self.collected_run()
        code, pool = self.pool(run)
        self.assertEqual(code, 0)
        manifest = graded_eval.read_json(run / 'manifest.json')
        rows = []
        for offset, assessor in offsets:
            rows.extend(support.fixture_judgments(pool / 'pool.json', manifest, assessor, offset))
        judgments = self.tmp / 'judgments.jsonl'
        graded_eval.write_jsonl(judgments, rows)
        return run, pool, judgments, manifest


class SnapshotTest(EndToEndCase):
    def test_snapshot_writes_catalog_and_provenance_metadata(self):
        with StubServer() as server:
            code, target = self.snapshot(server)
        self.assertEqual(code, 0)
        rows = graded_eval.read_jsonl(target)
        self.assertEqual([row['id'] for row in rows], sorted(row['id'] for row in support.CATALOG))
        metadata = graded_eval.read_json(str(target) + '.metadata.json')
        self.assertEqual(metadata['products'], len(support.CATALOG))
        self.assertEqual(metadata['corpus_provenance'], 'synthetic_fixture_catalog')
        self.assertIn('not_a_database_snapshot', metadata['consistency'])
        self.assertEqual(metadata['sha256'], graded_eval.sha(target.read_bytes()))

    def test_catalog_changing_between_reads_is_refused(self):
        stub = Stub()
        stub.behavior['mutate_catalog'] = True
        with StubServer(stub) as server:
            code, target = self.snapshot(server)
        self.assertEqual(code, 2)
        self.assertFalse(target.exists())

    def test_snapshot_refuses_to_overwrite_an_existing_snapshot(self):
        with StubServer() as server:
            self.assertEqual(self.snapshot(server)[0], 0)
            self.assertEqual(self.snapshot(server)[0], 2)


class CollectTest(EndToEndCase):
    def test_collect_freezes_inputs_and_records_every_query_mode_pair(self):
        run = self.collected_run()
        manifest = graded_eval.read_json(run / 'manifest.json')
        self.assertEqual(manifest['attempted'], len(support.QUERIES['queries']) * len(graded_eval.MODES))
        self.assertEqual(manifest['errors'], 0)
        self.assertEqual(set(manifest['retrieval']), set(graded_eval.MODES))
        self.assertEqual(manifest['index_and_model_identity'], 'operator_declared_not_verified_by_collector')
        self.assertEqual(manifest['corpus_provenance'], 'synthetic_fixture_catalog')
        self.assertEqual(manifest['query_provenance']['author_type'], 'synthetic_fixture')
        self.assertIn('not a throughput benchmark', manifest['latency_scope'])
        for name, field in [('queries.json', 'query_sha256'), ('catalog.jsonl', 'catalog_sha256'),
                            ('config.json', 'config_sha256'), ('requests.jsonl', 'requests_sha256')]:
            self.assertEqual(graded_eval.sha((run / name).read_bytes()), manifest[field])
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        self.assertTrue(all(row['elapsed_ms'] >= 0 and row['http_status'] == 200 for row in rows))

    def test_collect_visits_modes_in_a_seeded_randomized_order(self):
        first, second = self.collected_run(name='run-a'), self.collected_run(name='run-b')
        order = lambda run: [(r['query_id'], r['mode']) for r in graded_eval.read_jsonl(run / 'requests.jsonl')]
        self.assertEqual(order(first), order(second))
        self.assertNotEqual(order(first), [(q['query_id'], mode) for q in support.QUERIES['queries']
                                           for mode in graded_eval.MODES])

    def test_collect_refuses_to_reuse_an_existing_run_directory(self):
        with StubServer() as server:
            code, catalog = self.snapshot(server)
            self.assertEqual(self.collect(server, catalog)[0], 0)
            self.assertEqual(self.collect(server, catalog)[0], 2)

    def test_http_failures_are_recorded_as_errors_and_block_scoring(self):
        stub = Stub()
        stub.behavior.update(search_status=503, fail_after=3)
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        self.assertEqual(code, 1)
        manifest = graded_eval.read_json(run / 'manifest.json')
        self.assertGreater(manifest['errors'], 0)
        failures = [r for r in graded_eval.read_jsonl(run / 'requests.jsonl') if r['status'] != 'ok']
        self.assertTrue(all(r['error_type'] == 'http_error' and r['http_status'] == 503 for r in failures))
        with self.assertRaisesRegex(ValueError, 'do not score errors as zero hits'):
            graded_eval.load_run(run)

    def test_transport_failures_are_recorded_rather_than_silently_dropped(self):
        with StubServer() as server:
            code, catalog = self.snapshot(server)
            base_url = server.base_url
        code, run = graded_eval.main(['collect', '--base-url', base_url, '--queries', str(self.queries),
                                      '--catalog', str(catalog), '--config', str(self.config),
                                      '--out', str(self.tmp / 'dead'), '--depth', str(self.depth),
                                      '--timeout', '2']), self.tmp / 'dead'
        self.assertEqual(code, 1)
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        self.assertTrue(rows and all(row['status'] == 'error' and row['http_status'] is None for row in rows))

    def test_missing_retrieval_metadata_is_an_error_not_a_silent_result(self):
        stub = Stub()
        stub.behavior['drop_retrieval'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        self.assertEqual(code, 1)
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        self.assertTrue(all(row['error_type'] == 'ValueError' for row in rows))
        self.assertTrue(all('retrieval metadata' in row['error'] for row in rows))

    def test_algorithm_downgrade_during_a_run_is_an_error(self):
        stub = Stub()
        stub.behavior['wrong_algorithm'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            self.assertEqual(self.collect(server, catalog)[0], 1)

    def test_retrieval_configuration_change_mid_run_is_detected(self):
        stub = Stub()
        stub.behavior['shift_config'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        self.assertEqual(code, 1)
        errors = [r for r in graded_eval.read_jsonl(run / 'requests.jsonl') if r['status'] != 'ok']
        self.assertTrue(any('configuration changed' in r['error'].lower() for r in errors))

    def test_inconsistent_totals_are_refused(self):
        stub = Stub()
        stub.behavior['inflate_total'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            self.assertEqual(self.collect(server, catalog)[0], 1)

    def test_results_outside_the_frozen_catalog_are_refused(self):
        with StubServer() as server:
            code, catalog = self.snapshot(server)
            trimmed = self.tmp / 'trimmed.jsonl'
            rows = graded_eval.read_jsonl(catalog)
            graded_eval.write_jsonl(trimmed, rows[:2])
            code, run = self.collect(server, trimmed, out='trimmed-run')
        self.assertEqual(code, 1)
        errors = [r for r in graded_eval.read_jsonl(run / 'requests.jsonl') if r['status'] != 'ok']
        self.assertTrue(any('absent from frozen catalog' in r['error'] for r in errors))

    def test_results_that_disagree_with_the_snapshot_are_refused(self):
        stub = Stub()
        stub.behavior['stale_price'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        self.assertEqual(code, 1)
        errors = [r for r in graded_eval.read_jsonl(run / 'requests.jsonl') if r['status'] != 'ok']
        self.assertTrue(all('price disagrees with frozen catalog' in r['error'] for r in errors))

    def test_rejected_responses_are_retained_for_diagnosis(self):
        stub = Stub()
        stub.behavior['wrong_algorithm'] = True
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        self.assertTrue(all(row['invalid_response']['retrieval']['algorithm'] == 'silently_downgraded'
                            for row in rows))

    def test_transport_errors_carry_no_response_body(self):
        stub = Stub()
        stub.behavior['search_status'] = 500
        with StubServer(stub) as server:
            code, catalog = self.snapshot(server)
            code, run = self.collect(server, catalog)
        self.assertTrue(all('invalid_response' not in row
                            for row in graded_eval.read_jsonl(run / 'requests.jsonl')))

    def test_rewritten_request_parameters_are_detected(self):
        run = self.collected_run()
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        rows[0]['params'] = {**rows[0]['params'], 'q': 'a different query'}
        (run / 'requests.jsonl').write_text(''.join(graded_eval.canonical(r).decode() + '\n' for r in rows))
        manifest = graded_eval.read_json(run / 'manifest.json')
        manifest['requests_sha256'] = graded_eval.sha((run / 'requests.jsonl').read_bytes())
        (run / 'manifest.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'Recorded request parameters do not match'):
            graded_eval.load_run(run)

    def test_tampering_with_a_frozen_file_after_collection_is_detected(self):
        run = self.collected_run()
        (run / 'catalog.jsonl').write_text((run / 'catalog.jsonl').read_text().replace('Quiet', 'Loud'))
        with self.assertRaisesRegex(ValueError, 'Frozen file changed: catalog.jsonl'):
            graded_eval.load_run(run)

    def test_incomplete_run_is_refused(self):
        run = self.collected_run()
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')[:-1]
        (run / 'requests.jsonl').write_text(''.join(graded_eval.canonical(r).decode() + '\n' for r in rows))
        manifest = graded_eval.read_json(run / 'manifest.json')
        manifest['requests_sha256'] = graded_eval.sha((run / 'requests.jsonl').read_bytes())
        manifest['attempted'] = len(rows)
        (run / 'manifest.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'Run is incomplete'):
            graded_eval.load_run(run)


class PoolTest(EndToEndCase):
    def test_pool_is_blinded_and_starts_with_no_grades(self):
        run = self.collected_run()
        code, pool = self.pool(run)
        self.assertEqual(code, 0)
        data = graded_eval.read_json(pool / 'pool.json')
        self.assertEqual(data['rubric_version'], graded_eval.RUBRIC)
        leaked = {'rank', 'mode', 'score', 'split', 'position', 'retrieval'}
        for pair in data['pairs']:
            self.assertFalse(leaked & set(pair), f'pool row leaks ranking signal: {pair}')
            self.assertEqual(set(pair), {'query_id', 'product_id', 'query', 'intent', 'filters', 'product'})
        template = graded_eval.read_jsonl(pool / 'judgments.template.jsonl')
        self.assertEqual(len(template), len(data['pairs']))
        self.assertTrue(all(row['grade'] is None and row['assessor_id'] is None for row in template))
        self.assertTrue(all(row['pool_id'] == data['pool_id'] for row in template))

    def test_reviewer_rows_hide_retrieval_derived_product_fields(self):
        catalog = [{**row, 'searchEmbedding': [0.1, 0.2], 'score': 9.9, 'rank': 1} for row in support.CATALOG]
        run = self.collected_run(Stub(catalog), name='derived')
        code, pool = self.pool(run)
        self.assertEqual(code, 0)
        for pair in graded_eval.read_json(pool / 'pool.json')['pairs']:
            self.assertFalse(graded_eval.DERIVED & set(pair['product']), pair['product'])
            self.assertIn('description', pair['product'])

    def test_pool_covers_every_returned_pair_exactly_once(self):
        run = self.collected_run()
        code, pool = self.pool(run)
        rows = graded_eval.read_jsonl(run / 'requests.jsonl')
        expected = {(row['query_id'], str(item['id'])) for row in rows for item in row['response']['items']}
        pairs = [(pair['query_id'], pair['product_id']) for pair in graded_eval.read_json(pool / 'pool.json')['pairs']]
        self.assertEqual(sorted(pairs), sorted(expected))
        self.assertEqual(len(pairs), len(set(pairs)))

    def test_pool_identity_is_stable_across_reruns_of_the_same_run(self):
        run = self.collected_run()
        first = graded_eval.read_json(self.pool(run, out='pool-a')[1] / 'pool.json')['pool_id']
        second = graded_eval.read_json(self.pool(run, out='pool-b')[1] / 'pool.json')['pool_id']
        self.assertEqual(first, second)


class EvaluateTest(EndToEndCase):
    def evaluate(self, run, judgments, out='report.json', **extra):
        target = self.tmp / out
        argv = ['evaluate', '--run', str(run), '--judgments', str(judgments),
                '--assessor', extra.pop('assessor', 'fixture-assessor-a'), '--out', str(target),
                '--split', extra.pop('split', 'test'), '--k', str(extra.pop('k', self.k))]
        return graded_eval.main(argv), target

    def test_full_pipeline_produces_a_labeled_synthetic_fixture_report(self):
        run, pool, judgments, manifest = self.judged_run()
        code, target = self.evaluate(run, judgments)
        self.assertEqual(code, 0)
        report = graded_eval.read_json(target)
        self.assertEqual(report['evidence_class'], 'synthetic_fixture_judged_pooled_evaluation')
        self.assertTrue(report['assessor_type_is_declared_not_authenticated'])
        self.assertEqual(report['query_provenance']['author_type'], 'synthetic_fixture')
        self.assertEqual(report['split'], 'test')
        self.assertEqual(set(report['by_mode']), set(graded_eval.MODES))
        self.assertEqual(report['definitions']['precision_denominator'], self.k)
        self.assertIn('pool-bounded', report['definitions']['recall'])
        self.assertEqual(report['judgments_sha256'], graded_eval.sha(judgments.read_bytes()))
        self.assertEqual(report['manifest']['catalog_sha256'], manifest['catalog_sha256'])

    def test_only_the_selected_split_is_scored(self):
        run, pool, judgments, _ = self.judged_run()
        report = graded_eval.read_json(self.evaluate(run, judgments)[1])
        scored = {row['query_id'] for row in report['per_query']}
        self.assertEqual(scored, {'q1', 'q2', 'q3', 'q4'})
        self.assertEqual(len(report['per_query']), 4 * len(graded_eval.MODES))
        for mode in graded_eval.MODES:
            self.assertEqual(report['by_mode'][mode]['queries'], 4)

    def test_dev_split_can_be_scored_independently(self):
        run, pool, judgments, _ = self.judged_run()
        report = graded_eval.read_json(self.evaluate(run, judgments, out='dev.json', split='dev')[1])
        self.assertEqual({row['query_id'] for row in report['per_query']}, {'q5', 'q6'})

    def test_paired_differences_are_reported_against_the_lexical_baseline(self):
        run, pool, judgments, _ = self.judged_run()
        report = graded_eval.read_json(self.evaluate(run, judgments)[1])
        self.assertEqual(set(report['paired_ndcg_differences']),
                         {'vector_minus_text', 'hybrid_minus_text', 'hybrid_rrf_minus_text'})
        for name, delta in report['paired_ndcg_differences'].items():
            self.assertEqual(delta['families'], 2, name)
            self.assertEqual(delta['queries'], 4, name)
            self.assertIsNotNone(delta['ci95'], name)
        self.assertEqual(report['paired_ndcg_differences']['hybrid_minus_text']['mean_delta'],
                         graded_eval.read_json(self.evaluate(run, judgments, out='again.json')[1])
                         ['paired_ndcg_differences']['hybrid_minus_text']['mean_delta'])

    def test_metrics_stay_within_their_definitions(self):
        run, pool, judgments, _ = self.judged_run()
        report = graded_eval.read_json(self.evaluate(run, judgments)[1])
        for row in report['per_query']:
            for key in (f'pooled_ndcg@{self.k}', f'pooled_recall@{self.k}'):
                self.assertTrue(row[key] is None or 0 <= row[key] <= 1, row)
            self.assertTrue(0 <= row[f'precision@{self.k}'] <= 1)
        for summary in report['by_mode'].values():
            self.assertEqual(summary['ndcg_scorable_queries'], 4)
            self.assertEqual(summary['zero_result_queries'], 0)
            self.assertGreater(summary['mean_observed_latency_ms'], 0)

    def test_incomplete_judgments_for_the_scored_split_are_refused(self):
        run, pool, judgments, manifest = self.judged_run()
        rows = graded_eval.read_jsonl(judgments)
        partial = self.tmp / 'partial.jsonl'
        graded_eval.write_jsonl(partial, [r for r in rows if not (r['query_id'] == 'q1' and r['product_id'] == 'p2')])
        self.assertEqual(self.evaluate(run, partial, out='partial.json')[0], 2)

    def test_judging_only_the_development_split_cannot_score_the_test_split(self):
        run, pool, judgments, manifest = self.judged_run()
        rows = [r for r in graded_eval.read_jsonl(judgments) if r['query_id'] in ('q5', 'q6')]
        dev_only = self.tmp / 'dev-only.jsonl'
        graded_eval.write_jsonl(dev_only, rows)
        self.assertEqual(self.evaluate(run, dev_only, out='devonly.json')[0], 2)
        self.assertEqual(self.evaluate(run, dev_only, out='devonly-dev.json', split='dev')[0], 0)

    def test_unknown_assessor_and_oversized_k_are_refused(self):
        run, pool, judgments, _ = self.judged_run()
        self.assertEqual(self.evaluate(run, judgments, out='a.json', assessor='nobody')[0], 2)
        self.assertEqual(self.evaluate(run, judgments, out='b.json', k=self.depth + 1)[0], 2)

    def test_report_output_is_never_overwritten(self):
        run, pool, judgments, _ = self.judged_run()
        self.assertEqual(self.evaluate(run, judgments, out='once.json')[0], 0)
        self.assertEqual(self.evaluate(run, judgments, out='once.json')[0], 2)


class AgreementTest(EndToEndCase):
    def agreement(self, run, judgments, first, second, out='agreement.json'):
        target = self.tmp / out
        return graded_eval.main(['agreement', '--run', str(run), '--judgments', str(judgments),
                                 '--first', first, '--second', second, '--out', str(target)]), target

    def test_perfect_agreement_between_two_assessors(self):
        run, pool, judgments, _ = self.judged_run(((0, 'a'), (0, 'b')))
        code, target = self.agreement(run, judgments, 'a', 'b')
        self.assertEqual(code, 0)
        result = graded_eval.read_json(target)
        self.assertEqual(result['coverage'], 1.0)
        self.assertEqual(result['exact_agreement'], 1.0)
        self.assertAlmostEqual(result['linear_weighted_cohen_kappa'], 1.0)
        self.assertIn('not truth', result['note'])

    def test_systematically_shifted_grades_lower_agreement(self):
        run, pool, judgments, _ = self.judged_run(((0, 'a'), (1, 'b')))
        result = graded_eval.read_json(self.agreement(run, judgments, 'a', 'b')[1])
        self.assertEqual(result['exact_agreement'], 0.0)
        self.assertLess(result['linear_weighted_cohen_kappa'], 0.5)
        self.assertEqual(result['assessors'], {'a': 'synthetic_fixture', 'b': 'synthetic_fixture'})

    def test_degenerate_constant_grading_reports_null_kappa_instead_of_a_number(self):
        run = self.collected_run()
        code, pool = self.pool(run)
        manifest = graded_eval.read_json(run / 'manifest.json')
        rows = []
        for assessor in ('a', 'b'):
            for row in support.fixture_judgments(pool / 'pool.json', manifest, assessor):
                rows.append({**row, 'grade': 3})
        judgments = self.tmp / 'constant.jsonl'
        graded_eval.write_jsonl(judgments, rows)
        result = graded_eval.read_json(self.agreement(run, judgments, 'a', 'b')[1])
        self.assertEqual(result['exact_agreement'], 1.0)
        self.assertIsNone(result['linear_weighted_cohen_kappa'])

    def test_agreement_requires_two_distinct_known_assessors(self):
        run, pool, judgments, _ = self.judged_run(((0, 'a'), (0, 'b')))
        self.assertEqual(self.agreement(run, judgments, 'a', 'a', out='x.json')[0], 2)
        self.assertEqual(self.agreement(run, judgments, 'a', 'ghost', out='y.json')[0], 2)


if __name__ == '__main__':
    unittest.main()


class CarryForwardTest(EndToEndCase):
    """Moving existing judgments onto a later run without inventing any.

    Regrading identical pairs after every code change is how judgment sets rot, but
    rebadging old grades with new hashes is how a benchmark starts reporting labels nobody
    made. These tests are about the line between the two.
    """

    def carry(self, source, judgments, target, *extra):
        out = self.tmp / f'carried-{target.name}.jsonl'
        code = graded_eval.main(['carry-forward', '--from-run', str(source), '--judgments', str(judgments),
                                 '--run', str(target), '--out', str(out), *extra])
        return code, out

    def second_run(self, name='run2', **behavior):
        stub = Stub()
        stub.behavior.update(behavior)
        return self.collected_run(stub, name=name)

    def test_grades_move_when_nothing_a_reviewer_saw_changed(self):
        source, _, judgments, manifest = self.judged_run()
        target = self.second_run()
        target_manifest = graded_eval.read_json(target / 'manifest.json')

        source_pool_id = graded_eval.read_jsonl(judgments)[0]['pool_id']
        code, out = self.carry(source, judgments, target)
        self.assertEqual(code, 0)
        carried = graded_eval.read_jsonl(out)
        self.assertTrue(carried)
        original = {(row['query_id'], row['product_id']): row for row in graded_eval.read_jsonl(judgments)}
        for row in carried:
            before = original[(row['query_id'], row['product_id'])]
            self.assertEqual(row['grade'], before['grade'])
            self.assertEqual(row['assessor_id'], before['assessor_id'])
            self.assertEqual(row['rationale'], before['rationale'])
            # Re-stamped for the new run, but the chain back to where the grade was made
            # stays in the row.
            self.assertEqual(row['catalog_sha256'], target_manifest['catalog_sha256'])
            self.assertEqual(row['carried_from_catalog_sha256'], manifest['catalog_sha256'])
            self.assertEqual(row['carried_from_pool_id'], source_pool_id)

    def test_carried_judgments_are_accepted_by_evaluate(self):
        source, _, judgments, _ = self.judged_run()
        target = self.second_run()
        code, out = self.carry(source, judgments, target)
        self.assertEqual(code, 0)
        report = self.tmp / 'carried-report.json'
        self.assertEqual(graded_eval.main(['evaluate', '--run', str(target), '--judgments', str(out),
                                           '--assessor', 'fixture-assessor-a', '--out', str(report),
                                           '--split', 'test', '--k', str(self.k)]), 0)

    def test_a_product_whose_details_changed_is_not_carried(self):
        source, _, judgments, _ = self.judged_run()
        target = self.second_run(reprice=('p1', 41))

        code, out = self.carry(source, judgments, target)
        self.assertEqual(code, 0)
        carried = graded_eval.read_jsonl(out)
        self.assertTrue(carried, 'unchanged products should still carry')
        self.assertNotIn('p1', {row['product_id'] for row in carried})

    def test_a_new_visible_field_must_be_declared_before_grades_can_move(self):
        source, _, judgments, _ = self.judged_run()
        target = self.second_run(extra_product_field='warranty')

        code, out = self.carry(source, judgments, target)
        self.assertEqual(code, 2, 'an undeclared new field must stop the carry, not be ignored')
        self.assertFalse(out.exists())

        code, out = self.carry(source, judgments, target, '--allow-new-field', 'warranty')
        self.assertEqual(code, 0)
        carried = graded_eval.read_jsonl(out)
        self.assertTrue(carried)
        self.assertEqual(carried[0]['carried_new_fields_ignored'], ['warranty'])

    def test_a_field_reviewers_never_see_does_not_need_declaring(self):
        # Retrieval-derived fields are stripped before a reviewer sees a product, so their
        # appearance cannot have changed a grade.
        source, _, judgments, _ = self.judged_run()
        target = self.second_run(extra_product_field=sorted(graded_eval.DERIVED)[0])
        self.assertEqual(self.carry(source, judgments, target)[0], 0)

    def test_judgments_from_another_pool_are_refused(self):
        source, _, judgments, _ = self.judged_run()
        target = self.second_run()
        rows = graded_eval.read_jsonl(judgments)
        for row in rows:
            row['pool_id'] = 'a' * 64
        foreign = self.tmp / 'foreign.jsonl'
        graded_eval.write_jsonl(foreign, rows)

        self.assertEqual(self.carry(source, foreign, target)[0], 2)

    def test_a_run_with_different_queries_refuses_the_carry(self):
        source, _, judgments, _ = self.judged_run()
        # Rewriting the query text changes the question the grade answered.
        queries = json.loads(self.queries.read_text())
        queries['queries'][0]['query'] = queries['queries'][0]['query'] + ' urgently'
        self.queries.write_text(json.dumps(queries))
        target = self.second_run(name='run-requeried')

        self.assertEqual(self.carry(source, judgments, target)[0], 2)
