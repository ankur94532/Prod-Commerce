// src/api/cart.js
import axios from "axios";

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// All cart calls go via API gateway
const cartApi = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
});

// Pull access token from localStorage (what AuthContext stores)
function getAuthHeaders() {
  try {
    const raw = localStorage.getItem("auth");
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    const token =
      parsed?.tokens?.accessToken || parsed?.accessToken || null;
    if (!token) return {};
    return { Authorization: `Bearer ${token}` };
  } catch {
    return {};
  }
}

// GET /api/v1/cart/{userId}
export async function getCart(userId) {
  const res = await cartApi.get(`/cart/${userId}`, {
    headers: getAuthHeaders(),
  });

  const payload = res.data;
  // Be tolerant: backend might return { data: cart } or cart directly
  const cart =
    payload && typeof payload === "object" && "data" in payload
      ? payload.data
      : payload;

  return cart;
}

// DELETE /api/v1/cart/{userId}
export async function clearCart(userId) {
  const res = await cartApi.delete(`/cart/${userId}`, {
    headers: getAuthHeaders(),
  });

  if (res.status !== 200 && res.status !== 204) {
    throw new Error(`Failed to clear cart: ${res.status}`);
  }
}

// POST /api/v1/cart/{userId}/items
export async function addCartItem(
  userId,
  { id, slug, name, price, currency, imageUrls },
  quantity = 1
) {
  const body = {
    productId: id,
    productSlug: slug,
    name,
    price,
    currency,
    quantity,
    imageUrl: imageUrls && imageUrls.length > 0 ? imageUrls[0] : null,
  };

  const res = await cartApi.post(`/cart/${userId}/items`, body, {
    headers: getAuthHeaders(),
  });

  const payload = res.data;
  return payload && typeof payload === "object" && "data" in payload
    ? payload.data
    : payload;
}
