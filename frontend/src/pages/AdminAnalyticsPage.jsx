// src/pages/AdminAnalyticsPage.jsx
import { useEffect, useMemo, useState } from "react";
import { getAnalyticsSummary } from "../api/analytics";

export default function AdminAnalyticsPage() {
  const [summary, setSummary] = useState(null);
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

  const fetchSummary = () => {
    setLoading(true);
    setError(null);

    getAnalyticsSummary()
      .then((data) => {
        setSummary(data);
      })
      .catch((err) => {
        console.error("Failed to load analytics summary", err);
        setError("Failed to load analytics. Please try again.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchSummary();
  }, []);

  const totalOrders = summary?.totalOrders ?? 0;

  // Handle BigDecimal serialized as number or string
  const rawRevenue = summary?.totalRevenue;
  let totalRevenue = 0;
  if (rawRevenue !== null && rawRevenue !== undefined) {
    const num = Number(rawRevenue);
    totalRevenue = Number.isNaN(num) ? 0 : num;
  }

  const avgOrderValue =
    totalOrders > 0 ? totalRevenue / totalOrders : 0;

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            Admin Analytics
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            High-level overview of store performance.
          </p>
        </div>
        <button
          type="button"
          onClick={fetchSummary}
          className="inline-flex items-center justify-center rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 transition-colors disabled:opacity-60"
          disabled={loading}
        >
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </header>

      {loading && (
        <div className="text-slate-600 text-sm">
          Loading analytics…
        </div>
      )}

      {error && !loading && (
        <div className="text-sm text-red-600">{error}</div>
      )}

      {!loading && !error && (
        <section
          className="grid gap-4 md:gap-6"
          style={{
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          }}
        >
          <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6">
            <h2 className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Total Orders
            </h2>
            <p className="mt-2 text-3xl font-semibold text-slate-900">
              {totalOrders}
            </p>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6">
            <h2 className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Total Revenue
            </h2>
            <p className="mt-2 text-3xl font-semibold text-emerald-600">
              {formatter.format(totalRevenue)}
            </p>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sm:p-6">
            <h2 className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Average Order Value
            </h2>
            <p className="mt-2 text-3xl font-semibold text-indigo-600">
              {formatter.format(avgOrderValue)}
            </p>
            <p className="mt-1 text-xs text-slate-400">
              Total revenue / total orders
            </p>
          </div>
        </section>
      )}
    </div>
  );
}
