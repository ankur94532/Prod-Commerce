// src/api/client.js
import axios from "axios";
import { API_V1_BASE_URL, getAuthHeaders } from "./apiBase";
import { createSession } from "../auth/session";

const apiClient = axios.create({
  baseURL: API_V1_BASE_URL,
});

// A bare client: the refresh call must not go through the interceptor below.
const refreshClient = axios.create({ baseURL: API_V1_BASE_URL });

export const session = createSession({
  storage: window.localStorage,
  requestRefresh: async (refreshToken) => {
    const res = await refreshClient.post("/auth/refresh", { refreshToken });
    return res.data?.data?.tokens ?? {};
  },
  onSessionLost: () => {
    window.dispatchEvent(new CustomEvent("auth:session-lost"));
  },
});

apiClient.interceptors.request.use((config) => {
  config.headers = {
    ...(config.headers || {}),
    ...getAuthHeaders(),
  };
  return config;
});

// A 401 means the short-lived access token expired. Refresh once and replay the request;
// a second 401, or a failed refresh, is a real sign-out.
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    if (error.response?.status !== 401 || !original || original.__refreshRetried) {
      return Promise.reject(error);
    }
    original.__refreshRetried = true;
    try {
      const accessToken = await session.refresh();
      original.headers = {
        ...(original.headers || {}),
        Authorization: `Bearer ${accessToken}`,
      };
      return apiClient(original);
    } catch {
      return Promise.reject(error);
    }
  }
);

export default apiClient;
export { apiClient };
