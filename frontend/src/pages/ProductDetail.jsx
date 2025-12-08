// src/pages/ProductDetail.jsx
import React from "react";
import { useParams } from "react-router-dom";

function ProductDetail() {
  const { slug } = useParams();

  return (
    <section className="max-w-4xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-2">Product: {slug}</h2>
      <p className="text-slate-600">
        Product details will be loaded from /api/v1/products/{slug} later.
      </p>
    </section>
  );
}

export default ProductDetail;