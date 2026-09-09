#!/usr/bin/env python3
"""Builds a deterministic query set for the load test.

The previous benchmark cycled five hard-coded queries across every virtual user, so after
the first few seconds nearly every request was a cache hit and the run measured Redis
rather than search. This samples a large, seeded, deduplicated set from the generated query
corpus so a "cold" run genuinely misses, and records enough provenance that a later run can
be compared to an earlier one.

Note what the corpus is: catalog-derived synthetic queries, not shopper traffic. A load test
built on it measures the system's behaviour under a plausible query mix, not real demand.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import random
import sys


def load_corpus(path: pathlib.Path) -> list[dict]:
    rows = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        row = json.loads(line)
        query = (row.get("query") or "").strip()
        if query:
            rows.append({"query": query, "kind": row.get("kind") or "unknown"})
    return rows


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", default="backend/search-service/scripts/generated-queries.jsonl")
    parser.add_argument("--out", required=True)
    parser.add_argument("--count", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv)

    corpus_path = pathlib.Path(args.corpus)
    if not corpus_path.exists():
        print(f"Query corpus not found: {corpus_path}", file=sys.stderr)
        return 2

    rows = load_corpus(corpus_path)
    unique: dict[str, dict] = {}
    for row in rows:
        unique.setdefault(row["query"].casefold(), row)
    pool = sorted(unique.values(), key=lambda row: row["query"])
    if not pool:
        print("Query corpus contained no usable queries", file=sys.stderr)
        return 2

    rng = random.Random(args.seed)
    rng.shuffle(pool)
    selected = pool[: args.count]

    payload = {
        "schema_version": 1,
        "seed": args.seed,
        "requested": args.count,
        "selected": len(selected),
        "unique_available": len(pool),
        "corpus": str(corpus_path),
        "corpus_sha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
        "provenance": "catalog-derived synthetic queries; not shopper traffic",
        "kinds": sorted({row["kind"] for row in selected}),
        "queries": [row["query"] for row in selected],
    }
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2) + "\n")

    if len(selected) < args.count:
        print(f"Warning: only {len(selected)} unique queries available, asked for {args.count}", file=sys.stderr)
    print(f"Wrote {len(selected)} unique queries to {out} (seed {args.seed})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
