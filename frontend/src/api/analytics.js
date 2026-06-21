// src/api/analytics.js
import { API_BASE_URL, getAccessToken } from "./apiBase";

export async function getAnalyticsSummary() {
  const token = getAccessToken();
  if (!token) {
    throw new Error("Not authenticated – no access token");
  }

  const res = await fetch(`${API_BASE_URL}/api/v1/analytics/summary`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(
      `Failed to fetch analytics summary: ${res.status} ${res.statusText} - ${text}`
    );
  }

  return res.json();
}
