import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import "./styles/index.css";
import { AuthProvider } from "./context/AuthContext.jsx";
import AppErrorBoundary from "./components/AppErrorBoundary.jsx";
import { installClientObservability } from "./observability";

installClientObservability();

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </AppErrorBoundary>
  </React.StrictMode>
);
