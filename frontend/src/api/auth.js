// src/api/auth.js
import api from "./client";

export async function registerUser({ email, password, fullName }) {
  const res = await api.post("/auth/register", {
    email,
    password,
    fullName,
  });
  return res.data.data; // { user, tokens }
}

export async function loginUser({ email, password }) {
  const res = await api.post("/auth/login", {
    email,
    password,
  });
  return res.data.data; // { user, tokens }
}

export async function fetchMe() {
  const res = await api.get("/auth/me");
  return res.data.data; // user
}
