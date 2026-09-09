import { createContext, useContext } from "react";

// Kept out of AuthContext.jsx so that file exports components only, which is what lets
// React Fast Refresh work reliably during development.
export const AuthContext = createContext(null);

export function useAuth() {
  return useContext(AuthContext);
}
