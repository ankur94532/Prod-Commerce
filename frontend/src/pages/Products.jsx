// src/pages/Products.jsx
import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchProducts, fetchProductFilters } from "../api/catalog";

function formatCategoryLabel(slug) {
  if (!slug) return "";
  return slug
    .replace(/-/g, " ")
    .replace(/\b\w/g, (ch) => ch.toUpperCase());
}

const attributeFilterLabels = {
  color: "Color",
  type: "Type",
  fit: "Fit",
  storage: "Storage",
  memory: "Memory",
  material: "Material",
};

function Products() {
  const emptyFilters = {
    category: "",
    brand: "",
    minPrice: "",
    maxPrice: "",
    inStock: false,
    color: "",
    type: "",
    fit: "",
    storage: "",
    memory: "",
    material: "",
    sort: "newest",
  };
  const [products, setProducts] = useState([]);
  const [meta, setMeta] = useState({
    page: 0,
    size: 12,
    totalPages: 0,
    totalElements: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [filters, setFilters] = useState(emptyFilters);
  const [filterOptions, setFilterOptions] = useState({
    categories: [],
    brands: [],
    price: { min: "", max: "" },
    attributes: {},
  });

  const loadFilterOptions = async (category = filters.category) => {
    try {
      const options = await fetchProductFilters({ category: category || undefined });
      setFilterOptions({
        categories: options.categories || [],
        brands: options.brands || [],
        price: options.price || { min: "", max: "" },
        attributes: options.attributes || {},
      });
    } catch (err) {
      console.error("Failed to load filter options", err);
    }
  };

  const loadProducts = async (page = 0, activeFilters = filters) => {
    setLoading(true);
    setError("");
    try {
      const res = await fetchProducts({
        page,
        size: 12,
        category: activeFilters.category || undefined,
        brand: activeFilters.brand || undefined,
        minPrice: activeFilters.minPrice,
        maxPrice: activeFilters.maxPrice,
        inStock: activeFilters.inStock,
        color: activeFilters.color,
        type: activeFilters.type,
        fit: activeFilters.fit,
        storage: activeFilters.storage,
        memory: activeFilters.memory,
        material: activeFilters.material,
        sort: activeFilters.sort,
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
    loadProducts(0);
    loadFilterOptions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleFilterChange = (name, value) => {
    const nextFilters = { ...filters, [name]: value };
    if (name === "category") {
      nextFilters.color = "";
      nextFilters.type = "";
      nextFilters.fit = "";
      nextFilters.storage = "";
      nextFilters.memory = "";
      nextFilters.material = "";
      loadFilterOptions(value);
    }
    setFilters(nextFilters);
    loadProducts(0, nextFilters);
  };

  const clearFilters = () => {
    setFilters(emptyFilters);
    loadFilterOptions("");
    loadProducts(0, emptyFilters);
  };

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= meta.totalPages) return;
    loadProducts(newPage);
  };

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-4 gap-3">
        <h2 className="text-2xl font-semibold">All Products</h2>
        <p className="text-sm text-slate-500">{meta.totalElements} items</p>
      </div>

      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Category
            <select
              value={filters.category}
              onChange={(e) => handleFilterChange("category", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            >
              <option value="">All categories</option>
              {filterOptions.categories.map((cat) => (
                <option key={cat} value={cat}>
                  {formatCategoryLabel(cat)}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Brand
            <select
              value={filters.brand}
              onChange={(e) => handleFilterChange("brand", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            >
              <option value="">All brands</option>
              {filterOptions.brands.map((brand) => (
                <option key={brand} value={brand}>
                  {brand}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Min price
            <input
              type="number"
              min={filterOptions.price.min || 0}
              placeholder={filterOptions.price.min || "0"}
              value={filters.minPrice}
              onChange={(e) => handleFilterChange("minPrice", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            />
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Max price
            <input
              type="number"
              min={filterOptions.price.min || 0}
              placeholder={filterOptions.price.max || "Any"}
              value={filters.maxPrice}
              onChange={(e) => handleFilterChange("maxPrice", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            />
          </label>

          {Object.entries(attributeFilterLabels).map(([key, label]) => {
            const values = filterOptions.attributes[key] || [];
            if (values.length === 0) return null;
            return (
              <label
                key={key}
                className="flex flex-col gap-1 text-xs font-medium text-slate-600"
              >
                {label}
                <select
                  value={filters[key]}
                  onChange={(e) => handleFilterChange(key, e.target.value)}
                  className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
                >
                  <option value="">Any {label.toLowerCase()}</option>
                  {values.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </label>
            );
          })}

          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Sort
            <select
              value={filters.sort}
              onChange={(e) => handleFilterChange("sort", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            >
              <option value="newest">Newest</option>
              <option value="price_asc">Price low to high</option>
              <option value="price_desc">Price high to low</option>
              <option value="name_asc">Name A-Z</option>
              <option value="name_desc">Name Z-A</option>
            </select>
          </label>

          <div className="flex items-end gap-3 sm:col-span-2 lg:col-span-1">
            <label className="flex h-10 items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={filters.inStock}
                onChange={(e) => handleFilterChange("inStock", e.target.checked)}
                className="h-4 w-4"
              />
              In stock
            </label>
            <button
              type="button"
              onClick={clearFilters}
              className="h-10 rounded border border-slate-300 px-3 text-sm text-slate-700 hover:bg-slate-50"
            >
              Clear
            </button>
          </div>
        </div>
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
