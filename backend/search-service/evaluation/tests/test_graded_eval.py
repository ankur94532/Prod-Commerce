"""Unit tests for the graded evaluation harness.

These verify the harness refuses bad input and computes documented metrics. They prove
nothing about retrieval quality: all data here is synthetic fixture data.
"""
from __future__ import annotations

import json
import math
import pathlib
import tempfile
import unittest

from support import graded_eval, RETRIEVAL, CATALOG, QUERIES


def response(ids, mode='text', depth=3, total=None, **overrides):
    total = len(ids) if total is None else total
    payload = {'items': [{'id': pid} for pid in ids], 'total': total, 'page': 0, 'size': depth,
               'retrieval': dict(RETRIEVAL[mode])}
    payload.update(overrides)
    return payload


class TempDirCase(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = pathlib.Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)


class MetricMathTest(unittest.TestCase):
    def test_dcg_uses_exponential_gain_and_log2_rank_discount(self):
        self.assertAlmostEqual(graded_eval.dcg([3, 0]), 7.0)
        self.assertAlmostEqual(graded_eval.dcg([1, 2, 3]), 1 / 1 + 3 / math.log2(3) + 7 / 2)
        self.assertEqual(graded_eval.dcg([]), 0)

    def test_metrics_match_hand_computed_values(self):
        judgments = {'a': 3, 'b': 0, 'c': 2, 'd': 3}
        result = graded_eval.metrics(['a', 'b', 'c'], judgments, 3)
        expected_dcg = 7 / 1 + 0 / math.log2(3) + 3 / 2
        expected_ideal = 7 / 1 + 7 / math.log2(3) + 3 / 2
        self.assertAlmostEqual(result['pooled_ndcg@3'], expected_dcg / expected_ideal)
        self.assertAlmostEqual(result['precision@3'], 2 / 3)
        self.assertAlmostEqual(result['pooled_recall@3'], 2 / 3)
        self.assertAlmostEqual(result['mrr@3'], 1.0)

    def test_precision_denominator_stays_k_when_fewer_results_are_returned(self):
        result = graded_eval.metrics(['a'], {'a': 3, 'b': 3}, 3)
        self.assertAlmostEqual(result['precision@3'], 1 / 3)
        self.assertAlmostEqual(result['pooled_recall@3'], 1 / 2)

    def test_mrr_is_zero_when_no_returned_result_reaches_the_relevance_threshold(self):
        result = graded_eval.metrics(['a', 'b'], {'a': 1, 'b': 0, 'c': 3}, 2)
        self.assertEqual(result['mrr@2'], 0.0)
        self.assertEqual(result['precision@2'], 0.0)
        self.assertAlmostEqual(result['pooled_recall@2'], 0.0)

    def test_mrr_uses_the_first_relevant_rank(self):
        self.assertAlmostEqual(graded_eval.metrics(['a', 'b'], {'a': 0, 'b': 2}, 2)['mrr@2'], 0.5)

    def test_all_zero_pool_yields_null_ndcg_and_recall_rather_than_zero(self):
        result = graded_eval.metrics(['a', 'b'], {'a': 0, 'b': 0}, 2)
        self.assertIsNone(result['pooled_ndcg@2'])
        self.assertIsNone(result['pooled_recall@2'])
        self.assertEqual(result['precision@2'], 0.0)

    def test_unjudged_result_is_refused_and_never_treated_as_zero(self):
        with self.assertRaisesRegex(ValueError, 'Unjudged result'):
            graded_eval.metrics(['a', 'b'], {'a': 3}, 2)

    def test_unjudged_results_below_k_are_ignored_but_judged_ones_still_count(self):
        result = graded_eval.metrics(['a', 'unseen'], {'a': 3}, 1)
        self.assertAlmostEqual(result['pooled_ndcg@1'], 1.0)

    def test_duplicate_result_ids_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate result IDs'):
            graded_eval.metrics(['a', 'a'], {'a': 3}, 2)

    def test_mean_present_skips_nulls_without_counting_them_as_zero(self):
        self.assertAlmostEqual(graded_eval.mean_present([1.0, None, 0.0]), 0.5)
        self.assertIsNone(graded_eval.mean_present([None, None]))


