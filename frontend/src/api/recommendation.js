// src/api/recommendations.js
import { API_BASE_URL } from "./apiBase";

export async function getTrendingProducts(limit = 8) {
  const res = await fetch(
    `${API_BASE_URL}/api/v1/recommendations/trending?limit=${limit}`
  );

  if (!res.ok) {
    throw new Error(`Failed to fetch trending products: ${res.status}`);
  }

  const data = await res.json();
  return data.items || [];
}
