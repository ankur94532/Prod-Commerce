// src/pages/AdminProductsPage.jsx
import { useEffect, useMemo, useState } from "react";
import {
  listAdminProducts,
  createAdminProduct,
  updateAdminProduct,
  deleteAdminProduct,
} from "../api/adminProducts";

const emptyForm = {
  id: null,
  slug: "",
  name: "",
  description: "",
  price: "",
  currency: "INR",
  categorySlug: "",
  brand: "",
  stockQuantity: "",
  imageUrlsCsv: "",
  active: true,
};

export default function AdminProductsPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(emptyForm);

  const moneyFormatter = useMemo(
    () =>
      new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2,
      }),
    []
  );

  // ---- Load products ----
  const loadPage = async (targetPage = 0) => {
    try {
      setLoading(true);
      setError(null);
      const res = await listAdminProducts({ page: targetPage, size });
      setItems(res.items || []);
      setPage(res.page ?? targetPage);
      setTotalPages(res.totalPages ?? 0);
    } catch (err) {
      console.error("Failed to load products", err);
      setError("Failed to load products. Please try again.");
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
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  const resetForm = () => {
    setForm(emptyForm);
  };

  const handleEditClick = (product) => {
    const imageUrls = Array.isArray(product.imageUrls) ? product.imageUrls : [];
    setForm({
      id: product.id,
      slug: product.slug || "",
      name: product.name || "",
      description: product.description || "",
      price: product.price != null ? String(product.price) : "",
      currency: product.currency || "INR",
      categorySlug: product.categorySlug || "",
      brand: product.brand || "",
      stockQuantity:
        product.stockQuantity != null ? String(product.stockQuantity) : "",
      imageUrlsCsv: imageUrls.join(", "),
      active: product.active ?? true,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleDeleteClick = async (id) => {
    const yes = window.confirm(
      "Hide this product from catalog (set it inactive)?"
    );
    if (!yes) return;

    try {
      setSaving(true);
      // soft delete → PATCH /status { active: false }
      await deleteAdminProduct(id);
      await loadPage(page);
    } catch (err) {
      console.error("Failed to update product status", err);
      alert("Failed to update product status. Check console for details.");
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!form.slug.trim() || !form.name.trim()) {
      alert("Slug and name are required.");
      return;
    }

    const priceNumber = Number(form.price);
    const stockNumber = Number(form.stockQuantity);

    const imageUrls = form.imageUrlsCsv
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

    const payload = {
      slug: form.slug.trim(),
      name: form.name.trim(),
      description: form.description.trim(),
      price: Number.isNaN(priceNumber) ? 0 : priceNumber,
      currency: form.currency || "INR",
      categorySlug: form.categorySlug.trim() || null,
      brand: form.brand.trim() || null,
      stockQuantity: Number.isNaN(stockNumber) ? 0 : stockNumber,
      imageUrls,
      active: !!form.active,
      attributes: {}, // extension point
    };

    try {
      setSaving(true);
      if (form.id == null) {
        await createAdminProduct(payload);
      } else {
        await updateAdminProduct(form.id, payload);
      }
      resetForm();
      await loadPage(page);
    } catch (err) {
      console.error("Failed to save product", err);
      alert("Failed to save product. Check console for details.");
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

  // ---- UI ----
  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            Manage Products
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Add, edit, and hide products in your catalog.
          </p>
        </div>
      </header>

      {/* Form */}
      <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6 mb-8">
        <h2 className="text-sm font-medium text-slate-800 mb-4">
          {form.id ? "Edit Product" : "Create New Product"}
        </h2>

        <form
          onSubmit={handleSubmit}
          className="grid gap-4 md:grid-cols-2 md:gap-6"
        >
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Slug *
            </label>
            <input
              type="text"
              name="slug"
              value={form.slug}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="s26-ultra-256gb-gray"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Name *
            </label>
            <input
              type="text"
              name="name"
              value={form.name}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="Galaxy S26 Ultra 256GB (Gray)"
              required
            />
          </div>

          <div className="md:col-span-2">
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Description
            </label>
            <textarea
              name="description"
              value={form.description}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              rows={3}
              placeholder="Short description of the product"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Price (INR)
            </label>
            <input
              type="number"
              name="price"
              value={form.price}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              min="0"
              step="0.01"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Stock Quantity
            </label>
            <input
              type="number"
              name="stockQuantity"
              value={form.stockQuantity}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              min="0"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Category Slug
            </label>
            <input
              type="text"
              name="categorySlug"
              value={form.categorySlug}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="smartphones"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Brand
            </label>
            <input
              type="text"
              name="brand"
              value={form.brand}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="Samsung"
            />
          </div>

          <div className="md:col-span-2">
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Image URLs (comma-separated)
            </label>
            <input
              type="text"
              name="imageUrlsCsv"
              value={form.imageUrlsCsv}
              onChange={handleFieldChange}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="https://..., https://..."
            />
          </div>

          <div className="flex items-center gap-2">
            <input
              id="active"
              type="checkbox"
              name="active"
              checked={form.active}
              onChange={handleFieldChange}
              className="h-4 w-4 rounded border-slate-300"
            />
            <label
              htmlFor="active"
              className="text-xs font-medium text-slate-700"
            >
              Active (visible in catalog)
            </label>
          </div>

          <div className="md:col-span-2 flex items-center gap-3 mt-2">
            <button
              type="submit"
              disabled={saving}
              className="inline-flex items-center rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {saving
                ? "Saving..."
                : form.id
                ? "Update Product"
                : "Create Product"}
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
            Products ({items.length})
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
          <p className="text-sm text-slate-500">Loading products…</p>
        )}

        {error && !loading && (
          <p className="text-sm text-red-600 mb-2">{error}</p>
        )}

        {!loading && !error && items.length === 0 && (
          <p className="text-sm text-slate-500">No products found.</p>
        )}

        {!loading && !error && items.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b">
                  <th className="py-2 pr-4">Name</th>
                  <th className="py-2 pr-4">Slug</th>
                  <th className="py-2 pr-4">Category</th>
                  <th className="py-2 pr-4">Price</th>
                  <th className="py-2 pr-4">Stock</th>
                  <th className="py-2 pr-4">Active</th>
                  <th className="py-2 pr-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((p) => (
                  <tr key={p.id} className="border-b last:border-0">
                    <td className="py-2 pr-4">{p.name}</td>
                    <td className="py-2 pr-4 text-xs text-slate-500">
                      {p.slug}
                    </td>
                    <td className="py-2 pr-4 text-xs text-slate-500">
                      {p.categorySlug || "—"}
                    </td>
                    <td className="py-2 pr-4">
                      {moneyFormatter.format(Number(p.price ?? 0))}
                    </td>
                    <td className="py-2 pr-4">{p.stockQuantity ?? 0}</td>
                    <td className="py-2 pr-4">
                      {p.active ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700">
                          Active
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-500">
                          Hidden
                        </span>
                      )}
                    </td>
                    <td className="py-2 pr-4 text-right space-x-2">
                      <button
                        type="button"
                        onClick={() => handleEditClick(p)}
                        className="text-xs text-indigo-600 hover:underline"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteClick(p.id)}
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
