import test from "node:test";
import assert from "node:assert/strict";
import { safeClientEvent } from "./observability.js";

test("client events exclude URL queries and bound error text", () => {
  const event = safeClientEvent("client_error", { message: "x".repeat(500) }, {
    pathname: "/checkout",
    search: "?email=private@example.com",
  });

  assert.equal(event.path, "/checkout");
  assert.equal(event.message.length, 300);
  assert.equal(JSON.stringify(event).includes("private@example.com"), false);
});
