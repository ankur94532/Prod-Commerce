// src/api/search.js
import axios from "axios";

const searchApi = axios.create({
  baseURL: "http://localhost:8080/api/v1",
});

// Calls GET /api/v1/search?q=...
export async function searchProducts({ q, category, page = 0, size = 20 }) {
  const params = { q, page, size };
  if (category) params.category = category;

  const res = await searchApi.get("/search", { params });
  // search-service returns plain { items, total } (no .data wrapper)
  return res.data;
}
