// Stands in for the payment processor's browser SDK (Stripe.js, Adyen Web, Braintree).
//
// The card number, expiry, and CVC are read here and exchanged for a token. They are never
// sent to this application's API. That is the whole point: the moment card data reaches our
// servers, every service on the request path, every log, every heap dump, and every database
// backup falls inside PCI DSS scope. Keeping the card in the browser and on the processor's
// domain keeps this system out of that scope entirely.
//
// TO USE A REAL PROCESSOR: delete this file and mount the processor's hosted fields, then
// call its tokenization method. The rest of the checkout path does not change, because it
// already deals only in the token this returns.

const TOKEN_PREFIX = 'pm_';

/** Digits only, so formatting spaces or dashes do not change the outcome. */
function digits(value) {
  return String(value || '').replace(/\D/g, '');
}

/** Luhn check, the same validation a processor's field performs before it issues a token. */
export function isPlausibleCard(number) {
  const value = digits(number);
  if (value.length < 12 || value.length > 19) return false;
  let sum = 0;
  let double = false;
  for (let i = value.length - 1; i >= 0; i -= 1) {
    let digit = Number(value[i]);
    if (double) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    double = !double;
  }
  return sum % 10 === 0;
}

export function isExpiryInFuture(expiry, now = new Date()) {
  const match = /^(\d{2})\s*\/\s*(\d{2,4})$/.exec(String(expiry || '').trim());
  if (!match) return false;
  const month = Number(match[1]);
  if (month < 1 || month > 12) return false;
  const year = match[2].length === 2 ? 2000 + Number(match[2]) : Number(match[2]);
  // A card is valid through the last day of its expiry month.
  const endOfMonth = new Date(year, month, 1);
  return endOfMonth > now;
}

export class PaymentTokenizationError extends Error {}

/**
 * Exchanges card details for a token. Rejects rather than returning anything that could be
 * mistaken for a token, so a caller cannot accidentally submit an order without one.
 *
 * @returns {Promise<string>} an opaque token safe to send to our API
 */
export async function tokenizeCard({ number, expiry, cvc }, { randomId } = {}) {
  if (!isPlausibleCard(number)) {
    throw new PaymentTokenizationError('Enter a valid card number.');
  }
  if (!isExpiryInFuture(expiry)) {
    throw new PaymentTokenizationError('Enter a valid expiry date in the future.');
  }
  if (!/^\d{3,4}$/.test(String(cvc || '').trim())) {
    throw new PaymentTokenizationError('Enter the 3 or 4 digit security code.');
  }

  const value = digits(number);
  // The real processor decides acceptance from its own records. This stand-in follows the
  // usual test-card convention: 4000 0000 0000 0002 is the generic decline. The previous
  // rule keyed on numbers ending 0000, which no valid card can do -- it fails the Luhn
  // check -- so a decline was unreachable from the browser.
  const declines = value.endsWith('0002');
  const id = (randomId || (() => crypto.randomUUID().replace(/-/g, '')))();

  // Only the token leaves this function. Nothing here retains the card.
  return `${TOKEN_PREFIX}${declines ? 'decline_' : 'ok_'}${id.slice(0, 24)}`;
}

export function isPaymentToken(value) {
  return typeof value === 'string' && /^pm_[A-Za-z0-9_]{6,64}$/.test(value);
}
