// src/pages/Products.jsx
import React from "react";

function Products() {
  // Later: fetch from /api/v1/products
  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">All Products</h2>
      <p className="text-slate-600">
        Product listing will come here (fetched from the catalog-service).
      </p>
    </section>
  );
}

export default Products;
