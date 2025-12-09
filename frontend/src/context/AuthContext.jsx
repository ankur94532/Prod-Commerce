import React, { createContext, useContext, useEffect, useState } from "react";
import { fetchMe } from "../api/auth";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [tokens, setTokens] = useState(null);
  const [loading, setLoading] = useState(true);

  // on mount, try to restore tokens
  useEffect(() => {
    const stored = localStorage.getItem("auth");
    if (stored) {
      const parsed = JSON.parse(stored);
      setTokens(parsed);
      // try loading current user
      fetchMe()
        .then((u) => setUser(u))
        .catch(() => {
          setUser(null);
          setTokens(null);
          localStorage.removeItem("auth");
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = ({ user, tokens }) => {
    setUser(user);
    setTokens(tokens);
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
