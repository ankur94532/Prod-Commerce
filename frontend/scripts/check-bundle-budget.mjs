import { gzipSync } from "node:zlib";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

export const DEFAULT_BUDGET = { rawBytes: 350 * 1024, gzipBytes: 110 * 1024 };

export function measureJavaScript(directory) {
  const files = readdirSync(directory).filter(name => name.endsWith(".js"));
  return files.reduce((total, name) => {
    const bytes = readFileSync(join(directory, name));
    return { rawBytes: total.rawBytes + bytes.length, gzipBytes: total.gzipBytes + gzipSync(bytes).length };
  }, { rawBytes: 0, gzipBytes: 0 });
}

export function assertWithinBudget(actual, budget = DEFAULT_BUDGET) {
  for (const field of ["rawBytes", "gzipBytes"]) {
    if (actual[field] > budget[field]) {
      throw new Error(`JavaScript ${field} ${actual[field]} exceeds budget ${budget[field]}`);
    }
  }
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const actual = measureJavaScript(new URL("../dist/assets", import.meta.url).pathname);
  assertWithinBudget(actual);
  console.log(`Bundle budget passed: ${actual.rawBytes} raw bytes, ${actual.gzipBytes} gzip bytes`);
}
