// src/api/catalog.js
import axios from "axios";

const catalogApi = axios.create({
  baseURL: "http://localhost:8080/api/v1",
});

export async function fetchProducts({ page = 0, size = 20, category } = {}) {
  const params = { page, size };
  if (category) params.category = category;

  const res = await catalogApi.get("/products", { params });
  console.log("Fetched products:", res.data);
  // res.data is expected to be the page object:
  // { data: [...], page, size, totalPages, totalElements }
  return res.data;
}

export async function fetchProductBySlug(slug) {
  const res = await catalogApi.get(`/products/${slug}`);
  return res.data.data;
}

// 🔹 NEW: fetch all available categories from backend
export async function fetchCategories() {
  const res = await catalogApi.get("/products/categories");
  console.log(res);
  // expected response: ["smartphones", "earbuds", "refrigerators", ...]
  return res.data;
}
