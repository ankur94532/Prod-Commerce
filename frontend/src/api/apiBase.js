// `??` rather than `||`, so an explicitly empty VITE_API_BASE_URL survives.
//
// That is what lets one built image serve any environment. With the gateway behind the same
// ingress host, an empty base makes every call a same-origin relative path -- no CORS, and
// no hostname compiled into the bundle. Vite substitutes this at build time, so `||` would
// have quietly turned the deployed configuration back into localhost:8080 and the browser
// would have called the shopper's own machine.
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
export const API_BASE_URL = configuredApiBaseUrl ?? "http://localhost:8080";
export const API_V1_BASE_URL = `${API_BASE_URL}/api/v1`;

export function getAuthHeaders() {
  try {
    const raw = localStorage.getItem("auth");
    if (!raw) return {};

    const parsed = JSON.parse(raw);
    const token = parsed?.tokens?.accessToken || parsed?.accessToken || null;
    return token ? { Authorization: `Bearer ${token}` } : {};
  } catch {
    return {};
  }
}

export function getAccessToken() {
  const auth = getAuthHeaders();
  return auth.Authorization ? auth.Authorization.replace("Bearer ", "") : null;
}
