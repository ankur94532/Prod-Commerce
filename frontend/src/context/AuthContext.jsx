// src/context/AuthContext.jsx
import React, { useEffect, useState } from "react";
import { fetchCurrentUser } from "../api/auth";
import { clearTokens, readTokens, writeTokens } from "../auth/session";
import { AuthContext } from "./authContextValue";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // Restored once, during the first render, so the effect below never has to set state
  // synchronously just to describe what was already known before mounting.
  const [tokens, setTokens] = useState(() => readTokens(localStorage));
  const [loading, setLoading] = useState(() => Boolean(readTokens(localStorage)?.accessToken));

  // Confirm the restored session with the server before treating the user as signed in.
  useEffect(() => {
    const accessToken = readTokens(localStorage)?.accessToken;
    if (!accessToken) return undefined;

    let active = true;
    fetchCurrentUser(accessToken)
      .then((u) => {
        if (active) setUser(u);
      })
      .catch((err) => {
        if (!active) return;
        console.error("Failed to fetch current user:", err);
        setUser(null);
        setTokens(null);
        clearTokens(localStorage);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  // The API client signals an unrecoverable session so the UI stops showing a signed-in
  // state after a refresh token expires or is revoked.
  useEffect(() => {
    const onSessionLost = () => {
      setUser(null);
      setTokens(null);
    };
    window.addEventListener("auth:session-lost", onSessionLost);
    return () => window.removeEventListener("auth:session-lost", onSessionLost);
  }, []);

  const login = ({ user, tokens }) => {
    setUser(user);
    setTokens(tokens);
    writeTokens(localStorage, tokens);
  };

  const logout = () => {
    setUser(null);
    setTokens(null);
    clearTokens(localStorage);
  };

  const value = { user, tokens, loading, login, logout };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
