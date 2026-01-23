// src/pages/AdminUsersPage.jsx
import { useEffect, useState } from "react";
import {
  listAdminUsers,
  updateAdminUser,
  deleteAdminUser,
} from "../api/adminUsers";

const emptyForm = {
  id: null,
  email: "",
  fullName: "",
  role: "USER",
};

export default function AdminUsersPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(emptyForm);

  // ---- Load users ----
  const loadPage = async (targetPage = 0) => {
    try {
        console.log("Let's load all the users");
      setLoading(true);
      setError(null);
      const res = await listAdminUsers({ page: targetPage, size });
      setItems(res.items || []);
      setPage(res.page ?? targetPage);
      setTotalPages(res.totalPages ?? 0);
    } catch (err) {
      console.error("Failed to load users", err);
      setError("Failed to load users. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPage(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- Form handlers ----
  const handleFieldChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const resetForm = () => {
    setForm(emptyForm);
  };

  const handleEditClick = (user) => {
    setForm({
      id: user.id,
      email: user.email || "",
      fullName: user.fullName || "",
      role: user.role || "USER",
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleDeleteClick = async (id) => {
    const yes = window.confirm(
      "Are you sure you want to permanently delete this user?"
    );
    if (!yes) return;

    try {
      setSaving(true);
      await deleteAdminUser(id);
      await loadPage(page);
    } catch (err) {
      console.error("Failed to delete user", err);
      alert("Failed to delete user. Check console for details.");
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!form.id) {
      alert("Creating users from admin UI is not supported. Use registration.");
      return;
    }

    if (!form.fullName.trim() || !form.role.trim()) {
      alert("Full name and role are required.");
      return;
    }

    const payload = {
      fullName: form.fullName.trim(),
      role: form.role.trim(),
    };

    try {
      setSaving(true);
      await updateAdminUser(form.id, payload);
      resetForm();
      await loadPage(page);
    } catch (err) {
      console.error("Failed to update user", err);
      alert("Failed to update user. Check console for details.");
    } finally {
      setSaving(false);
    }
  };

  const handlePrev = () => {
    if (page > 0) {
      loadPage(page - 1);
    }
  };

  const handleNext = () => {
    if (page + 1 < totalPages) {
      loadPage(page + 1);
    }
  };

  const formatDateTime = (value) => {
    if (!value) return "—";
    try {
      return new Date(value).toLocaleString("en-IN");
    } catch {
      return String(value);
    }
  };

  // ---- UI ----
  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            Manage Users
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            View registered users, change roles, and delete accounts.
          </p>
        </div>
      </header>

      {/* Edit form (for existing users) */}
      <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6 mb-8">
        <h2 className="text-sm font-medium text-slate-800 mb-4">
          {form.id ? "Edit User" : "Edit User (select from table below)"}
        </h2>

        <form
          onSubmit={handleSubmit}
          className="grid gap-4 md:grid-cols-2 md:gap-6"
        >
          <div className="md:col-span-2">
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Email
            </label>
            <input
              type="email"
              name="email"
              value={form.email}
              readOnly
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm bg-slate-50 cursor-not-allowed"
              placeholder="Select a user from the table"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Full Name
            </label>
            <input
              type="text"
              name="fullName"
              value={form.fullName}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="User's full name"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Role
            </label>
            <select
              name="role"
              value={form.role}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>

          <div className="md:col-span-2 flex items-center gap-3 mt-2">
            <button
              type="submit"
              disabled={saving || !form.id}
              className="inline-flex items-center rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {saving ? "Saving..." : "Save Changes"}
            </button>
            {form.id && (
              <button
                type="button"
                onClick={resetForm}
                className="text-xs text-slate-500 hover:text-slate-700"
              >
                Cancel edit
              </button>
            )}
          </div>
        </form>
      </section>

      {/* List */}
      <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-medium text-slate-800">
            Users ({items.length})
          </h2>
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <button
              type="button"
              onClick={handlePrev}
              disabled={page === 0 || loading}
              className="px-2 py-1 border rounded disabled:opacity-50"
            >
              Prev
            </button>
            <span>
              Page {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              type="button"
              onClick={handleNext}
              disabled={page + 1 >= totalPages || loading}
              className="px-2 py-1 border rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>

        {loading && (
          <p className="text-sm text-slate-500">Loading users…</p>
        )}

        {error && !loading && (
          <p className="text-sm text-red-600 mb-2">{error}</p>
        )}

        {!loading && !error && items.length === 0 && (
          <p className="text-sm text-slate-500">No users found.</p>
        )}

        {!loading && !error && items.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b">
                  <th className="py-2 pr-4">Email</th>
                  <th className="py-2 pr-4">Full Name</th>
                  <th className="py-2 pr-4">Role</th>
                  <th className="py-2 pr-4">Created At</th>
                  <th className="py-2 pr-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((u) => (
                  <tr key={u.id} className="border-b last:border-0">
                    <td className="py-2 pr-4 text-xs text-slate-700">
                      {u.email}
                    </td>
                    <td className="py-2 pr-4">{u.fullName}</td>
                    <td className="py-2 pr-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-700">
                        {u.role}
                      </span>
                    </td>
                    <td className="py-2 pr-4 text-xs text-slate-500">
                      {formatDateTime(u.createdAt)}
                    </td>
                    <td className="py-2 pr-4 text-right space-x-2">
                      <button
                        type="button"
                        onClick={() => handleEditClick(u)}
                        className="text-xs text-indigo-600 hover:underline"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteClick(u.id)}
                        className="text-xs text-red-600 hover:underline"
                        disabled={saving}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
