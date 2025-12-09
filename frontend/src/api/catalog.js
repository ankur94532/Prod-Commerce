// src/api/catalog.js
import axios from "axios";

const catalogApi = axios.create({
  baseURL: "http://localhost:8082/api/v1",
});

export async function fetchProducts({ page = 0, size = 12, category } = {}) {
  const params = { page, size };
  if (category) params.category = category;

  const res = await catalogApi.get("/products", { params });
  return res.data; // { data, page, size, totalElements, totalPages }
}

export async function fetchProductBySlug(slug) {
  const res = await catalogApi.get(`/products/${slug}`);
  return res.data.data; // product
}
