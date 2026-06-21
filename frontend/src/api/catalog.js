// src/api/catalog.js
import axios from "axios";
import { API_V1_BASE_URL } from "./apiBase";

const catalogApi = axios.create({
  baseURL: API_V1_BASE_URL,
});

export async function fetchProducts({
  page = 0,
  size = 20,
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
} = {}) {
  const params = { page, size };
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

  const res = await catalogApi.get("/products", { params });
  return res.data;
}

export async function fetchProductBySlug(slug) {
  const res = await catalogApi.get(`/products/${slug}`);
  return res.data.data;
}

export async function fetchCategories() {
  const res = await catalogApi.get("/products/categories");
  return res.data;
}

export async function fetchProductFilters({ category } = {}) {
  const params = {};
  if (category) params.category = category;
  const res = await catalogApi.get("/products/filters", { params });
  return res.data;
}
