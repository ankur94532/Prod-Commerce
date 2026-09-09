import test from 'node:test';
import assert from 'node:assert/strict';
import { cartFingerprint, submitCheckout, startNewAttempt, inspectAttempt, cleanupPaidCart, withCheckoutLock } from './attempt.js';

function fixture() {
  const records = new Map();
  const calls = [];
  let sequence = 0;
  const args = {
    userId: 'u', items: [{ productId: '1', quantity: 2 }], cartRevision: 3,
    payment: { cardNumber: 'secret-card', cardCvc: 'secret-cvc' },
    storage: { getItem: k => records.get(k) || null, setItem: (k, v) => records.set(k, v), removeItem: k => records.delete(k) },
    uuid: () => `key-${++sequence}`,
    createOrder: async req => { calls.push(req); return { id: 1, status: 'PAID' }; },
    clearCart: async () => {},
  };
  return { args, records, calls };
}

test('lost response and reload retry the persisted key without payment data in storage', async () => {
  const { args, records, calls } = fixture();
  const successful = args.createOrder;
  args.createOrder = async req => { calls.push(req); throw new Error('response lost'); };
  await assert.rejects(submitCheckout(args));
  args.createOrder = successful;
  await submitCheckout({ ...args });
  assert.equal(calls.length, 2);
  assert.equal(calls[0].idempotencyKey, calls[1].idempotencyKey);
  assert.ok(!JSON.stringify([...records.values()]).includes('secret'));
});

test('cart cleanup failure retains paid receipt and does not resubmit order on retry', async () => {
  const { args, calls } = fixture();
  args.clearCart = async () => { throw new Error('cart down'); };
  const result = await submitCheckout(args);
  assert.equal(result.order.status, 'PAID');
  assert.match(result.warning, /Order placed/);
  args.clearCart = async () => {};
  await submitCheckout(args);
  assert.equal(calls.length, 1);
});

test('pending and compensating responses keep same key; cancellation permits a new attempt', async () => {
  const { args, calls } = fixture();
  for (const status of ['PENDING_PAYMENT', 'COMPENSATING', 'CANCELLED', 'PAID']) {
    args.createOrder = async req => { calls.push(req); return { id: 1, status }; };
    await submitCheckout(args);
  }
  assert.equal(calls[0].idempotencyKey, calls[2].idempotencyKey);
  assert.notEqual(calls[2].idempotencyKey, calls[3].idempotencyKey);
});

test('a changed cart cannot silently abandon an unresolved attempt', async () => {
  const { args, calls } = fixture();
  args.createOrder = async req => { calls.push(req); throw new Error('timeout'); };
  await assert.rejects(submitCheckout(args));
  args.items = [{ productId: '1', quantity: 3 }];
  await assert.rejects(submitCheckout(args), /previous checkout/);
  assert.equal(calls.length, 1);
});

test('unavailable storage fails before ordering; paid persistence failure does not undo success', async () => {
  const { args, calls } = fixture();
  const set = args.storage.setItem;
  args.storage.setItem = () => { throw new Error('storage denied'); };
  await assert.rejects(submitCheckout(args));
  assert.equal(calls.length, 0);
  let writes = 0;
  args.storage.setItem = (k, v) => { if (++writes > 1) throw new Error('full'); set(k, v); };
  assert.equal((await submitCheckout(args)).order.status, 'PAID');
});

test('receipt survives reloads until a new purchase is explicitly started', async () => {
  const { args, calls } = fixture();
  await submitCheckout(args);
  await submitCheckout(args);
  assert.equal(calls.length, 1);
  startNewAttempt(args.userId, args.storage, { id: 1, status: "PAID", attemptKey: calls[0].idempotencyKey });
  await submitCheckout(args);
  assert.notEqual(calls[0].idempotencyKey, calls[1].idempotencyKey);
});

test('fingerprint ignores display prices and line order', () => {
  assert.equal(cartFingerprint([{productId: 'a', quantity: 1}, {productId: 'b', quantity: 2}]),
    cartFingerprint([{productId: 'b', quantity: 2, price: 9}, {productId: 'a', quantity: 1}]));
});

test('status lookup resolves changed carts without resending payment', async () => {
  const { args } = fixture();
  args.createOrder = async () => { throw new Error('lost response'); };
  await assert.rejects(submitCheckout(args));
  const order = await inspectAttempt(args.userId, args.storage, async key => {
    assert.equal(key, 'key-1');
    return { id: 1, status: 'CANCELLED' };
  });
  startNewAttempt(args.userId, args.storage, order);
  assert.equal(await inspectAttempt(args.userId, args.storage, () => assert.fail()), null);
  assert.throws(() => startNewAttempt(args.userId, args.storage, { status: 'COMPENSATING' }));
});

test('a stale tab cannot discard an attempt started in another tab', async () => {
  const { args } = fixture();
  const old = (await submitCheckout(args)).order;
  startNewAttempt(args.userId, args.storage, old);
  await submitCheckout(args);
  assert.throws(() => startNewAttempt(args.userId, args.storage, old), /another tab/);
});

test('cleanup cannot clear a changed cart or act on an attempt replaced in another tab', async () => {
  const { args } = fixture();
  const previousOrder = (await submitCheckout(args)).order;
  let cleared = 0;
  const cleanup = { ...args, previousOrder, clearCart: async () => { cleared++; } };
  await assert.rejects(cleanupPaidCart({ ...cleanup, items: [{ productId: 'different', quantity: 1 }] }));
  await cleanupPaidCart(cleanup);
  startNewAttempt(args.userId, args.storage, previousOrder);
  await assert.rejects(cleanupPaidCart(cleanup));
  assert.equal(cleared, 1);
});

test('cart cleanup uses the revision captured before payment', async () => {
  const { args } = fixture();
  let clearArguments;
  args.clearCart = async (...values) => { clearArguments = values; };

  await submitCheckout(args);

  assert.deepEqual(clearArguments, ['u', 3]);
});

test('checkout lock falls back when Web Locks are unavailable and releases its lease', async () => {
  const records = new Map();
  const storage = {
    getItem: key => records.get(key) || null,
    setItem: (key, value) => records.set(key, value),
    removeItem: key => records.delete(key),
  };

  const result = await withCheckoutLock('u', storage, async () => 'done', {});

  assert.equal(result, 'done');
  assert.equal(records.has('checkout-lock:u'), false);
});

test('checkout lock prefers native Web Locks when present', async () => {
  let requested;
  const browser = { locks: { request: async (key, operation) => { requested = key; return operation(); } } };

  assert.equal(await withCheckoutLock('u', {}, async () => 'native', browser), 'native');
  assert.equal(requested, 'checkout:u');
});
