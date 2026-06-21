export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
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
