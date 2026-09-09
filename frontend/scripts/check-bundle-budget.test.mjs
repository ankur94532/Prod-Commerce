import test from "node:test";
import assert from "node:assert/strict";
import { assertWithinBudget } from "./check-bundle-budget.mjs";

test("bundle budget rejects an oversized artifact", () => {
  assert.throws(() => assertWithinBudget(
    { rawBytes: 11, gzipBytes: 5 }, { rawBytes: 10, gzipBytes: 10 }), /exceeds budget/);
});

test("bundle budget accepts artifacts within both limits", () => {
  assert.doesNotThrow(() => assertWithinBudget(
    { rawBytes: 10, gzipBytes: 10 }, { rawBytes: 10, gzipBytes: 10 }));
});
