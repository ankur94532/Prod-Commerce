import test from 'node:test';
import assert from 'node:assert/strict';
import { createSession, readTokens, writeTokens, clearTokens, AUTH_STORAGE_KEY } from './session.js';

function memoryStorage(initial) {
  const records = new Map();
  if (initial !== undefined) records.set(AUTH_STORAGE_KEY, initial);
  return {
    records,
    getItem: key => (records.has(key) ? records.get(key) : null),
    setItem: (key, value) => records.set(key, value),
    removeItem: key => records.delete(key),
  };
}

function stored(storage) {
  const raw = storage.getItem(AUTH_STORAGE_KEY);
  return raw === null ? null : JSON.parse(raw);
}

test('reads both the flat and nested token shapes the app has written', () => {
  assert.deepEqual(
    readTokens(memoryStorage(JSON.stringify({ accessToken: 'a', refreshToken: 'r' }))),
    { accessToken: 'a', refreshToken: 'r' });
  assert.deepEqual(
    readTokens(memoryStorage(JSON.stringify({ tokens: { accessToken: 'a', refreshToken: 'r' } }))),
    { accessToken: 'a', refreshToken: 'r' });
});

test('absent, malformed, or empty stored auth reads as no session rather than throwing', () => {
  assert.equal(readTokens(memoryStorage()), null);
  assert.equal(readTokens(memoryStorage('not json')), null);
  assert.equal(readTokens(memoryStorage(JSON.stringify({ user: 'someone' }))), null);
  assert.equal(readTokens({ getItem: () => { throw new Error('blocked'); } }), null);
});

test('only the two tokens are persisted', () => {
  const storage = memoryStorage();
  writeTokens(storage, { accessToken: 'a', refreshToken: 'r', user: { email: 'x@example.com' } });
  assert.deepEqual(stored(storage), { accessToken: 'a', refreshToken: 'r' });
});

test('a successful refresh stores the new pair and returns the access token', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  const session = createSession({
    storage,
    requestRefresh: async token => {
      assert.equal(token, 'r1');
      return { accessToken: 'new', refreshToken: 'r2' };
    },
  });

  assert.equal(await session.refresh(), 'new');
  assert.deepEqual(stored(storage), { accessToken: 'new', refreshToken: 'r2' });
  assert.equal(session.getAccessToken(), 'new');
});

test('a server that does not rotate the refresh token keeps the existing one', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  const session = createSession({ storage, requestRefresh: async () => ({ accessToken: 'new' }) });

  await session.refresh();

  assert.deepEqual(stored(storage), { accessToken: 'new', refreshToken: 'r1' });
});

test('concurrent expiries trigger exactly one refresh request', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  let calls = 0;
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const session = createSession({
    storage,
    requestRefresh: async () => { calls += 1; await gate; return { accessToken: 'new', refreshToken: 'r2' }; },
  });

  const pending = [session.refresh(), session.refresh(), session.refresh()];
  assert.equal(session.isRefreshing(), true);
  release();
  const results = await Promise.all(pending);

  assert.equal(calls, 1);
  assert.deepEqual(results, ['new', 'new', 'new']);
});

test('a failed refresh clears the session once and reports it', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  let lost = 0;
  const session = createSession({
    storage,
    requestRefresh: async () => { throw new Error('refresh rejected'); },
    onSessionLost: () => { lost += 1; },
  });

  await assert.rejects(session.refresh(), /refresh rejected/);

  assert.equal(lost, 1);
  assert.equal(stored(storage), null);
});

test('a refresh response without an access token is treated as a failure', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  const session = createSession({ storage, requestRefresh: async () => ({ refreshToken: 'r2' }) });

  await assert.rejects(session.refresh(), /no access token/);
  assert.equal(stored(storage), null);
});

test('no stored refresh token reports session loss without calling the server', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old' }));
  let calls = 0;
  let lost = 0;
  const session = createSession({
    storage,
    requestRefresh: async () => { calls += 1; return { accessToken: 'new' }; },
    onSessionLost: () => { lost += 1; },
  });

  await assert.rejects(session.refresh(), /No refresh token/);

  assert.equal(calls, 0);
  assert.equal(lost, 1);
  assert.equal(stored(storage), null);
});

test('the single-flight latch resets so a later sign-in can refresh again', async () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'old', refreshToken: 'r1' }));
  let succeed = false;
  const session = createSession({
    storage,
    requestRefresh: async () => {
      if (!succeed) throw new Error('refresh rejected');
      return { accessToken: 'new', refreshToken: 'r2' };
    },
  });

  await assert.rejects(session.refresh());
  assert.equal(session.isRefreshing(), false);

  succeed = true;
  writeTokens(storage, { accessToken: 'old', refreshToken: 'r3' });
  assert.equal(await session.refresh(), 'new');
});

test('clearing removes the stored session', () => {
  const storage = memoryStorage(JSON.stringify({ accessToken: 'a', refreshToken: 'r' }));
  clearTokens(storage);
  assert.equal(stored(storage), null);
});
