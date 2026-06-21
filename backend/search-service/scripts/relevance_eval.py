#!/usr/bin/env python3
"""
Generate catalog-aware search queries, run them against the live search API,
and report relevance metrics.

The evaluator builds expectations from the current catalog:
- product queries expect one exact product
- category queries expect products in that category
- brand queries expect products from that brand
- attribute queries expect products matching the attribute value

This makes precision/recall-style metrics meaningful enough for regression
checks without requiring a manually labeled dataset yet.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import json
import math
import random
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass
from typing import Any


STOP_WORDS = {
    "the",
    "and",
    "of",
    "for",
    "with",
    "pack",
    "set",
    "pro",
    "max",
    "plus",
    "inch",
    "inches",
    "gb",
    "tb",
    "ml",
    "kg",
    "cm",
    "x",
    "by",
    "to",
    "a",
    "an",
    "all",
    "new",
}

CATEGORY_ALIASES = {
    "smartphones": [
        "mobile",
        "mobiles",
        "phone",
        "phones",
        "smartphone",
        "smartphones",
        "android phone",
        "iphone",
        "5g phone",
        "cell phone",
        "handset",
    ],
    "laptops": ["laptop", "laptops", "gaming laptop", "work laptop", "student laptop", "ultrabook"],
    "tablets": ["tablet", "tablets", "android tablet", "ipad", "kindle", "e reader"],
    "earbuds-headphones": [
        "earbuds",
        "headphones",
        "earphones",
        "headset",
        "wireless earbuds",
        "bluetooth headphones",
        "noise cancelling headphones",
    ],
    "watches-wearables": ["watch", "watches", "smartwatch", "smart watch", "fitness band", "sports watch", "wearable"],
    "footwear": ["shoes", "shoe", "sneakers", "sandals", "heels", "running shoes", "walking shoes", "formal shoes", "flip flops"],
    "bags-wallets": ["bag", "bags", "backpack", "wallet", "sling bag", "tote bag", "crossbody bag", "travel bag", "laptop backpack"],
    "mens-shirts": ["men shirt", "mens shirt", "shirt for men", "formal shirt", "casual shirt"],
    "mens-tshirts": ["tshirt", "t shirt", "men tshirt", "mens tshirt", "cotton tshirt", "oversized tshirt"],
    "mens-jeans-trousers": ["jeans", "trousers", "chinos", "pants", "men jeans", "mens jeans"],
    "womens-dresses": ["dress", "dresses", "women dress", "maxi dress", "party dress"],
    "womens-tops": ["top", "tops", "women top", "kurti", "blouse"],
    "home-appliances": ["home appliance", "appliance", "fan", "mixer", "vacuum", "air fryer", "washing machine", "refrigerator"],
    "kitchen-dining": ["kitchen", "dining", "bottle", "cookware", "lunch box", "pressure cooker", "water bottle", "tawa", "flask"],
    "fitness-sports": ["fitness", "sports", "gym", "yoga mat", "badminton", "football", "cricket", "dumbbell"],
    "beauty-grooming": ["beauty", "grooming", "lipstick", "serum", "shampoo", "trimmer", "sunscreen", "face wash"],
    "books-stationery": ["book", "books", "stationery", "notebook", "notebooks", "planner", "pen", "paper"],
    "accessories-cables": ["accessory", "accessories", "charger", "cable", "usb cable", "usb-c cable", "keyboard", "mouse", "adapter", "phone stand"],
}

SPECIAL_EXPECTATIONS = {
    "mobile stand": {"category": "accessories-cables"},
    "phone stand": {"category": "accessories-cables"},
    "tablet stand": {"category": "accessories-cables"},
    "notebook": {"category": "books-stationery"},
    "notebooks": {"category": "books-stationery"},
}

TYPO_REPLACEMENTS = {
    "phone": "fone",
    "mobile": "moblie",
    "laptop": "lapotp",
    "headphones": "headfones",
    "earbuds": "earbudz",
    "shoes": "shoos",
    "watch": "wach",
    "charger": "chargre",
    "shirt": "shrit",
    "dress": "dres",
}

SEMANTIC_INTENTS = [
    ("comfortable shoes for daily walking", {"category": "footwear"}),
    ("wireless audio for travel", {"category": "earbuds-headphones"}),
    ("portable computer for office work", {"category": "laptops"}),
    ("phone with good camera", {"category": "smartphones"}),
    ("bag for carrying laptop", {"category": "bags-wallets"}),
    ("bottle for gym and travel", {"category": "kitchen-dining"}),
    ("skin care for sunny days", {"category": "beauty-grooming"}),
    ("keyboard and mouse accessories", {"category": "accessories-cables"}),
    ("books and notebooks for study", {"category": "books-stationery"}),
    ("home appliance for kitchen cooking", {"category": "home-appliances"}),
]


@dataclass(frozen=True)
class EvalCase:
    query: str
    kind: str
    expected: dict[str, Any]


def fetch_json(url: str, timeout: float) -> dict[str, Any]:
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def fetch_all_products(base_url: str, timeout: float, page_size: int = 100) -> list[dict[str, Any]]:
    products: list[dict[str, Any]] = []
    page = 0
    total_pages = 1
    while page < total_pages:
        response = fetch_json(f"{base_url}/api/v1/products?page={page}&size={page_size}", timeout)
        page_products = response.get("data") or response.get("items") or []
        products.extend(page_products)
        total_pages = int(response.get("totalPages") or 1)
        page += 1
    return products


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def words(text: str | None) -> list[str]:
    if not text:
        return []
    return [word for word in re.findall(r"[a-z0-9]+", text.lower()) if word not in STOP_WORDS]


def product_id(product: dict[str, Any]) -> str:
    return str(product["id"])


def product_category(product: dict[str, Any]) -> str | None:
    return product.get("categorySlug") or product.get("category")


def product_brand(product: dict[str, Any]) -> str | None:
    brand = product.get("brand")
    return str(brand).lower() if brand else None


def product_attributes(product: dict[str, Any]) -> dict[str, str]:
    attrs = product.get("attributes") or {}
    if not isinstance(attrs, dict):
        return {}
    return {str(key): str(value) for key, value in attrs.items() if value is not None and str(value).strip()}


def product_name(product: dict[str, Any]) -> str:
    return normalize_text(str(product.get("name") or "")).lower()


def main_terms(product: dict[str, Any]) -> list[str]:
    brand_words = set(words(product.get("brand")))
    return [word for word in words(product.get("name")) if word not in brand_words]


def compact_name_query(product: dict[str, Any]) -> str:
    terms = main_terms(product)
    return " ".join(terms[:3]) if len(terms) >= 2 else str(product.get("name") or "")


def noun_query(product: dict[str, Any]) -> str:
    terms = main_terms(product)
    return terms[-1] if terms else str(product.get("name") or "")


def attr_pairs(product: dict[str, Any]) -> list[tuple[str, str]]:
    generated_only_keys = {"edition", "variant"}
    return [(key, value) for key, value in product_attributes(product).items() if key not in generated_only_keys]


def product_expectation(product: dict[str, Any]) -> dict[str, Any]:
    category = product_category(product)
    brand = product_brand(product)
    if brand and category:
        return {"brand": brand, "category": category}
    if category:
        return {"category": category}
    return {"id": product_id(product)}


def add_case(cases: list[EvalCase], seen: set[str], query: str, kind: str, expected: dict[str, Any]) -> None:
    query = normalize_text(query)
    if not query:
        return
    key = query.lower()
    if key in seen:
        return
    seen.add(key)
    cases.append(EvalCase(query=query, kind=kind, expected=expected))


def typo_variant(query: str) -> str | None:
    lowered = query.lower()
    for source, replacement in TYPO_REPLACEMENTS.items():
        if source in lowered:
            return re.sub(source, replacement, query, count=1, flags=re.IGNORECASE)
    terms = query.split()
    if terms and len(terms[0]) > 4:
        word = terms[0]
        terms[0] = word[:2] + word[3] + word[2] + word[4:]
        return " ".join(terms)
    return None


def build_cases(products: list[dict[str, Any]], count: int, seed: int) -> list[EvalCase]:
    random.seed(seed)
    by_brand: dict[str, list[dict[str, Any]]] = defaultdict(list)
    by_category: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for product in products:
        category = product_category(product)
        if category:
            by_category[category].append(product)
        brand = product_brand(product)
        if brand:
            by_brand[brand].append(product)

    cases: list[EvalCase] = []
    seen: set[str] = set()

    for product in products:
        pid = product_id(product)
        category = product_category(product)
        add_case(cases, seen, str(product.get("name") or ""), "product_exact", {"name": product_name(product)})
        add_case(cases, seen, compact_name_query(product), "product_compact", product_expectation(product))

        brand = product.get("brand")
        if brand:
            add_case(cases, seen, f"{brand} {noun_query(product)}", "brand_product", product_expectation(product))
            if category:
                add_case(
                    cases,
                    seen,
                    f"{brand} {category.replace('-', ' ')}",
                    "brand_category",
                    {"brand": str(brand).lower(), "category": category},
                )

        for key, value in attr_pairs(product)[:2]:
            attr_expected = {"attribute": {key: value}}
            if category:
                attr_expected["category"] = category
            add_case(cases, seen, f"{value} {noun_query(product)}", "attribute_product", attr_expected)
            if category:
                add_case(
                    cases,
                    seen,
                    f"{value} {category.replace('-', ' ')}",
                    "attribute_category",
                    {"category": category, "attribute": {key: value}},
                )

    for category, aliases in CATEGORY_ALIASES.items():
        for alias in aliases:
            expected = SPECIAL_EXPECTATIONS.get(alias, {"category": category})
            for template in ["{}", "best {}", "buy {}", "{} online", "new {}"]:
                add_case(cases, seen, template.format(alias), "category_alias", expected)

    for brand, brand_products in sorted(by_brand.items()):
        add_case(cases, seen, brand, "brand", {"brand": brand})
        categories = sorted({product_category(product) for product in brand_products if product_category(product)})
        for category in categories:
            add_case(
                cases,
                seen,
                f"{brand} {category.replace('-', ' ')}",
                "brand_category",
                {"brand": brand, "category": category},
            )

    for product in products:
        category = product_category(product)
        for key, value in attr_pairs(product):
            add_case(cases, seen, f"{key} {value}", "attribute_value", {"attribute": {key: value}})
            attr_expected = {"attribute": {key: value}}
            if category:
                attr_expected["category"] = category
            add_case(cases, seen, f"{value} {noun_query(product)}", "attribute_product", attr_expected)
            if category:
                add_case(
                    cases,
                    seen,
                    f"{value} {category.replace('-', ' ')}",
                    "attribute_category",
                    {"category": category, "attribute": {key: value}},
                )

    modifiers = ["premium", "budget", "latest", "popular", "comfortable", "durable", "wireless", "cotton", "black", "blue"]
    for modifier in modifiers:
        for category, aliases in CATEGORY_ALIASES.items():
            for alias in aliases[:4]:
                expected = SPECIAL_EXPECTATIONS.get(alias, {"category": category})
                add_case(cases, seen, f"{modifier} {alias}", "modified_category", {"category": expected["category"]})

    for product in products:
        terms = main_terms(product)
        if len(terms) >= 2:
            add_case(cases, seen, " ".join(terms[-2:]), "product_tail", product_expectation(product))
        description_terms = words(product.get("description"))
        if len(description_terms) >= 2:
            add_case(cases, seen, " ".join(description_terms[:3]), "description_phrase", product_expectation(product))

    price_templates = [
        "cheap {}",
        "affordable {}",
        "premium {}",
        "budget {}",
        "{} under 1000",
        "{} under 5000",
        "{} under 50000",
        "best rated {}",
        "popular {}",
        "latest {}",
    ]
    for category, aliases in CATEGORY_ALIASES.items():
        for alias in aliases:
            expected = SPECIAL_EXPECTATIONS.get(alias, {"category": category})
            for template in price_templates:
                add_case(cases, seen, template.format(alias), "commercial_category", {"category": expected["category"]})
            add_case(cases, seen, f"{alias} in stock", "stock_intent", {"category": expected["category"]})
            add_case(cases, seen, f"{alias} with discount", "deal_intent", {"category": expected["category"]})

    brand_modifiers = ["best", "new", "latest", "premium", "budget", "popular", "compact", "durable", "wireless", "daily"]
    for brand, brand_products in sorted(by_brand.items()):
        categories = sorted({product_category(product) for product in brand_products if product_category(product)})
        for modifier in brand_modifiers:
            add_case(cases, seen, f"{modifier} {brand}", "modified_brand", {"brand": brand})
            for category in categories[:4]:
                add_case(
                    cases,
                    seen,
                    f"{modifier} {brand} {category.replace('-', ' ')}",
                    "modified_brand_category",
                    {"brand": brand, "category": category},
                )

    for query, expected in SEMANTIC_INTENTS:
        add_case(cases, seen, query, "semantic_intent", expected)

    category_products = [(category, product) for category, items in sorted(by_category.items()) for product in items]
    attribute_modifiers = ["best", "new", "premium", "budget", "popular", "latest"]
    for category, product in category_products:
        for key, value in attr_pairs(product):
            for modifier in attribute_modifiers:
                add_case(
                    cases,
                    seen,
                    f"{modifier} {value} {category.replace('-', ' ')}",
                    "modified_attribute_category",
                    {"category": category, "attribute": {key: value}},
                )

    typo_sources = list(cases)
    for case in typo_sources:
        typo = typo_variant(case.query)
        if typo:
            add_case(cases, seen, typo, f"typo_{case.kind}", case.expected)
        if len(cases) >= count:
            break

    if len(cases) < count:
        filler_modifiers = ["recommended", "top", "value", "sale", "online", "lightweight", "heavy duty", "everyday", "office", "travel"]
        for modifier in filler_modifiers:
            for product in products:
                category = product_category(product)
                if category:
                    add_case(
                        cases,
                        seen,
                        f"{modifier} {compact_name_query(product)}",
                        "modified_product",
                        {"category": category},
                    )
                if len(cases) >= count:
                    break
            if len(cases) >= count:
                break

    return cases[:count]


def matches_attribute(product: dict[str, Any], expected_attr: dict[str, str]) -> bool:
    attrs = product_attributes(product)
    for key, expected_value in expected_attr.items():
        actual = attrs.get(key)
        if actual is None or str(actual).lower() != str(expected_value).lower():
            return False
    return True


def is_relevant(product: dict[str, Any], expected: dict[str, Any]) -> bool:
    if "id" in expected:
        return product_id(product) == str(expected["id"])
    if "name" in expected and product_name(product) != str(expected["name"]).lower():
        return False
    if "brand" in expected and product_brand(product) != str(expected["brand"]).lower():
        return False
    if "category" in expected and product_category(product) != expected["category"]:
        return False
    if "attribute" in expected and not matches_attribute(product, expected["attribute"]):
        return False
    return bool(expected)


def relevant_ids(products: list[dict[str, Any]], expected: dict[str, Any]) -> set[str]:
    return {product_id(product) for product in products if is_relevant(product, expected)}


def build_relevance_index(products: list[dict[str, Any]]) -> dict[str, Any]:
    all_ids: set[str] = set()
    by_id: dict[str, set[str]] = {}
    by_name: dict[str, set[str]] = defaultdict(set)
    by_brand: dict[str, set[str]] = defaultdict(set)
    by_category: dict[str, set[str]] = defaultdict(set)
    by_attribute: dict[tuple[str, str], set[str]] = defaultdict(set)

    for product in products:
        pid = product_id(product)
        all_ids.add(pid)
        by_id[pid] = {pid}
        by_name[product_name(product)].add(pid)

        brand = product_brand(product)
        if brand:
            by_brand[brand].add(pid)

        category = product_category(product)
        if category:
            by_category[category].add(pid)

        for key, value in product_attributes(product).items():
            by_attribute[(str(key), str(value).lower())].add(pid)

    return {
        "all": all_ids,
        "id": by_id,
        "name": by_name,
        "brand": by_brand,
        "category": by_category,
        "attribute": by_attribute,
    }


def indexed_relevant_ids(index: dict[str, Any], expected: dict[str, Any]) -> set[str]:
    candidates = set(index["all"])
    if "id" in expected:
        candidates &= index["id"].get(str(expected["id"]), set())
    if "name" in expected:
        candidates &= index["name"].get(str(expected["name"]).lower(), set())
    if "brand" in expected:
        candidates &= index["brand"].get(str(expected["brand"]).lower(), set())
    if "category" in expected:
        candidates &= index["category"].get(expected["category"], set())
    if "attribute" in expected:
        for key, value in expected["attribute"].items():
            candidates &= index["attribute"].get((str(key), str(value).lower()), set())
    return candidates if expected else set()


def search(base_url: str, case: EvalCase, size: int, timeout: float, mode: str | None) -> dict[str, Any]:
    params: dict[str, Any] = {"q": case.query, "sort": "relevance", "page": 0, "size": size}
    if mode:
        params["mode"] = mode
    query_string = urllib.parse.urlencode(params)
    return fetch_json(f"{base_url}/api/v1/search?{query_string}", timeout)


def dcg(relevance: list[int]) -> float:
    return sum(rel / math.log2(index + 2) for index, rel in enumerate(relevance))


def average_precision(relevance: list[int], relevant_count: int) -> float:
    if relevant_count == 0:
        return 0.0
    hits = 0
    total_precision = 0.0
    for index, rel in enumerate(relevance, start=1):
        if rel:
            hits += 1
            total_precision += hits / index
    return total_precision / min(relevant_count, len(relevance))


def reciprocal_rank(relevance: list[int]) -> float:
    for index, rel in enumerate(relevance, start=1):
        if rel:
            return 1.0 / index
    return 0.0


def evaluate_case(
    case: EvalCase,
    response: dict[str, Any],
    products_by_id: dict[str, dict[str, Any]],
    expected_ids: set[str],
    k_values: list[int],
) -> dict[str, Any]:
    items = response.get("items") or []
    ids = [str(item.get("id")) for item in items if item.get("id") is not None]
    relevance = [1 if pid in expected_ids else 0 for pid in ids]
    max_k = max(k_values)
    padded_relevance = relevance + [0] * max(0, max_k - len(relevance))

    metrics: dict[str, float] = {
        "mrr": reciprocal_rank(padded_relevance[:max_k]),
        "ap": average_precision(padded_relevance[:max_k], len(expected_ids)),
    }

    for k in k_values:
        top_k = padded_relevance[:k]
        hits = sum(top_k)
        ideal_hits = min(len(expected_ids), k)
        metrics[f"precision@{k}"] = hits / k
        metrics[f"recall@{k}"] = hits / len(expected_ids) if expected_ids else 0.0
        metrics[f"hit@{k}"] = 1.0 if hits > 0 else 0.0
        metrics[f"ndcg@{k}"] = dcg(top_k) / dcg([1] * ideal_hits) if ideal_hits else 0.0

    top_product = products_by_id.get(ids[0]) if ids else None
    ok = metrics.get(f"hit@{max_k}", 0.0) > 0
    reason = "matched"
    if not ids:
        reason = "zero_results"
    elif not ok:
        reason = "no_relevant_result_in_top_k"
    elif top_product and not is_relevant(top_product, case.expected):
        reason = "relevant_result_not_top1"

    return {
        "query": case.query,
        "kind": case.kind,
        "expected": case.expected,
        "expectedCount": len(expected_ids),
        "total": response.get("total"),
        "ok": ok,
        "reason": reason,
        "metrics": metrics,
        "topResults": [
            {
                "id": pid,
                "name": (products_by_id.get(pid) or {}).get("name"),
                "category": product_category(products_by_id.get(pid) or {}),
                "brand": (products_by_id.get(pid) or {}).get("brand"),
                "relevant": pid in expected_ids,
            }
            for pid in ids[:max_k]
        ],
    }


def mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * pct)))
    return ordered[index]


def summarize(results: list[dict[str, Any]], k_values: list[int], latencies_ms: list[float]) -> dict[str, Any]:
    failures = [result for result in results if not result["ok"]]
    summary: dict[str, Any] = {
        "cases": len(results),
        "pass": len(results) - len(failures),
        "fail": len(failures),
        "passRate": round((len(results) - len(failures)) / len(results), 4) if results else 0.0,
        "latencyMs": {
            "avg": round(mean(latencies_ms), 2),
            "p50": round(percentile(latencies_ms, 0.50), 2),
            "p95": round(percentile(latencies_ms, 0.95), 2),
            "max": round(max(latencies_ms), 2) if latencies_ms else 0.0,
        },
        "failuresByReason": dict(Counter(result["reason"] for result in failures)),
        "failuresByKind": dict(Counter(result["kind"] for result in failures)),
        "metrics": {
            "mrr": round(mean([result["metrics"]["mrr"] for result in results]), 4),
            "map": round(mean([result["metrics"]["ap"] for result in results]), 4),
        },
    }
    for k in k_values:
        summary["metrics"][f"precision@{k}"] = round(mean([result["metrics"][f"precision@{k}"] for result in results]), 4)
        summary["metrics"][f"recall@{k}"] = round(mean([result["metrics"][f"recall@{k}"] for result in results]), 4)
        summary["metrics"][f"hit@{k}"] = round(mean([result["metrics"][f"hit@{k}"] for result in results]), 4)
        summary["metrics"][f"ndcg@{k}"] = round(mean([result["metrics"][f"ndcg@{k}"] for result in results]), 4)
    return summary


def write_jsonl(path: str, rows: list[dict[str, Any]]) -> None:
    with open(path, "w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def write_csv(path: str, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "query",
        "kind",
        "ok",
        "reason",
        "expected",
        "expectedCount",
        "total",
        "topResults",
        "metrics",
    ]
    with open(path, "w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "query": row["query"],
                    "kind": row["kind"],
                    "ok": row["ok"],
                    "reason": row["reason"],
                    "expected": json.dumps(row["expected"], sort_keys=True),
                    "expectedCount": row["expectedCount"],
                    "total": row["total"],
                    "topResults": json.dumps(row["topResults"], ensure_ascii=False),
                    "metrics": json.dumps(row["metrics"], sort_keys=True),
                }
            )


def write_markdown(path: str, summary: dict[str, Any], failures: list[dict[str, Any]]) -> None:
    metrics = summary.get("metrics", {})
    latency = summary.get("latencyMs", {})
    lines = [
        "# Search Relevance Metrics",
        "",
        f"Generated mode: `{summary.get('mode')}`",
        f"Base URL: `{summary.get('baseUrl')}`",
        f"Cases: `{summary.get('cases')}`",
        f"Pass rate: `{summary.get('passRate')}`",
        f"Elapsed seconds: `{summary.get('elapsedSec')}`",
        "",
        "## Ranking Metrics",
        "",
        "| Metric | Value |",
        "|---|---:|",
    ]
    for key in sorted(metrics.keys()):
        lines.append(f"| `{key}` | {metrics[key]} |")

    lines.extend([
        "",
        "## Latency",
        "",
        "| Metric | ms |",
        "|---|---:|",
    ])
    for key in ["avg", "p50", "p95", "max"]:
        lines.append(f"| `{key}` | {latency.get(key)} |")

    lines.extend([
        "",
        "## Failure Breakdown",
        "",
        f"- Failures: `{summary.get('fail')}`",
        f"- Failures by reason: `{json.dumps(summary.get('failuresByReason', {}), sort_keys=True)}`",
        f"- Failures by kind: `{json.dumps(summary.get('failuresByKind', {}), sort_keys=True)}`",
    ])

    if failures:
        lines.extend([
            "",
            "## Sample Failures",
            "",
            "| Query | Kind | Reason |",
            "|---|---|---|",
        ])
        for failure in failures[:20]:
            lines.append(f"| `{failure['query']}` | `{failure['kind']}` | `{failure['reason']}` |")

    lines.extend([
        "",
        "## Resume Metric Template",
        "",
        (
            "- Evaluated hybrid search quality across "
            f"{summary.get('cases')} catalog-aware generated queries, achieving "
            f"Precision@10={metrics.get('precision@10')}, Recall@10={metrics.get('recall@10')}, "
            f"NDCG@10={metrics.get('ndcg@10')}, MRR={metrics.get('mrr')}, "
            f"and p95 evaluation latency of {latency.get('p95')} ms."
        ),
        "",
    ])
    with open(path, "w", encoding="utf-8") as file:
        file.write("\n".join(lines))


def parse_k_values(value: str) -> list[int]:
    values = sorted({int(part.strip()) for part in value.split(",") if part.strip()})
    if not values or any(value <= 0 for value in values):
        raise argparse.ArgumentTypeError("--k must contain positive integers")
    return values


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run catalog search relevance evaluation.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="API gateway or search service base URL")
    parser.add_argument("--count", type=int, default=10000, help="Number of generated queries to evaluate")
    parser.add_argument("--k", type=parse_k_values, default=parse_k_values("1,3,5,10"), help="Comma-separated metric cutoffs")
    parser.add_argument("--workers", type=int, default=6, help="Concurrent search requests")
    parser.add_argument("--timeout", type=float, default=20.0, help="HTTP timeout in seconds")
    parser.add_argument("--seed", type=int, default=42, help="Deterministic query generation seed")
    parser.add_argument("--mode", choices=["hybrid", "text", "vector"], help="Optional hidden/dev search mode")
    parser.add_argument("--dump-queries", help="Write generated query cases as JSONL")
    parser.add_argument("--generate-only", action="store_true", help="Generate query cases and exit without running searches")
    parser.add_argument("--output-json", help="Write full evaluation results as JSON")
    parser.add_argument("--output-csv", help="Write per-query evaluation results as CSV")
    parser.add_argument("--output-md", help="Write markdown summary report")
    parser.add_argument("--fail-under", type=float, help="Exit non-zero if passRate is below this value, e.g. 0.99")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    base_url = args.base_url.rstrip("/")

    products = fetch_all_products(base_url, args.timeout)
    if not products:
        print("No products returned from catalog API.", file=sys.stderr)
        return 2

    products_by_id = {product_id(product): product for product in products}
    cases = build_cases(products, args.count, args.seed)
    if args.dump_queries:
        write_jsonl(args.dump_queries, [{"query": case.query, "kind": case.kind, "expected": case.expected} for case in cases])
    if args.generate_only:
        print(json.dumps({"cases": len(cases), "dumpQueries": args.dump_queries}, indent=2, sort_keys=True))
        return 0

    relevance_index = build_relevance_index(products)
    expected_by_query = {case.query: indexed_relevant_ids(relevance_index, case.expected) for case in cases}
    k_values = args.k
    max_k = max(k_values)
    latencies_ms: list[float] = []

    def run_case(case: EvalCase) -> dict[str, Any]:
        started = time.perf_counter()
        try:
            response = search(base_url, case, max_k, args.timeout, args.mode)
            latency_ms = (time.perf_counter() - started) * 1000
            latencies_ms.append(latency_ms)
            result = evaluate_case(case, response, products_by_id, expected_by_query[case.query], k_values)
            result["latencyMs"] = round(latency_ms, 2)
            return result
        except Exception as ex:  # noqa: BLE001 - this is a CLI evaluator.
            latency_ms = (time.perf_counter() - started) * 1000
            latencies_ms.append(latency_ms)
            empty_metrics = {"mrr": 0.0, "ap": 0.0}
            for k in k_values:
                empty_metrics[f"precision@{k}"] = 0.0
                empty_metrics[f"recall@{k}"] = 0.0
                empty_metrics[f"hit@{k}"] = 0.0
                empty_metrics[f"ndcg@{k}"] = 0.0
            return {
                "query": case.query,
                "kind": case.kind,
                "expected": case.expected,
                "expectedCount": len(expected_by_query.get(case.query, set())),
                "total": None,
                "ok": False,
                "reason": "exception",
                "error": repr(ex),
                "metrics": empty_metrics,
                "topResults": [],
                "latencyMs": round(latency_ms, 2),
            }

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        results = list(executor.map(run_case, cases))
    elapsed_sec = time.perf_counter() - started

    summary = summarize(results, k_values, latencies_ms)
    summary["elapsedSec"] = round(elapsed_sec, 2)
    summary["baseUrl"] = base_url
    summary["mode"] = args.mode or "default"

    payload = {
        "summary": summary,
        "failures": [result for result in results if not result["ok"]][:100],
        "results": results,
    }

    if args.output_json:
        with open(args.output_json, "w", encoding="utf-8") as file:
            json.dump(payload, file, indent=2, ensure_ascii=False, sort_keys=True)
    if args.output_csv:
        write_csv(args.output_csv, results)
    if args.output_md:
        write_markdown(args.output_md, summary, payload["failures"])

    print(json.dumps(summary, indent=2, sort_keys=True))
    failures = [result for result in results if not result["ok"]]
    if failures:
        print("\nTop failures:")
        for failure in failures[:10]:
            print(
                json.dumps(
                    {
                        "query": failure["query"],
                        "kind": failure["kind"],
                        "reason": failure["reason"],
                        "expected": failure["expected"],
                        "topResults": failure["topResults"][:5],
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                )
            )

    if args.fail_under is not None and summary["passRate"] < args.fail_under:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
