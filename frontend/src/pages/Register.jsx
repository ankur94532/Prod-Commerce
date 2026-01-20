import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { registerUser } from "../api/auth";
import { useAuth } from "../context/AuthContext.jsx";

function Register() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const data = await registerUser(form); // { user, tokens }
      login(data); // save in context/localStorage
      navigate("/"); // go home after signup
    } catch (err) {
      console.error(err);
      setError("Registration failed. Maybe email already in use?");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="max-w-md mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">Register</h2>

      {error && (
        <p className="mb-3 text-sm text-red-600 bg-red-50 border border-red-100 rounded px-3 py-2">
          {error}
        </p>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">
            Full name
          </label>
          <input
            type="text"
            name="fullName"
            value={form.fullName}
            onChange={handleChange}
            className="w-full border rounded px-3 py-2 text-sm"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">
            Email
          </label>
          <input
            type="email"
            name="email"
            value={form.email}
            onChange={handleChange}
            className="w-full border rounded px-3 py-2 text-sm"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">
            Password
          </label>
          <input
            type="password"
            name="password"
            value={form.password}
            onChange={handleChange}
            className="w-full border rounded px-3 py-2 text-sm"
            required
            minLength={6}
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full py-2 rounded bg-blue-600 text-white text-sm disabled:opacity-60"
        >
          {submitting ? "Creating account..." : "Register"}
        </button>
      </form>

      <p className="text-sm text-slate-500 mt-4">
        Already have an account?{" "}
        <Link to="/login" className="text-blue-600 hover:underline">
          Login
        </Link>
      </p>
    </section>
  );
}

export default Register;
