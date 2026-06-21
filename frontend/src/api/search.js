// src/api/search.js
import axios from "axios";
import { API_V1_BASE_URL } from "./apiBase";

const searchApi = axios.create({
  baseURL: API_V1_BASE_URL,
});

export async function searchProducts({
  q,
  category,
  brand,
  minPrice,
  maxPrice,
  inStock,
  color,
  type,
  fit,
  storage,
  memory,
  material,
  sort,
  page = 0,
  size = 20,
}) {
  const params = { q, page, size };
  if (category) params.category = category;
  if (brand) params.brand = brand;
  if (minPrice !== undefined && minPrice !== "") params.minPrice = minPrice;
  if (maxPrice !== undefined && maxPrice !== "") params.maxPrice = maxPrice;
  if (inStock) params.inStock = true;
  if (color) params.color = color;
  if (type) params.type = type;
  if (fit) params.fit = fit;
  if (storage) params.storage = storage;
  if (memory) params.memory = memory;
  if (material) params.material = material;
  if (sort) params.sort = sort;

  const res = await searchApi.get("/search", { params });
  return res.data;
}