class BootstrapTest(unittest.TestCase):
    def test_empty_deltas_report_no_estimate(self):
        self.assertEqual(graded_eval.paired_bootstrap({}),
                         {'mean_delta': None, 'ci95': None, 'families': 0, 'queries': 0})

    def test_single_family_reports_mean_without_a_confidence_interval(self):
        result = graded_eval.paired_bootstrap({'f1': [0.2, 0.4]})
        self.assertAlmostEqual(result['mean_delta'], 0.3)
        self.assertIsNone(result['ci95'])
        self.assertEqual((result['families'], result['queries']), (1, 2))

    def test_resampling_is_seeded_and_brackets_the_point_estimate(self):
        deltas = {'f1': [0.1, 0.3], 'f2': [-0.2], 'f3': [0.5]}
        first = graded_eval.paired_bootstrap(deltas, seed=7)
        second = graded_eval.paired_bootstrap(deltas, seed=7)
        self.assertEqual(first, second)
        self.assertAlmostEqual(first['mean_delta'], (0.1 + 0.3 - 0.2 + 0.5) / 4)
        self.assertLessEqual(first['ci95'][0], first['mean_delta'])
        self.assertGreaterEqual(first['ci95'][1], first['mean_delta'])
        self.assertEqual((first['families'], first['queries']), (3, 4))

    def test_interval_interpolates_quantiles(self):
        self.assertEqual(graded_eval.interval([0, 1]), [0.025, 0.975])
        self.assertEqual(graded_eval.interval([5, 5, 5]), [5, 5])


class QueryValidationTest(unittest.TestCase):
    def dataset(self, **overrides):
        data = json.loads(json.dumps(QUERIES))
        data.update(overrides)
        return data

    def test_fixture_dataset_and_shipped_query_set_both_validate(self):
        self.assertEqual(len(graded_eval.validate_queries(self.dataset())), 6)
        shipped = pathlib.Path(__file__).resolve().parents[1] / 'queries.v1.json'
        self.assertTrue(graded_eval.validate_queries(graded_eval.read_json(shipped)))

    def test_shipped_query_set_declares_non_human_authorship(self):
        shipped = graded_eval.read_json(pathlib.Path(__file__).resolve().parents[1] / 'queries.v1.json')
        self.assertNotEqual(shipped['provenance']['author_type'], 'human')

    def test_unsupported_schema_version_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'Unsupported query schema'):
            graded_eval.validate_queries(self.dataset(schema_version=2))

    def test_missing_or_unknown_provenance_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'provenance'):
            graded_eval.validate_queries(self.dataset(provenance={'author_type': 'ai'}))
        with self.assertRaisesRegex(ValueError, 'provenance'):
            graded_eval.validate_queries(self.dataset(provenance={'author_type': 'crowd', 'source': 'x'}))

    def test_duplicate_query_ids_are_refused(self):
        data = self.dataset()
        data['queries'][1]['query_id'] = data['queries'][0]['query_id']
        with self.assertRaisesRegex(ValueError, 'Duplicate/missing query ID'):
            graded_eval.validate_queries(data)

    def test_query_family_may_not_span_dev_and_test(self):
        data = self.dataset()
        data['queries'][1]['split'] = 'dev'
        with self.assertRaisesRegex(ValueError, 'leaks across dev/test'):
            graded_eval.validate_queries(data)

    def test_unknown_split_is_refused(self):
        data = self.dataset()
        data['queries'][0]['split'] = 'holdout'
        with self.assertRaisesRegex(ValueError, 'Split must be'):
            graded_eval.validate_queries(data)

    def test_unknown_filter_field_is_refused(self):
        data = self.dataset()
        data['queries'][0]['filters'] = {'colour': 'black'}
        with self.assertRaisesRegex(ValueError, 'Unknown filter'):
            graded_eval.validate_queries(data)

    def test_malformed_filter_values_are_refused(self):
        for filters, message in [({'minPrice': -1}, 'Invalid price filter'),
                                 ({'maxPrice': float('inf')}, 'Invalid price filter'),
                                 ({'inStock': 'true'}, 'inStock must be a boolean'),
                                 ({'category': '  '}, 'Invalid term filter'),
                                 ({'minPrice': 50, 'maxPrice': 10}, 'Invalid price range')]:
            data = self.dataset()
            data['queries'][0]['filters'] = filters
            with self.assertRaisesRegex(ValueError, message):
                graded_eval.validate_queries(data)

    def test_non_relevance_sort_is_refused_for_relevance_ablations(self):
        data = self.dataset()
        data['queries'][0]['sort'] = 'price_asc'
        with self.assertRaisesRegex(ValueError, 'relevance sort'):
            graded_eval.validate_queries(data)

    def test_missing_required_query_fields_are_refused(self):
        for field in ('query', 'intent', 'kind', 'family_id'):
            data = self.dataset()
            data['queries'][0][field] = ''
            with self.assertRaises(ValueError):
                graded_eval.validate_queries(data)


