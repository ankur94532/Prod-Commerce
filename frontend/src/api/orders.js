// src/api/orders.js
import axios from "axios";

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const ordersApi = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
});

// Reuse same pattern as cart for JWT
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

// Create an order from current cart snapshot
// POST /api/v1/orders
export async function createOrder({ userId, items }) {
  const safeItems = Array.isArray(items) ? items : [];

  const payload = {
    userId,
    items: safeItems.map((item) => ({
      productId: item.productId, // matches what order-service expects
      productName: item.name,
      quantity: item.quantity,
      unitPrice: item.price,
    })),
  };

  const res = await ordersApi.post("/orders", payload, {
    headers: getAuthHeaders(),
  });

  return res.data; // OrderResponse from backend
}

// Fetch all orders for a user
// GET /api/v1/orders?userId=...
export async function fetchOrdersForUser(userId) {
  const res = await ordersApi.get("/orders", {
    params: { userId },
    headers: getAuthHeaders(),
  });

  return res.data; // list<OrderResponse>
}
