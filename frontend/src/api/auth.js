// src/api/auth.js
import axios from "axios";

const authApi = axios.create({
  baseURL: "http://localhost:8080/api/v1/auth", 
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
  //console.log("Fetching current user with token:", token);
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