class CatalogValidationTest(unittest.TestCase):
    def test_fixture_catalog_validates_and_indexes_by_string_id(self):
        indexed = graded_eval.validate_catalog([dict(row) for row in CATALOG])
        self.assertEqual(set(indexed), {row['id'] for row in CATALOG})

    def test_empty_catalog_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'empty'):
            graded_eval.validate_catalog([])

    def test_duplicate_catalog_ids_are_refused(self):
        rows = [dict(CATALOG[0]), dict(CATALOG[0])]
        with self.assertRaisesRegex(ValueError, 'Duplicate/missing catalog ID'):
            graded_eval.validate_catalog(rows)

    def test_rows_without_judgeable_evidence_are_refused(self):
        for field, message in [('description', 'descriptions for judgment'),
                               ('stockQuantity', 'stockQuantity'),
                               ('price', 'name/currency/price'),
                               ('currency', 'name/currency/price')]:
            row = dict(CATALOG[0])
            row.pop(field)
            with self.assertRaisesRegex(ValueError, message):
                graded_eval.validate_catalog([row])


class FilterSemanticsTest(unittest.TestCase):
    def product(self, **overrides):
        return {**CATALOG[0], **overrides}

    def test_price_bounds_are_inclusive(self):
        self.assertFalse(graded_eval.violates_filters(self.product(price=40), {'minPrice': 40, 'maxPrice': 40}))
        self.assertTrue(graded_eval.violates_filters(self.product(price=39), {'minPrice': 40}))
        self.assertTrue(graded_eval.violates_filters(self.product(price=41), {'maxPrice': 40}))

    def test_in_stock_filter_only_constrains_when_requested(self):
        self.assertTrue(graded_eval.violates_filters(self.product(stockQuantity=0), {'inStock': True}))
        self.assertFalse(graded_eval.violates_filters(self.product(stockQuantity=0), {'inStock': False}))

    def test_term_filters_compare_case_insensitively_with_slug_fallback(self):
        self.assertFalse(graded_eval.violates_filters(self.product(), {'category': 'EARBUDS-HEADPHONES'}))
        self.assertTrue(graded_eval.violates_filters(self.product(), {'category': 'books-stationery'}))
        row = {k: v for k, v in self.product().items() if k != 'categorySlug'}
        row['category'] = 'earbuds-headphones'
        self.assertFalse(graded_eval.violates_filters(row, {'category': 'earbuds-headphones'}))

    def test_attribute_filters_fall_back_to_the_attributes_map(self):
        row = {k: v for k, v in self.product().items() if k != 'color'}
        row['attributes'] = {'color': 'Black'}
        self.assertFalse(graded_eval.violates_filters(row, {'color': 'black'}))
        self.assertTrue(graded_eval.violates_filters(row, {'color': 'silver'}))

    def test_absent_attribute_counts_as_a_violation_rather_than_a_pass(self):
        self.assertTrue(graded_eval.violates_filters(self.product(), {'material': 'mesh'}))


