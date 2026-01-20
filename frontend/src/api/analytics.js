// src/api/analytics.js

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

function getAccessToken() {
  try {
    const raw = localStorage.getItem("auth");
    if (!raw) return null;

    const parsed = JSON.parse(raw);

    // Our AuthContext stores: { user, tokens: { accessToken, refreshToken } }
    if (parsed.tokens?.accessToken) {
      return parsed.tokens.accessToken;
    }

    // Fallback if you ever stored a flat accessToken before
    if (parsed.accessToken) {
      return parsed.accessToken;
    }

    return null;
  } catch (e) {
    console.error("Failed to read auth from localStorage", e);
    return null;
  }
}

export async function getAnalyticsSummary() {
  const token = getAccessToken();
  if (!token) {
    throw new Error("Not authenticated – no access token");
  }

  const res = await fetch(`${BASE_URL}/api/v1/analytics/summary`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    // credentials is optional here; keep it if you want
    credentials: "include",
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(
      `Failed to fetch analytics summary: ${res.status} ${res.statusText} - ${text}`
    );
  }

  return res.json(); // { totalOrders, totalRevenue }
}
