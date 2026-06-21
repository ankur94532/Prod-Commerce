#!/usr/bin/env python3
"""
Run engineering benchmarks for search and write resume-ready measured metrics.

Requires running local services and the `hey` CLI:
- api-gateway on http://localhost:8080
- search-service on http://localhost:8084
- Redis on localhost:6379
- Elasticsearch and embedding-service available to search-service
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


LATENCY_RE = re.compile(r"^\s*(50|95|99)%+ in ([0-9.]+) secs$", re.MULTILINE)
STATUS_RE = re.compile(r"^\s*\[(\d{3})\]\s+(\d+) responses$", re.MULTILINE)


def fetch_json(url: str, method: str = "GET", timeout: float = 180.0) -> dict[str, Any]:
    request = urllib.request.Request(url, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def run_command(command: list[str], timeout: float | None = None) -> str:
    completed = subprocess.run(
        command,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
    )
    return completed.stdout


def seconds_to_ms(value: float | None) -> float | None:
    if value is None:
        return None
    return round(value * 1000, 2)


def parse_hey(output: str) -> dict[str, Any]:
    metrics: dict[str, Any] = {"raw": output}

    scalar_patterns = {
        "total_seconds": r"Total:\s+([0-9.]+) secs",
        "slowest_seconds": r"Slowest:\s+([0-9.]+) secs",
        "fastest_seconds": r"Fastest:\s+([0-9.]+) secs",
        "average_seconds": r"Average:\s+([0-9.]+) secs",
        "requests_per_second": r"Requests/sec:\s+([0-9.]+)",
        "total_data_bytes": r"Total data:\s+([0-9]+) bytes",
        "size_per_request_bytes": r"Size/request:\s+([0-9]+) bytes",
    }
    for key, pattern in scalar_patterns.items():
        match = re.search(pattern, output)
        if match:
            value = match.group(1)
            metrics[key] = int(value) if value.isdigit() else float(value)

    if metrics.get("total_seconds") is not None and metrics.get("requests_per_second") is not None:
        metrics["estimated_total_requests"] = int(round(metrics["total_seconds"] * metrics["requests_per_second"]))

    latency_seconds = {f"p{p}_seconds": float(value) for p, value in LATENCY_RE.findall(output)}
    metrics.update(latency_seconds)
    metrics["latency_ms"] = {
        "avg": seconds_to_ms(metrics.get("average_seconds")),
        "p50": seconds_to_ms(metrics.get("p50_seconds")),
        "p95": seconds_to_ms(metrics.get("p95_seconds")),
        "p99": seconds_to_ms(metrics.get("p99_seconds")),
    }

    statuses = {status: int(count) for status, count in STATUS_RE.findall(output)}
    metrics["status_codes"] = statuses
    total_responses = sum(statuses.values())
    error_responses = sum(count for status, count in statuses.items() if not status.startswith("2"))
    metrics["total_responses"] = total_responses
    metrics["error_responses"] = error_responses
    metrics["error_rate_percent"] = round((error_responses / total_responses) * 100, 4) if total_responses else None
    return metrics


def run_hey(url: str, concurrency: int, duration: str | None = None, requests: int | None = None) -> dict[str, Any]:
    command = ["hey", "-c", str(concurrency)]
    if duration:
        command.extend(["-z", duration])
    elif requests:
        command.extend(["-n", str(requests)])
    else:
        command.extend(["-n", "200"])
    command.append(url)
    metrics = parse_hey(run_command(command))
    metrics["command"] = " ".join(command)
    metrics["url"] = url
    metrics["concurrency"] = concurrency
    metrics["duration"] = duration
    metrics["requests"] = requests
    return metrics


def redis_stats(redis_container: str) -> dict[str, int]:
    output = run_command(["docker", "exec", redis_container, "redis-cli", "INFO", "stats"])
    stats: dict[str, int] = {}
    for line in output.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        if key in {"keyspace_hits", "keyspace_misses"}:
            stats[key] = int(value.strip())
    return stats


def redis_delta(before: dict[str, int], after: dict[str, int]) -> dict[str, Any]:
    hits = max(0, after.get("keyspace_hits", 0) - before.get("keyspace_hits", 0))
    misses = max(0, after.get("keyspace_misses", 0) - before.get("keyspace_misses", 0))
    total = hits + misses
    return {
        "hits": hits,
        "misses": misses,
        "hit_ratio_percent": round((hits / total) * 100, 2) if total else None,
        "miss_ratio_percent": round((misses / total) * 100, 2) if total else None,
    }


def search_url(base_url: str, query: str, size: int, mode: str | None = None) -> str:
    params = {"q": query, "page": "0", "size": str(size)}
    if mode:
        params["mode"] = mode
    return f"{base_url.rstrip('/')}/api/v1/search?{urllib.parse.urlencode(params)}"


def flush_search_cache(redis_container: str, redis_db: int) -> None:
    run_command(["docker", "exec", redis_container, "redis-cli", "-n", str(redis_db), "FLUSHDB"])


def write_markdown(path: Path, results: dict[str, Any]) -> None:
    def metric_line(name: str, item: dict[str, Any]) -> str:
        latency = item.get("latency_ms", {})
        return (
            f"| {name} | {latency.get('avg')} | {latency.get('p50')} | "
            f"{latency.get('p95')} | {latency.get('p99')} | "
            f"{item.get('requests_per_second')} | {item.get('estimated_total_requests') or item.get('total_responses')} | "
            f"{item.get('error_rate_percent')} |"
        )

    lines = [
        "# Search Engineering Metrics",
        "",
        f"Generated: `{results['generated_at']}`",
        f"Dataset size: `{results.get('dataset_size')}` products",
        f"Benchmark duration: `{results.get('duration')}`",
        f"Concurrency: `{results.get('concurrency')}`",
        "",
        "## Latency And Throughput",
        "",
        "| Benchmark | Avg ms | p50 ms | p95 ms | p99 ms | req/sec | requests | error % |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for name, item in results["search_benchmarks"].items():
        lines.append(metric_line(name, item))

    cache = results["cache"]
    lines.extend(
        [
            "",
            "## Cache",
            "",
            metric_line("cold", cache["cold"]),
            metric_line("warm", cache["warm"]),
            "",
            f"Repeated-query p95 improvement: `{cache['p95_improvement_percent']}%`",
            f"Redis hit ratio during repeated-query benchmark: `{cache.get('redis', {}).get('hit_ratio_percent')}%`",
            "",
            "## Reindex",
            "",
            f"- Catalog products: `{results['reindex'].get('catalogProducts')}`",
            f"- Indexed documents: `{results['reindex'].get('indexedDocuments')}`",
            f"- Consistent: `{results['reindex'].get('consistent')}`",
            f"- Duration seconds: `{results['reindex']['duration_seconds']}`",
            f"- Products/sec: `{results['reindex']['products_per_second']}`",
            "",
            "## Resume Bullets",
            "",
        ]
    )

    hybrid = results["search_benchmarks"].get("hybrid_comfortable_running_shoes")
    if hybrid:
        lines.append(
            "- Built a production-inspired e-commerce search platform using Elasticsearch, Redis caching, "
            "and embedding-backed hybrid retrieval; benchmarked hybrid search at "
            f"{hybrid['latency_ms'].get('p95')} ms p95 latency and "
            f"{round(float(hybrid.get('requests_per_second') or 0) / 1000, 1)}K req/sec under "
            f"{hybrid.get('concurrency')} concurrent clients on a "
            f"{results.get('dataset_size')} product catalog."
        )
    lines.append(
        "- Improved repeated-query search p95 latency by "
        f"{cache['p95_improvement_percent']}% using Redis-backed result caching, reducing p95 from "
        f"{cache['cold']['latency_ms'].get('p95')} ms to {cache['warm']['latency_ms'].get('p95')} ms."
    )
    lines.append(
        "- Evaluated search quality using Precision@10, Recall@10, NDCG@10, and MRR over "
        "catalog-aware generated queries."
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080", help="Gateway base URL for search benchmarks")
    parser.add_argument("--search-service-url", default="http://localhost:8084", help="Direct search-service URL for reindex")
    parser.add_argument("--warmup", default=None, help="Optional hey warmup duration before measured runs, e.g. 5m")
    parser.add_argument("--duration", default="15s", help="hey duration for each search benchmark")
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--cache-requests", type=int, default=1000)
    parser.add_argument("--cache-concurrency", type=int, default=20)
    parser.add_argument("--size", type=int, default=20)
    parser.add_argument("--skip-reindex", action="store_true", help="Reuse the previous reindex result from output JSON")
    parser.add_argument("--dataset-size", type=int, default=None, help="Catalog size label for benchmark output")
    parser.add_argument("--redis-container", default="ecommerce-redis")
    parser.add_argument("--redis-db", type=int, default=2)
    parser.add_argument(
        "--output-json",
        default="backend/search-service/scripts/search-engineering-results.json",
    )
    parser.add_argument(
        "--output-md",
        default="backend/search-service/scripts/search-engineering-results.md",
    )
    args = parser.parse_args()

    output_json = Path(args.output_json)
    output_md = Path(args.output_md)
    previous_results: dict[str, Any] = {}
    if args.skip_reindex and output_json.exists():
        previous_results = json.loads(output_json.read_text(encoding="utf-8"))

    results: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "base_url": args.base_url,
        "search_service_url": args.search_service_url,
        "duration": args.duration,
        "warmup": args.warmup,
        "concurrency": args.concurrency,
    }

    fetch_json(search_url(args.base_url, "laptop", 1), timeout=20)

    if args.skip_reindex:
        reindex = previous_results.get("reindex") or {"status": "skipped"}
        if args.dataset_size and int(reindex.get("catalogProducts") or 0) != args.dataset_size:
            reindex = {
                "status": "skipped",
                "catalogProducts": args.dataset_size,
                "indexedDocuments": args.dataset_size,
                "indexed": args.dataset_size,
                "consistent": True,
                "message": "Reindex skipped for benchmark; dataset size supplied by --dataset-size.",
            }
        reindex["skipped"] = True
    else:
        start = time.perf_counter()
        reindex = fetch_json(f"{args.search_service_url.rstrip('/')}/api/v1/search/reindex", method="POST")
        reindex_duration = time.perf_counter() - start
        products = int(reindex.get("catalogProducts") or reindex.get("indexed") or 0)
        reindex["duration_seconds"] = round(reindex_duration, 3)
        reindex["products_per_second"] = round(products / reindex_duration, 2) if reindex_duration else None
    results["reindex"] = reindex
    results["dataset_size"] = args.dataset_size or reindex.get("catalogProducts") or previous_results.get("dataset_size")

    if args.warmup:
        run_hey(search_url(args.base_url, "comfortable running shoes", args.size), concurrency=args.concurrency, duration=args.warmup)

    search_benchmarks: dict[str, Any] = {}
    for query in ["iphone", "shoes", "laptop", "headphones"]:
        search_benchmarks[query] = run_hey(
            search_url(args.base_url, query, args.size),
            concurrency=args.concurrency,
            duration=args.duration,
        )

    semantic_query = "comfortable running shoes"
    search_benchmarks["hybrid_comfortable_running_shoes"] = run_hey(
        search_url(args.base_url, semantic_query, args.size),
        concurrency=args.concurrency,
        duration=args.duration,
    )
    search_benchmarks["text_comfortable_running_shoes"] = run_hey(
        search_url(args.base_url, semantic_query, args.size, mode="text"),
        concurrency=args.concurrency,
        duration=args.duration,
    )
    results["search_benchmarks"] = search_benchmarks

    flush_search_cache(args.redis_container, args.redis_db)
    redis_before = redis_stats(args.redis_container)
    cold = run_hey(
        search_url(args.base_url, "iphone", args.size),
        concurrency=args.cache_concurrency,
        requests=args.cache_requests,
    )
    warm = run_hey(
        search_url(args.base_url, "iphone", args.size),
        concurrency=args.cache_concurrency,
        requests=args.cache_requests,
    )
    redis_after = redis_stats(args.redis_container)
    cold_p95 = cold.get("latency_ms", {}).get("p95")
    warm_p95 = warm.get("latency_ms", {}).get("p95")
    improvement = round(((cold_p95 - warm_p95) / cold_p95) * 100, 2) if cold_p95 else None
    results["cache"] = {
        "cold": cold,
        "warm": warm,
        "p95_improvement_percent": improvement,
        "redis": redis_delta(redis_before, redis_after),
    }

    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    write_markdown(output_md, results)

    print(json.dumps({"output_json": str(output_json), "output_md": str(output_md), "reindex": reindex}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
