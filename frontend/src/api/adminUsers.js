// src/api/adminUsers.js
import apiClient from "./client";

/**
 * List users for admin (paginated)
 */
export async function listAdminUsers({ page = 0, size = 20 } = {}) {
  const res = await apiClient.get("/admin/users", {
    params: { page, size },
  });
  // backend: { items, page, size, totalElements, totalPages }
  return res.data;
}

/**
 * Update a user's profile / role
 */
export async function updateAdminUser(id, payload) {
  const res = await apiClient.put(`/admin/users/${id}`, payload);
  // backend: { item: {...} }
  return res.data.item;
}

/**
 * Delete a user (hard delete)
 */
export async function deleteAdminUser(id) {
  await apiClient.delete(`/admin/users/${id}`);
}
