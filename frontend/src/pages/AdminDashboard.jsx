// src/pages/AdminDashboard.jsx
import { Link } from "react-router-dom";

export default function AdminDashboard() {
  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">
          Admin Dashboard
        </h1>
        <p className="text-slate-500 text-sm mt-1">
          Manage your store and view performance insights.
        </p>
      </header>

      <section
        className="grid gap-4 md:gap-6"
        style={{
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        }}
      >
        <Link
          to="/admin/analytics"
          className="block bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6 hover:shadow-md transition-shadow"
        >
          <h2 className="text-sm font-medium text-slate-800">
            View Analytics
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            See total orders, revenue, and average order value.
          </p>
        </Link>

        <Link
          to="/admin/products"
          className="block bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6 hover:shadow-md transition-shadow"
        >
          <h2 className="text-sm font-medium text-slate-800">
            Manage Products
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            Add, edit, and delete products in your catalog.
          </p>
        </Link>

        <Link
          to="/admin/users"
          className="block bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6 hover:shadow-md transition-shadow"
        >
          <h2 className="text-sm font-medium text-slate-800">
            Manage Users
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            View registered users, change roles, and delete accounts.
          </p>
        </Link>
      </section>
    </div>
  );
}