class NumericValidationTest(unittest.TestCase):
    def test_accepts_numbers_and_numeric_strings(self):
        self.assertEqual(graded_eval.numeric(40, 'bad'), 40.0)
        self.assertEqual(graded_eval.numeric('40.5', 'bad'), 40.5)
        self.assertEqual(graded_eval.numeric(0, 'bad'), 0.0)

    def test_rejects_missing_negative_nonfinite_and_non_numeric_values(self):
        for value in (None, -1, float('nan'), float('inf'), 'cheap', [], True):
            with self.assertRaisesRegex(ValueError, 'bad'):
                graded_eval.numeric(value, 'bad')

    def test_integral_flag_rejects_fractional_values(self):
        self.assertEqual(graded_eval.numeric(3, 'bad', integral=True), 3.0)
        with self.assertRaisesRegex(ValueError, 'bad'):
            graded_eval.numeric(2.5, 'bad', integral=True)


class CatalogNumericTest(unittest.TestCase):
    def catalog(self, **overrides):
        return [{**CATALOG[0], **overrides}]

    def test_non_numeric_or_negative_prices_are_refused(self):
        for price in ('free', -5, float('nan')):
            with self.assertRaisesRegex(ValueError, 'price must be'):
                graded_eval.validate_catalog(self.catalog(price=price))

    def test_fractional_or_negative_stock_is_refused(self):
        for stock in (2.5, -1, 'many'):
            with self.assertRaisesRegex(ValueError, 'stockQuantity must be'):
                graded_eval.validate_catalog(self.catalog(stockQuantity=stock))

    def test_string_encoded_numbers_are_accepted(self):
        self.assertTrue(graded_eval.validate_catalog(self.catalog(price='40.00', stockQuantity='5')))


class ItemCrossCheckTest(unittest.TestCase):
    catalog = {row['id']: row for row in CATALOG}

    def test_matching_items_pass(self):
        graded_eval.cross_check_items([{'id': 'p1', 'name': CATALOG[0]['name'], 'price': 40}], self.catalog)

    def test_items_missing_optional_fields_still_pass(self):
        graded_eval.cross_check_items([{'id': 'p1'}], self.catalog)

    def test_stale_name_slug_or_price_is_refused(self):
        for item, field in ([{'id': 'p1', 'name': 'Renamed'}, 'name'],
                            [{'id': 'p1', 'slug': 'other-slug'}, 'slug'],
                            [{'id': 'p1', 'price': 41}, 'price']):
            with self.assertRaisesRegex(ValueError, f'Result {field} disagrees'):
                graded_eval.cross_check_items([item], self.catalog)

    def test_unknown_product_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'absent from frozen catalog'):
            graded_eval.cross_check_items([{'id': 'p99'}], self.catalog)


class SearchParamsTest(unittest.TestCase):
    def test_parameters_are_built_deterministically_from_the_frozen_query(self):
        query = QUERIES['queries'][2]
        params = graded_eval.search_params(query, 'hybrid_rrf', 10)
        self.assertEqual(params, {'q': query['query'], 'maxPrice': 50, 'inStock': 'true',
                                  'mode': 'hybrid_rrf', 'sort': 'relevance', 'page': 0, 'size': 10})

    def test_queries_without_filters_still_pin_sort_page_and_depth(self):
        params = graded_eval.search_params(QUERIES['queries'][0], 'text', 5)
        self.assertEqual(params['sort'], 'relevance')
        self.assertEqual((params['page'], params['size']), (0, 5))


