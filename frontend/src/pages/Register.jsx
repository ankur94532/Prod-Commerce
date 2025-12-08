// src/pages/Register.jsx
import React from "react";
import { Link } from "react-router-dom";

function Register() {
  return (
    <section className="max-w-md mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">Register</h2>
      <p className="text-slate-600 mb-4">
        Registration form will submit to /auth/register later.
      </p>
      <p className="text-sm text-slate-500">
        Already have an account?{" "}
        <Link to="/login" className="text-blue-600 hover:underline">
          Login
        </Link>
      </p>
    </section>
  );
}

export default Register;