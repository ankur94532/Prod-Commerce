// src/pages/SearchResultsPage.jsx
import { useEffect, useMemo, useState } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { searchProducts } from "../api/search";
import { fetchProductFilters } from "../api/catalog";

const attributeFilterLabels = {
  color: "Color",
  type: "Type",
  fit: "Fit",
  storage: "Storage",
  memory: "Memory",
  material: "Material",
};

function formatCategoryLabel(slug) {
  if (!slug) return "";
  return slug
    .replace(/-/g, " ")
    .replace(/\b\w/g, (ch) => ch.toUpperCase());
}

export default function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const paramsKey = searchParams.toString();
  const q = searchParams.get("q") || "";
  const page = Number(searchParams.get("page") || 0);
  const size = 12;
  const filters = useMemo(
    () => ({
      category: searchParams.get("category") || "",
      brand: searchParams.get("brand") || "",
      minPrice: searchParams.get("minPrice") || "",
      maxPrice: searchParams.get("maxPrice") || "",
      inStock: searchParams.get("inStock") === "true",
      color: searchParams.get("color") || "",
      type: searchParams.get("type") || "",
      fit: searchParams.get("fit") || "",
      storage: searchParams.get("storage") || "",
      memory: searchParams.get("memory") || "",
      material: searchParams.get("material") || "",
      sort: searchParams.get("sort") || "relevance",
    }),
    [paramsKey, searchParams]
  );

  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [meta, setMeta] = useState({ page: 0, size, totalPages: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [filterOptions, setFilterOptions] = useState({
    categories: [],
    brands: [],
    price: { min: "", max: "" },
    attributes: {},
  });

  useEffect(() => {
    if (!searchParams.has("mode")) return;
    const next = new URLSearchParams(searchParams);
    next.delete("mode");
    setSearchParams(next, { replace: true });
  }, [paramsKey, searchParams, setSearchParams]);

  const priceFormatter = useMemo(
    () =>
      new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2,
      }),
    []
  );

  const hasSelectedFilters = useMemo(
    () =>
      Object.entries(filters).some(([key, value]) => {
        if (key === "sort") return value && value !== "relevance";
        return Boolean(value);
      }),
    [filters]
  );

  useEffect(() => {
    if (!q) {
      setItems([]);
      setTotal(0);
      setMeta({ page: 0, size, totalPages: 0 });
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    searchProducts({
      q,
      page,
      size,
      category: filters.category || undefined,
      brand: filters.brand || undefined,
      minPrice: filters.minPrice,
      maxPrice: filters.maxPrice,
      inStock: filters.inStock,
      color: filters.color,
      type: filters.type,
      fit: filters.fit,
      storage: filters.storage,
      memory: filters.memory,
      material: filters.material,
      sort: filters.sort,
    })
      .then((data) => {
        setItems(data?.items || []);
        setTotal(data?.total || 0);
        setMeta({
          page: data?.page || 0,
          size: data?.size || size,
          totalPages: data?.totalPages || 0,
        });
      })
      .catch((err) => {
        console.error("Search failed", err);
        setError("Failed to load search results. Please try again.");
      })
      .finally(() => setLoading(false));
  }, [paramsKey]);

  useEffect(() => {
    fetchProductFilters({ category: filters.category || undefined })
      .then((options) => {
        setFilterOptions({
          categories: options.categories || [],
          brands: options.brands || [],
          price: options.price || { min: "", max: "" },
          attributes: options.attributes || {},
        });
      })
      .catch((err) => {
        console.error("Failed to load search filter options", err);
      });
  }, [filters.category]);

  const updateFilter = (name, value) => {
    const next = new URLSearchParams(searchParams);
    next.delete("page");

    if (name === "category") {
      ["color", "type", "fit", "storage", "memory", "material"].forEach((key) =>
        next.delete(key)
      );
    }

    if (value === "" || value === false || value === null || value === undefined) {
      next.delete(name);
    } else {
      next.set(name, String(value));
    }

    if (name === "sort" && value === "relevance") {
      next.delete("sort");
    }

    setSearchParams(next);
  };

  const clearFilters = () => {
    const next = new URLSearchParams();
    if (q) next.set("q", q);
    setSearchParams(next);
  };

  const changePage = (newPage) => {
    if (newPage < 0 || newPage >= meta.totalPages) return;
    const next = new URLSearchParams(searchParams);
    if (newPage === 0) {
      next.delete("page");
    } else {
      next.set("page", String(newPage));
    }
    setSearchParams(next);
  };

  if (!q) {
    return (
      <section className="max-w-6xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-semibold mb-2">Search</h1>
        <p className="text-slate-600">
          Type something in the search bar to find products.
        </p>
      </section>
    );
  }

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-2 mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            Search results for{" "}
            <span className="text-blue-600 break-words">“{q}”</span>
          </h1>
          {!loading && !error && (
            <p className="text-sm text-slate-500 mt-1">
              {total} result{total !== 1 ? "s" : ""} found
            </p>
          )}
        </div>
      </header>

      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
            Category
            <select
              value={filters.category}
              onChange={(e) => updateFilter("category", e.target.value)}
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
              onChange={(e) => updateFilter("brand", e.target.value)}
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
              onChange={(e) => updateFilter("minPrice", e.target.value)}
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
              onChange={(e) => updateFilter("maxPrice", e.target.value)}
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
                  onChange={(e) => updateFilter(key, e.target.value)}
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
              onChange={(e) => updateFilter("sort", e.target.value)}
              className="border rounded px-2 py-2 text-sm font-normal text-slate-900"
            >
              <option value="relevance">Relevance</option>
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
                onChange={(e) => updateFilter("inStock", e.target.checked)}
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

      {loading && (
        <div className="text-slate-600 text-sm">Loading results…</div>
      )}

      {error && !loading && (
        <div className="text-sm text-red-600">{error}</div>
      )}

      {!loading && !error && total === 0 && (
        <p className="text-slate-600 text-sm">
          No products found for “{q}”
          {hasSelectedFilters ? " with the selected filters." : "."}
        </p>
      )}

      {!loading && !error && total > 0 && (
        <>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => (
              <article
                key={item.id}
                className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden flex flex-col"
              >
                {item.thumbnailUrl && (
                  <div className="aspect-[4/3] bg-slate-100">
                    <img
                      src={item.thumbnailUrl}
                      alt={item.name}
                      className="w-full h-full object-cover"
                    />
                  </div>
                )}

                <div className="p-4 flex flex-col gap-2 flex-1">
                  <h3 className="text-sm font-medium text-slate-900">
                    {item.name}
                  </h3>

                  {item.category && (
                    <p className="text-xs text-slate-500 uppercase tracking-wide">
                      {item.category}
                    </p>
                  )}

                  <p className="text-base font-semibold text-slate-900">
                    {typeof item.price === "number"
                      ? priceFormatter.format(item.price)
                      : item.price}{" "}
                    {item.currency && item.currency !== "INR"
                      ? `(${item.currency})`
                      : ""}
                  </p>

                  {item.slug && (
                    <Link
                      to={`/products/${item.slug}`}
                      className="mt-auto inline-flex text-sm text-blue-600 hover:text-blue-700"
                    >
                      View details
                    </Link>
                  )}
                </div>
              </article>
            ))}
          </div>

          {meta.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 mt-6 text-sm">
              <button
                onClick={() => changePage(meta.page - 1)}
                disabled={meta.page === 0}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Prev
              </button>
              <span>
                Page {meta.page + 1} of {meta.totalPages}
              </span>
              <button
                onClick={() => changePage(meta.page + 1)}
                disabled={meta.page + 1 >= meta.totalPages}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
