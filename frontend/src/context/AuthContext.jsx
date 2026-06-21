// src/context/AuthContext.jsx
import React, { createContext, useContext, useEffect, useState } from "react";
import { fetchCurrentUser } from "../api/auth";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [tokens, setTokens] = useState(null); // { accessToken, refreshToken }
  const [loading, setLoading] = useState(true);

  // on mount, try to restore tokens
  useEffect(() => {
    const stored = localStorage.getItem("auth");
    if (!stored) {
      setLoading(false);
      return;
    }

    try {
      const parsed = JSON.parse(stored);

      setTokens(parsed);

      const accessToken = parsed?.accessToken;
      if (!accessToken) {
        console.warn("No accessToken in stored auth, clearing it");
        setTokens(null);
        localStorage.removeItem("auth");
        setLoading(false);
        return;
      }

      // now actually call /me with the token
      fetchCurrentUser(accessToken)
        .then((u) => setUser(u))
        .catch((err) => {
          console.error("Failed to fetch current user:", err);
          setUser(null);
          setTokens(null);
          localStorage.removeItem("auth");
        })
        .finally(() => setLoading(false));
    } catch (e) {
      console.error("Failed to parse stored auth tokens:", e);
      setTokens(null);
      localStorage.removeItem("auth");
      setLoading(false);
    }
  }, []);

  const login = ({ user, tokens }) => {
    setUser(user);
    setTokens(tokens);
    // tokens should be { accessToken, refreshToken }
    localStorage.setItem("auth", JSON.stringify(tokens));
  };

  const logout = () => {
    setUser(null);
    setTokens(null);
    localStorage.removeItem("auth");
  };

  const value = { user, tokens, loading, login, logout };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
