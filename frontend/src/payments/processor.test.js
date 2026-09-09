import test from 'node:test';
import assert from 'node:assert/strict';
import {
  tokenizeCard, isPaymentToken, isPlausibleCard, isExpiryInFuture, PaymentTokenizationError,
} from './processor.js';

const VALID = { number: '4242 4242 4242 4242', expiry: '12/30', cvc: '123' };
const DECLINING = { number: '4000 0000 0000 0002', expiry: '12/30', cvc: '123' };
const ids = () => 'abcdef0123456789abcdef0123456789';

test('a valid card yields a token and nothing resembling the card', async () => {
  const token = await tokenizeCard(VALID, { randomId: ids });

  assert.ok(isPaymentToken(token), `not a token: ${token}`);
  // The property that keeps this system out of PCI scope: no digit of the card survives.
  assert.ok(!token.includes('4242'));
  assert.ok(!/\d{12,}/.test(token), 'a token must not contain anything card-length');
});

test('the declining test card is distinguishable only by the token the processor issues', async () => {
  const declined = await tokenizeCard(DECLINING, { randomId: ids });
  const accepted = await tokenizeCard(VALID, { randomId: ids });

  assert.ok(declined.startsWith('pm_decline_'));
  assert.ok(accepted.startsWith('pm_ok_'));
  assert.ok(!declined.includes('0002'));
});

test('invalid cards are refused in the browser rather than sent anywhere', async () => {
  await assert.rejects(() => tokenizeCard({ ...VALID, number: '4242424242424241' }),
    PaymentTokenizationError, 'a number failing the Luhn check must be refused');
  await assert.rejects(() => tokenizeCard({ ...VALID, number: '424242' }), PaymentTokenizationError);
  await assert.rejects(() => tokenizeCard({ ...VALID, expiry: '12/20' }), PaymentTokenizationError);
  await assert.rejects(() => tokenizeCard({ ...VALID, expiry: '13/30' }), PaymentTokenizationError);
  await assert.rejects(() => tokenizeCard({ ...VALID, expiry: 'soon' }), PaymentTokenizationError);
  await assert.rejects(() => tokenizeCard({ ...VALID, cvc: '12' }), PaymentTokenizationError);
  await assert.rejects(() => tokenizeCard({ ...VALID, cvc: 'abc' }), PaymentTokenizationError);
});

test('card validation accepts the common test cards and rejects mistyped ones', () => {
  assert.ok(isPlausibleCard('4242424242424242'));
  assert.ok(isPlausibleCard('5555 5555 5555 4444'));
  assert.ok(!isPlausibleCard('1234567812345678'));
  assert.ok(!isPlausibleCard(''));
  assert.ok(!isPlausibleCard(null));
});

test('a card is valid through the last day of its expiry month', () => {
  const during = new Date(2030, 11, 31);
  assert.ok(isExpiryInFuture('12/30', during));
  assert.ok(!isExpiryInFuture('11/30', during));
  assert.ok(isExpiryInFuture('12/2030', during));
});

test('only processor tokens satisfy the token check', () => {
  assert.ok(isPaymentToken('pm_ok_abcdef1234'));
  assert.ok(!isPaymentToken('4242424242424242'));
  assert.ok(!isPaymentToken('tok_something'));
  assert.ok(!isPaymentToken(''));
  assert.ok(!isPaymentToken(null));
});

test('each tokenization is distinct so one token cannot be replayed as another order', async () => {
  let n = 0;
  const unique = () => `id${(n += 1)}`.padEnd(24, '0');
  const first = await tokenizeCard(VALID, { randomId: unique });
  const second = await tokenizeCard(VALID, { randomId: unique });

  assert.notEqual(first, second);
});
