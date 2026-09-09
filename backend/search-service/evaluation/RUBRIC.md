# Grading rubric `shopper-graded-v1`

A judgment answers one question: **if a shopper typed this query, how well does this
product serve the need behind it?** Judge the product as described in the frozen catalog
snapshot shown to you. Do not judge the ranker, the wording of the query, or the catalog.

Reviewers see a shuffled pool of query/product pairs with no rank, no score, no retrieval
mode, and no split label. Retrieval-derived fields (score, rank, embeddings, highlights)
are stripped from the product record before it reaches you. This is deliberate: a reviewer
who can see the ranking is voting on the ranker.

## Grades

| Grade | Meaning | Test |
| --- | --- | --- |
| 3 | Ideal | Directly satisfies the need; a shopper would reasonably buy this. Nothing important about the request is unmet. |
| 2 | Relevant | Satisfies the need with a caveat a shopper would accept — a weaker variant, a different brand or style, an imperfect but usable match. |
| 1 | Marginal | Same general area, does not serve the stated need. An accessory, a component, an adjacent category, or a product that meets only part of a compound request. |
| 0 | Irrelevant | Does not serve the need, or violates a hard constraint. |

Binary relevance (used by precision, recall, and MRR) is **grade ≥ 2**. Gain for NDCG is
`2^grade - 1`, so a 3 is worth 7 and a 2 is worth 3: the metric strongly prefers ideal
results over merely acceptable ones.

## Hard constraints

A query may state constraints as structured filters (`minPrice`, `maxPrice`, `inStock`,
`category`, `brand`, `color`, `type`, `fit`, `storage`, `memory`, `material`). These are
not preferences. A product that violates any of them **must be graded 0**, however good it
otherwise looks. The harness enforces this against the frozen catalog and refuses to score
a judgment set that breaks the rule.

Constraints stated only in the query text (for example "under 2000") are judged, not
filtered: apply them with the same severity, but they are your call, not the harness's.

## Missing evidence and compatibility

- If the snapshot does not say whether the product meets the need, grade what is shown.
  Do not assume an unstated feature exists. Absent evidence for a required attribute caps
  the grade at 1.
- If the need requires compatibility (a case for a specific device, a cable for a specific
  port) and the snapshot does not establish it, the ceiling is 1.
- Never open the live catalog, the product page, or a search engine to resolve a doubt.
  Judgments must be reproducible from the frozen snapshot alone.

## No-match queries

Some queries have no good answer in this catalog. That is a valid, intentional case. Grade
every pooled candidate on its own merits — usually 1 or 0. Do **not** inflate the best of a
bad set to 2 to "give the query an answer". A query whose entire pool is 0 reports a null
NDCG rather than a zero, and the count of scorable queries is published alongside the mean.

## Required metadata

Every judgment row carries `grade`, `assessor_id`, `assessor_type`
(`human` | `ai` | `synthetic_fixture`), and a non-empty `rationale`. A blank grade is not a
zero — the harness refuses an incomplete pool rather than filling gaps. `assessor_type` is
a declaration, not an authenticated fact, and the report says so.

## Known limits of any number this produces

- **Pool bias.** Only products returned by one of the evaluated modes are judged. Recall is
  bounded by the pool, not the catalog, and a mode cannot be credited for a good product no
  mode retrieved. Adding a mode later changes the pool and invalidates comparison with
  earlier reports.
- **Ideal set.** NDCG's ideal ranking is drawn from judged pooled products, not the full
  catalog, so absolute NDCG is optimistic relative to a true full-catalog ideal.
- **No positive gain.** Queries whose judged pool contains no grade ≥ 2 report null NDCG
  and null recall and are excluded from those means, with `ndcg_scorable_queries` reported.
- **Held-out discipline.** Tune on `dev`. Report on `test`. Query families keep their
  variants on one side of that split so a paraphrase cannot leak.
- **Small sets are exploratory.** The shipped query set is tiny. Paired differences use a
  query-family bootstrap; confidence intervals on a handful of families are wide and must
  be reported with the interval, never as a bare mean.
- **Synthetic corpus.** The catalog is generated, not a real assortment. Relevance measured
  over it does not transfer to a real one.
- **Provenance.** If the assessor is `ai`, the report's evidence class says
  `ai_judged_pooled_evaluation`. That is an AI-labeled benchmark and must be described as
  one — never as human relevance judgments or as shopper behavior.
