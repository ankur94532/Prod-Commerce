// src/api/client.js
import axios from "axios";

// Use API gateway as the single entry point
const apiClient = axios.create({
  baseURL: "http://localhost:8080/api/v1",
});

// Attach JWT if present
apiClient.interceptors.request.use((config) => {
  try {
    const raw = localStorage.getItem("auth");
    if (raw) {
      const tokens = JSON.parse(raw);
      if (tokens?.accessToken) {
        config.headers.Authorization = `Bearer ${tokens.accessToken}`;
      }
    }
  } catch (e) {
    console.error("Failed to read auth from localStorage", e);
  }
  return config;
});

export default apiClient;
export { apiClient };
