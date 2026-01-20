// src/api/recommendations.js
const BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export async function getTrendingProducts(limit = 8) {
  const res = await fetch(
    `${BASE_URL}/api/v1/recommendations/trending?limit=${limit}`
  );

  if (!res.ok) {
    throw new Error(`Failed to fetch trending products: ${res.status}`);
  }

  // Backend shape: { items: [ { productId, productName, totalQuantity, totalRevenue }, ... ] }
  const data = await res.json();
  return data.items || [];
}
