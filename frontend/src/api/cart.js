// src/api/cart.js
import axios from "axios";
import { API_V1_BASE_URL, getAuthHeaders } from "./apiBase";

const cartApi = axios.create({
  baseURL: API_V1_BASE_URL,
});

export async function getCart(userId) {
  const res = await cartApi.get(`/cart/${userId}`, {
    headers: getAuthHeaders(),
  });

  const payload = res.data;
  return payload && typeof payload === "object" && "data" in payload
    ? payload.data
    : payload;
}

export async function clearCart(userId, revision) {
  if (!Number.isSafeInteger(revision) || revision < 0) {
    throw new Error("Cannot clear a cart without its revision.");
  }
  const res = await cartApi.delete(`/cart/${userId}`, {
    headers: { ...getAuthHeaders(), "If-Match": `"${revision}"` },
  });

  if (res.status !== 200 && res.status !== 204) {
    throw new Error(`Failed to clear cart: ${res.status}`);
  }
}

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
    headers: {
      ...getAuthHeaders(),
      "Idempotency-Key": globalThis.crypto?.randomUUID?.()
        || `cart-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    },
  });

  const payload = res.data;
  return payload && typeof payload === "object" && "data" in payload
    ? payload.data
    : payload;
}