class ResponseContractTest(unittest.TestCase):
    def test_valid_response_returns_ordered_ids(self):
        self.assertEqual(graded_eval.validate_response(response(['a', 'b']), 'text', 3), ['a', 'b'])

    def test_missing_retrieval_metadata_is_refused_as_an_old_server_or_fallback(self):
        payload = response(['a'])
        payload.pop('retrieval')
        with self.assertRaisesRegex(ValueError, 'Missing/mismatched retrieval metadata'):
            graded_eval.validate_response(payload, 'text', 3)

    def test_mode_and_algorithm_mismatches_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'Missing/mismatched retrieval metadata'):
            graded_eval.validate_response(response(['a'], mode='text'), 'vector', 3)
        payload = response(['a'])
        payload['retrieval']['algorithm'] = 'silently_downgraded'
        with self.assertRaisesRegex(ValueError, 'Unexpected algorithm'):
            graded_eval.validate_response(payload, 'text', 3)

    def test_total_relation_must_match_the_mode(self):
        payload = response(['a'])
        payload['retrieval']['totalRelation'] = 'candidate_union'
        with self.assertRaisesRegex(ValueError, 'Unknown total relation'):
            graded_eval.validate_response(payload, 'text', 3)

    def test_rrf_window_must_cover_the_collection_depth(self):
        payload = response(['a'], mode='hybrid_rrf')
        payload['retrieval']['candidateWindow'] = 2
        with self.assertRaisesRegex(ValueError, 'candidate window smaller'):
            graded_eval.validate_response(payload, 'hybrid_rrf', 3)
        payload['retrieval'].update(candidateWindow=100, rrfRankConstant=0)
        with self.assertRaisesRegex(ValueError, 'Invalid RRF constant'):
            graded_eval.validate_response(payload, 'hybrid_rrf', 3)

    def test_server_side_page_or_depth_changes_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'changed page/depth'):
            graded_eval.validate_response(response(['a'], page=1), 'text', 3)
        with self.assertRaisesRegex(ValueError, 'changed page/depth'):
            graded_eval.validate_response(response(['a'], size=5), 'text', 3)

    def test_duplicate_or_excess_results_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate results or excess depth'):
            graded_eval.validate_response(response(['a', 'a'], total=2), 'text', 3)
        with self.assertRaisesRegex(ValueError, 'Duplicate results or excess depth'):
            graded_eval.validate_response(response(['a', 'b', 'c', 'd'], total=4), 'text', 3)

    def test_totals_inconsistent_with_returned_items_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'Invalid total'):
            graded_eval.validate_response(response(['a', 'b'], total=1), 'text', 3)
        with self.assertRaisesRegex(ValueError, 'Truncated response'):
            graded_eval.validate_response(response(['a'], total=9), 'text', 3)

    def test_genuine_zero_hits_are_accepted(self):
        self.assertEqual(graded_eval.validate_response(response([], total=0), 'text', 3), [])


