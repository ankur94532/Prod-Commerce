# Consumer-driven HTTP contracts

These JSON files are synthetic protocol fixtures, not production samples or relevance
labels. Each boundary is checked from both sides:

- the consumer's real HTTP client must send or parse the fixture; and
- the provider's real web controller must accept or produce the same fixture.

Run all four boundaries with `ops/testing/contracts.sh`. A field rename, type change, or
missing field therefore fails before independently deployed services discover the drift at
runtime. These are compatibility checks, not a substitute for end-to-end or load testing.
