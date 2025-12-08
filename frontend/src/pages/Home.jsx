// src/pages/Home.jsx
import React from "react";
import { Link } from "react-router-dom";

function Home() {
  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <div className="bg-blue-50 rounded-xl p-8 mb-8">
        <h1 className="text-3xl font-bold mb-2 text-slate-900">
          GoCommerce (Java Edition)
        </h1>
        <p className="text-slate-700 mb-4">
          A production-style e-commerce app you&apos;re building with
          Java + Spring Boot + Kafka and React.
        </p>
        <Link
          to="/products"
          className="inline-block px-4 py-2 rounded-md bg-blue-600 text-white text-sm"
        >
          Start Shopping
        </Link>
      </div>
    </section>
  );
}

export default Home;