class JudgmentLoadingTest(TempDirCase):
    manifest = {'query_sha256': 'qs', 'catalog_sha256': 'cs'}
    pairs = [('q1', 'p1'), ('q1', 'p2')]

    def row(self, **overrides):
        base = {'query_id': 'q1', 'product_id': 'p1', 'grade': 2, 'assessor_id': 'a',
                'assessor_type': 'human', 'rationale': 'because', 'pool_id': 'pool',
                'rubric_version': graded_eval.RUBRIC, 'query_sha256': 'qs', 'catalog_sha256': 'cs'}
        base.update(overrides)
        return base

    def load(self, rows):
        path = self.tmp / f'judgments-{len(list(self.tmp.iterdir()))}.jsonl'
        graded_eval.write_jsonl(path, rows)
        return graded_eval.load_judgments(path, 'pool', self.manifest, self.pairs)

    def test_valid_judgments_load_with_assessor_types(self):
        labels, types = self.load([self.row(), self.row(product_id='p2', grade=0)])
        self.assertEqual(labels, {('a', 'q1', 'p1'): 2, ('a', 'q1', 'p2'): 0})
        self.assertEqual(types, {'a': 'human'})

    def test_blank_grade_is_not_read_as_zero(self):
        with self.assertRaisesRegex(ValueError, 'blank is not zero'):
            self.load([self.row(grade=None)])

    def test_grades_outside_the_rubric_range_or_of_the_wrong_type_are_refused(self):
        for grade in (-1, 4, 2.0, True, '2'):
            with self.assertRaisesRegex(ValueError, 'integer grade 0–3'):
                self.load([self.row(grade=grade)])

    def test_assessor_provenance_and_rationale_are_required(self):
        for overrides in ({'assessor_id': ''}, {'assessor_type': 'unknown'}, {'rationale': '   '}):
            with self.assertRaisesRegex(ValueError, 'Assessor provenance'):
                self.load([self.row(**overrides)])

    def test_pool_rubric_and_snapshot_mismatches_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'pool/rubric mismatch'):
            self.load([self.row(pool_id='other')])
        with self.assertRaisesRegex(ValueError, 'pool/rubric mismatch'):
            self.load([self.row(rubric_version='freeform')])
        with self.assertRaisesRegex(ValueError, 'snapshot mismatch'):
            self.load([self.row(catalog_sha256='changed')])

    def test_pairs_outside_the_frozen_pool_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'outside frozen pool'):
            self.load([self.row(product_id='p9')])

    def test_duplicate_judgments_require_explicit_adjudication(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate judgment'):
            self.load([self.row(), self.row(grade=3)])

    def test_one_assessor_may_not_change_declared_type_midway(self):
        with self.assertRaisesRegex(ValueError, 'Assessor type changed'):
            self.load([self.row(), self.row(product_id='p2', assessor_type='ai')])

    def test_two_assessors_may_judge_the_same_pair(self):
        labels, types = self.load([self.row(), self.row(assessor_id='b', assessor_type='ai', grade=1)])
        self.assertEqual(len(labels), 2)
        self.assertEqual(types, {'a': 'human', 'b': 'ai'})


class OutputSafetyTest(TempDirCase):
    def test_json_and_jsonl_writes_refuse_to_overwrite_existing_evidence(self):
        target = self.tmp / 'evidence.json'
        graded_eval.write_json(target, {'a': 1})
        with self.assertRaises(FileExistsError):
            graded_eval.write_json(target, {'a': 2})
        rows = self.tmp / 'rows.jsonl'
        graded_eval.write_jsonl(rows, [{'a': 1}])
        with self.assertRaises(FileExistsError):
            graded_eval.write_jsonl(rows, [{'a': 2}])
        self.assertEqual(graded_eval.read_json(target), {'a': 1})

    def test_canonical_encoding_is_order_independent_and_rejects_nan(self):
        self.assertEqual(graded_eval.canonical({'a': 1, 'b': 2}), graded_eval.canonical({'b': 2, 'a': 1}))
        with self.assertRaises(ValueError):
            graded_eval.canonical({'a': float('nan')})

    def test_cli_refuses_invalid_input_with_a_nonzero_status_instead_of_raising(self):
        bad = self.tmp / 'bad.json'
        graded_eval.write_json(bad, {'schema_version': 9})
        self.assertEqual(graded_eval.main(['validate', '--queries', str(bad)]), 2)

    def test_cli_validate_accepts_the_shipped_query_set(self):
        shipped = pathlib.Path(__file__).resolve().parents[1] / 'queries.v1.json'
        self.assertEqual(graded_eval.main(['validate', '--queries', str(shipped)]), 0)


if __name__ == '__main__':
    unittest.main()
