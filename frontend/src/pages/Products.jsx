// src/pages/Products.jsx
import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchProducts, fetchCategories } from "../api/catalog";

function formatCategoryLabel(slug) {
  if (!slug) return "";
  return slug
    .replace(/-/g, " ")
    .replace(/\b\w/g, (ch) => ch.toUpperCase());
}

function Products() {
  const [products, setProducts] = useState([]);
  const [meta, setMeta] = useState({
    page: 0,
    size: 12,
    totalPages: 0,
    totalElements: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [category, setCategory] = useState("");
  const [categories, setCategories] = useState([]); // 🔹 dynamic categories

  const loadProducts = async (page = 0, cat = category) => {
    setLoading(true);
    setError("");
    try {
      const res = await fetchProducts({
        page,
        size: 12,
        category: cat || undefined,
      });

      // res is already the page object (from fetchProducts)
      setProducts(res.data);
      setMeta({
        page: res.page,
        size: res.size,
        totalPages: res.totalPages,
        totalElements: res.totalElements,
      });
    } catch (err) {
      console.error(err);
      setError("Failed to load products.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // load initial products
    loadProducts(0);

    // load categories for dropdown
    const loadCategories = async () => {
      try {
        const list = await fetchCategories(); // ["smartphones", "earbuds", ...]
        setCategories(list || []);
      } catch (err) {
        console.error("Failed to load categories", err);
        // silently fall back to just "All categories"
      }
    };

    loadCategories();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCategoryChange = (e) => {
    const value = e.target.value;
    setCategory(value);
    loadProducts(0, value);
  };

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= meta.totalPages) return;
    loadProducts(newPage);
  };

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-semibold">All Products</h2>

        <select
          value={category}
          onChange={handleCategoryChange}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="">All categories</option>
          {categories.map((cat) => (
            <option key={cat} value={cat}>
              {formatCategoryLabel(cat)}
            </option>
          ))}
        </select>
      </div>

      {loading && <p className="text-slate-600 mb-4">Loading products...</p>}
      {error && (
        <p className="text-red-600 mb-4 text-sm bg-red-50 border border-red-100 rounded px-3 py-2">
          {error}
        </p>
      )}

      {!loading && products.length === 0 && !error && (
        <p className="text-slate-500">No products found.</p>
      )}

      <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
        {products.map((p) => (
          <Link
            key={p.id}
            to={`/products/${p.slug}`}
            className="bg-white rounded-lg shadow-sm hover:shadow-md transition p-3 flex flex-col"
          >
            <div className="aspect-[4/3] bg-slate-100 rounded mb-3 flex items-center justify-center overflow-hidden">
              {p.imageUrls && p.imageUrls.length > 0 ? (
                <img
                  src={p.imageUrls[0]}
                  alt={p.name}
                  className="w-full h-full object-cover"
                />
              ) : (
                <span className="text-xs text-slate-400">No image</span>
              )}
            </div>
            <h3 className="text-sm font-medium text-slate-900 mb-1 line-clamp-2">
              {p.name}
            </h3>
            <p className="text-sm text-slate-500 mb-1">
              {p.brand} • {p.categorySlug}
            </p>
            <p className="text-base font-semibold text-slate-900 mb-1">
              {p.currency} {p.price}
            </p>
            <p className="text-xs text-slate-500 mt-auto">
              {p.stockQuantity > 0
                ? `${p.stockQuantity} in stock`
                : "Out of stock"}
            </p>
          </Link>
        ))}
      </div>

      {meta.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-6 text-sm">
          <button
            onClick={() => handlePageChange(meta.page - 1)}
            disabled={meta.page === 0}
            className="px-3 py-1 border rounded disabled:opacity-50"
          >
            Prev
          </button>
          <span>
            Page {meta.page + 1} of {meta.totalPages}
          </span>
          <button
            onClick={() => handlePageChange(meta.page + 1)}
            disabled={meta.page + 1 >= meta.totalPages}
            className="px-3 py-1 border rounded disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}

export default Products;
