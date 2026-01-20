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
  return res.data; // page of ProductResponse
}

export async function fetchProductBySlug(slug) {
  const res = await catalogApi.get(`/products/${slug}`);
  return res.data.data;
}
