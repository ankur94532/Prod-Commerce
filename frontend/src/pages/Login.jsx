// src/pages/Login.jsx
import React from "react";
import { Link } from "react-router-dom";

function Login() {
  // Later: use form + call /auth/login
  return (
    <section className="max-w-md mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">Login</h2>
      <p className="text-slate-600 mb-4">
        We&apos;ll add a proper form and connect it to auth-service later.
      </p>
      <p className="text-sm text-slate-500">
        Don&apos;t have an account?{" "}
        <Link to="/register" className="text-blue-600 hover:underline">
          Register
        </Link>
      </p>
    </section>
  );
}

export default Login;