// src/api/client.js
import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8081/api/v1",
});

api.interceptors.request.use((config) => {
  const tokens = JSON.parse(localStorage.getItem("auth") || "null");
  if (tokens?.accessToken) {
    config.headers.Authorization = `Bearer ${tokens.accessToken}`;
  }
  return config;
});

export default api;
