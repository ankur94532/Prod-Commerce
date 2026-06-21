// src/api/orders.js
import axios from "axios";
import { API_V1_BASE_URL, getAuthHeaders } from "./apiBase";

const ordersApi = axios.create({
  baseURL: API_V1_BASE_URL,
});

export async function createOrder({ userId, items, payment, idempotencyKey }) {
  const safeItems = Array.isArray(items) ? items : [];

  const payload = {
    userId,
    items: safeItems.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
    })),
    payment: payment
      ? {
          cardNumber: payment.cardNumber,
          cardExpiry: payment.cardExpiry,
          cardCvc: payment.cardCvc,
        }
      : null,
  };

  const headers = getAuthHeaders();
  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }

  const res = await ordersApi.post("/orders", payload, { headers });
  return res.data;
}

export async function fetchOrdersForUser(userId) {
  const res = await ordersApi.get("/orders", {
    params: { userId },
    headers: getAuthHeaders(),
  });

  return res.data;
}
