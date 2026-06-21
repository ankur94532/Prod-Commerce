// src/api/client.js
import axios from "axios";
import { API_V1_BASE_URL, getAuthHeaders } from "./apiBase";

const apiClient = axios.create({
  baseURL: API_V1_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  config.headers = {
    ...(config.headers || {}),
    ...getAuthHeaders(),
  };
  return config;
});

export default apiClient;
export { apiClient };
