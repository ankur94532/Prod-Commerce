// src/pages/SearchResultsPage.jsx
import { useEffect, useMemo, useState } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { searchProducts } from "../api/search";

export default function SearchResultsPage() {
  const [searchParams] = useSearchParams();
  const q = searchParams.get("q") || "";

  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const priceFormatter = useMemo(
    () =>
      new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2,
      }),
    []
  );

  useEffect(() => {
    if (!q) {
      setItems([]);
      setTotal(0);
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    searchProducts({ q })
      .then((data) => {
        setItems(data?.items || []);
        setTotal(data?.total || 0);
      })
      .catch((err) => {
        console.error("Search failed", err);
        setError("Failed to load search results. Please try again.");
      })
      .finally(() => setLoading(false));
  }, [q]);

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

      {loading && (
        <div className="text-slate-600 text-sm">Loading results…</div>
      )}

      {error && !loading && (
        <div className="text-sm text-red-600">{error}</div>
      )}

      {!loading && !error && total === 0 && (
        <p className="text-slate-600 text-sm">
          No results found. Try a different query.
        </p>
      )}

      {!loading && !error && total > 0 && (
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
      )}
    </section>
  );
}
