// src/api/adminProducts.js
import apiClient from "./client";

/**
 * List products for admin (paginated)
 */
export async function listAdminProducts({ page = 0, size = 20 } = {}) {
  const res = await apiClient.get("/admin/products", {
    params: { page, size },
  });
  // backend returns: { items, page, size, totalElements, totalPages }
  return res.data;
}

/**
 * Get a single product by id
 */
export async function getAdminProduct(id) {
  const res = await apiClient.get(`/admin/products/${id}`);
  // backend returns: { item: {...} }
  return res.data.item;
}

/**
 * Create a new product
 */
export async function createAdminProduct(payload) {
  const res = await apiClient.post("/admin/products", payload);
  // backend returns: { item: {...} }
  return res.data.item;
}

/**
 * Update an existing product
 */
export async function updateAdminProduct(id, payload) {
  const res = await apiClient.put(`/admin/products/${id}`, payload);
  // backend returns: { item: {...} }
  return res.data.item;
}

/**
 * DELETE a product (hard delete)
 */
export async function deleteAdminProduct(id) {
  await apiClient.delete(`/admin/products/${id}`);
}
