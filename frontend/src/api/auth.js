// src/api/auth.js
import axios from "axios";
import { API_V1_BASE_URL } from "./apiBase";

const authApi = axios.create({
  baseURL: `${API_V1_BASE_URL}/auth`,
});

export async function registerUser(payload) {
  const res = await authApi.post("/register", payload);
  return res.data.data;
}

export async function loginUser(payload) {
  const res = await authApi.post("/login", payload);
  return res.data.data;
}

export async function fetchCurrentUser(token) {
  if (!token) {
    throw new Error("No token provided");
  }
  const res = await authApi.get("/me", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  return res.data.data;
}
