// Access tokens are short-lived (15 minutes). Without a refresh path a shopper would be
// signed out mid-checkout, so the client exchanges the refresh token when a call comes
// back 401 -- once, even if several requests fail at the same moment.

export const AUTH_STORAGE_KEY = 'auth';

export function readTokens(storage) {
  try {
    const raw = storage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const accessToken = parsed?.tokens?.accessToken ?? parsed?.accessToken ?? null;
    const refreshToken = parsed?.tokens?.refreshToken ?? parsed?.refreshToken ?? null;
    if (!accessToken && !refreshToken) return null;
    return { accessToken, refreshToken };
  } catch {
    return null;
  }
}

export function writeTokens(storage, tokens) {
  // Only the two tokens are persisted: never a profile, never payment details.
  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
    accessToken: tokens.accessToken ?? null,
    refreshToken: tokens.refreshToken ?? null,
  }));
}

export function clearTokens(storage) {
  storage.removeItem(AUTH_STORAGE_KEY);
}

/**
 * @param requestRefresh async (refreshToken) => { accessToken, refreshToken }
 * @param onSessionLost  called once when the session cannot be recovered
 */
export function createSession({ storage, requestRefresh, onSessionLost = () => {} }) {
  let inFlight = null;

  function lose() {
    clearTokens(storage);
    onSessionLost();
  }

  async function refresh() {
    const current = readTokens(storage);
    if (!current?.refreshToken) {
      lose();
      throw new Error('No refresh token available');
    }
    if (!inFlight) {
      inFlight = (async () => {
        try {
          const next = await requestRefresh(current.refreshToken);
          const accessToken = next?.accessToken ?? null;
          if (!accessToken) throw new Error('Refresh returned no access token');
          // The server may rotate the refresh token; keep the old one when it does not.
          writeTokens(storage, {
            accessToken,
            refreshToken: next?.refreshToken ?? current.refreshToken,
          });
          return accessToken;
        } catch (error) {
          lose();
          throw error;
        } finally {
          inFlight = null;
        }
      })();
    }
    return inFlight;
  }

  return {
    refresh,
    getAccessToken: () => readTokens(storage)?.accessToken ?? null,
    isRefreshing: () => inFlight !== null,
  };
}
