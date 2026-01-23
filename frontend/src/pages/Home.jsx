// src/pages/Home.jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getTrendingProducts } from "../api/recommendation.js";

export default function Home() {
  const [trending, setTrending] = useState([]);
  const [loadingTrending, setLoadingTrending] = useState(true);
  const [trendingError, setTrendingError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTrending() {
      setLoadingTrending(true);
      setTrendingError(null);

      try {
        const items = await getTrendingProducts(8);
        if (!cancelled) {
          setTrending(items);
        }
      } catch (err) {
        console.error("Failed to load trending products", err);
        if (!cancelled) {
          setTrendingError("Failed to load trending products.");
        }
      } finally {
        if (!cancelled) {
          setLoadingTrending(false);
        }
      }
    }

    loadTrending();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 space-y-10">
      {/* Hero / intro */}
      <section className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6 sm:p-8 flex flex-col sm:flex-row gap-6 items-center">
        <div className="flex-1">
          <h1 className="text-3xl sm:text-4xl font-bold text-slate-900 mb-3">
            Welcome to GoCommerce
          </h1>
          <p className="text-slate-600 mb-4 text-sm sm:text-base">
            A production-style demo store built with Java Spring Boot
            microservices, React, Kafka, Redis, Elasticsearch and more.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link
              to="/products"
              className="inline-flex items-center justify-center rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 transition-colors"
            >
              Start Shopping
            </Link>
            <Link
              to="/search?q=mac"
              className="inline-flex items-center justify-center rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors"
            >
              Try a search
            </Link>
          </div>
        </div>

        {/* 🔁 Replaced gray placeholder with real image */}
        <div className="w-full sm:w-64 h-40 sm:h-48">
          <img
            src="https://media.licdn.com/dms/image/v2/C560BAQEVmHLki5EcYQ/company-logo_200_200/company-logo_200_200/0/1630668766422/navco_ecommerce_logo?e=2147483647&v=beta&t=fBCZRMJknbj9i8xx_PDUYaZ_G4msujsSPJBHL1Ip4tM"
            alt="GoCommerce hero"
            className="w-full h-full rounded-xl object-cover shadow-sm"
            loading="lazy"
          />
        </div>
      </section>

      {/* Trending products from recommendation-service */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-xl font-semibold text-slate-900">
            Trending products
          </h2>
          <Link
            to="/products"
            className="text-xs font-medium text-blue-600 hover:underline"
          >
            View all products
          </Link>
        </div>

        {loadingTrending && (
          <p className="text-sm text-slate-500">Loading trending products…</p>
        )}

        {trendingError && !loadingTrending && (
          <p className="text-sm text-red-600">{trendingError}</p>
        )}

        {!loadingTrending && !trendingError && trending.length === 0 && (
          <p className="text-sm text-slate-500">
            No trending data yet. Place a few orders to see this fill up.
          </p>
        )}

        {!loadingTrending && !trendingError && trending.length > 0 && (
          <div className="grid gap-4 sm:gap-5 md:grid-cols-2 lg:grid-cols-3">
            {trending.map((item) => (
              <Link
                key={item.productId}
                to={`/search?q=${encodeURIComponent(item.productName)}`}
                className="block bg-white rounded-xl shadow-sm border border-slate-200 p-4 hover:shadow-md hover:-translate-y-0.5 transition-transform transition-shadow"
              >
                <h3 className="text-sm font-medium text-slate-900 mb-1">
                  {item.productName}
                </h3>
                <p className="text-xs text-slate-500">Trending now</p>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
