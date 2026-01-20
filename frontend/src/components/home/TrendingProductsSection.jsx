// src/components/home/TrendingProductsSection.jsx
import { useEffect, useMemo, useState } from "react";
import { getTrendingProducts } from "../../api/recommendation.js";

export default function TrendingProductsSection() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const formatter = useMemo(
    () =>
      new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2,
      }),
    []
  );

  useEffect(() => {
    setLoading(true);
    setError(null);

    getTrendingProducts(5)
      .then((data) => {
        setItems(data.items || []);
      })
      .catch((err) => {
        console.error("Failed to load trending products", err);
        setError("Failed to load trending products.");
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading && items.length === 0) {
    return (
      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">
          Trending products
        </h2>
        <p className="mt-2 text-sm text-slate-500">Loading…</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">
          Trending products
        </h2>
        <p className="mt-2 text-sm text-red-600">{error}</p>
      </section>
    );
  }

  if (!items.length) {
    return (
      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">
          Trending products
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          No trending products yet. Place some orders to see this populate.
        </p>
      </section>
    );
  }

  return (
    <section className="mt-8">
      <div className="flex items-center justify-between gap-2 mb-3">
        <h2 className="text-lg font-semibold text-slate-900">
          Trending products
        </h2>
        <p className="text-xs text-slate-500">
          Based on total quantity sold
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        {items.map((item) => (
          <div
            key={item.productId}
            className="bg-white border border-slate-200 rounded-xl shadow-sm p-4 flex flex-col"
          >
            <h3 className="text-sm font-medium text-slate-900 line-clamp-2">
              {item.productName}
            </h3>

            <p className="mt-1 text-xs text-slate-500">
              Product ID: {item.productId}
            </p>

            <div className="mt-3 flex flex-col gap-1 text-sm">
              <p className="text-slate-700">
                Total sold:{" "}
                <span className="font-semibold">{item.totalQuantity}</span>
              </p>
              <p className="text-slate-700">
                Total revenue:{" "}
                <span className="font-semibold">
                  {formatter.format(
                    Number(item.totalRevenue ?? 0) || 0
                  )}
                </span>
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
