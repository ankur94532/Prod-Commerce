// The attempt identity contains only user/cart data. Never persist payment credentials.
export function cartFingerprint(items) {
  return JSON.stringify(items.map(({ productId, quantity }) => [String(productId), quantity])
    .sort(([a], [b]) => a.localeCompare(b)));
}

export async function submitCheckout({ userId, items, cartRevision, payment, storage, createOrder, clearCart, uuid }) {
  const slot = `checkout-attempt:${userId}`;
  const fingerprint = cartFingerprint(items);
  const saved = storage.getItem(slot);
  let attempt = saved ? JSON.parse(saved) : null;
  if (attempt && attempt.fingerprint !== fingerprint) {
    throw new Error('Your previous checkout must be resolved first. Restore that cart or check My Orders.');
  }
  if (!attempt) {
    attempt = { key: uuid(), fingerprint, cartRevision };
    // Fail before sending if durable browser storage is unavailable.
    storage.setItem(slot, JSON.stringify(attempt));
  }
  const response = attempt.order || await createOrder({ userId, items, payment, idempotencyKey: attempt.key });
  const order = { ...response, attemptKey: attempt.key };
  if (order.status === 'CANCELLED') {
    storage.removeItem(slot);
    return { order, warning: 'Order cancelled. Stock reservations have been released. You can try again.' };
  }
  if (order.status !== 'PAID') {
    return { order, warning: 'Checkout is being recovered. Retry to check this same order, or visit My Orders.' };
  }
  // From here on, cart/storage failures must never turn a paid order into a checkout failure.
  try { storage.setItem(slot, JSON.stringify({ ...attempt, order })); } catch { /* Original key still replays safely. */ }
  try {
    await clearCart(userId, attempt.cartRevision);
  } catch {
    return { order, warning: 'Order placed. Your cart could not be cleared; retry checkout to clear it without placing another order.' };
  }
  // Keep a paid receipt until the user explicitly starts a new purchase.
  return { order, warning: '' };
}

export async function inspectAttempt(userId, storage, fetchAttempt) {
  const saved = storage.getItem(`checkout-attempt:${userId}`);
  if (!saved) return null;
  const attempt = JSON.parse(saved);
  if (attempt.order) return { ...attempt.order, attemptKey: attempt.key };
  try { return { ...await fetchAttempt(attempt.key), attemptKey: attempt.key }; }
  catch (error) {
    if (error?.response?.status === 404) return null;
    throw error;
  }
}

export function startNewAttempt(userId, storage, previousOrder) {
  if (!['PAID', 'CANCELLED'].includes(previousOrder?.status)) {
    throw new Error('Previous checkout is not yet resolved.');
  }
  const slot = `checkout-attempt:${userId}`;
  const saved = storage.getItem(slot);
  if (!saved) return;
  if (JSON.parse(saved).key !== previousOrder.attemptKey) {
    throw new Error('Checkout changed in another tab. Refresh its status.');
  }
  storage.removeItem(slot);
}

export async function cleanupPaidCart({ userId, items, storage, previousOrder, clearCart }) {
  const saved = storage.getItem(`checkout-attempt:${userId}`);
  const attempt = saved ? JSON.parse(saved) : null;
  if (previousOrder?.status !== 'PAID' || !attempt || attempt.key !== previousOrder.attemptKey
      || attempt.fingerprint !== cartFingerprint(items)) {
    throw new Error('Cart or checkout changed. Review it before clearing.');
  }
  await clearCart(userId, attempt.cartRevision);
}

/** Web Locks when available, with a localStorage lease for older/insecure browsers. */
export async function withCheckoutLock(userId, storage, operation, browser = globalThis.navigator) {
  if (browser?.locks?.request) {
    return browser.locks.request(`checkout:${userId}`, operation);
  }

  const key = `checkout-lock:${userId}`;
  const owner = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) {
    let current;
    try { current = JSON.parse(storage.getItem(key)); } catch { current = null; }
    if (!current || current.expiresAt <= Date.now()) {
      storage.setItem(key, JSON.stringify({ owner, expiresAt: Date.now() + 30000 }));
      await new Promise(resolve => setTimeout(resolve, 40 + Math.floor(Math.random() * 40)));
      let claimed;
      try { claimed = JSON.parse(storage.getItem(key)); } catch { claimed = null; }
      if (claimed?.owner === owner) {
        try { return await operation(); }
        finally {
          let latest;
          try { latest = JSON.parse(storage.getItem(key)); } catch { latest = null; }
          if (latest?.owner === owner) storage.removeItem(key);
        }
      }
    }
    await new Promise(resolve => setTimeout(resolve, 50 + Math.floor(Math.random() * 50)));
  }
  throw new Error('Checkout is already active in another tab. Retry in a moment.');
}
