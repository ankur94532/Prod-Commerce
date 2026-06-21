// src/pages/ProductDetail.jsx
import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { fetchProductBySlug } from "../api/catalog";
import { addCartItem } from "../api/cart";
import { useAuth } from "../context/AuthContext.jsx";
import ProductImage from "../components/ProductImage";

function ProductDetail() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);
  const [addedMessage, setAddedMessage] = useState("");

  useEffect(() => {
    let isMounted = true;

    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const p = await fetchProductBySlug(slug);
        if (isMounted) setProduct(p);
      } catch (err) {
        console.error(err);
        if (isMounted) setError("Failed to load product.");
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    load();

    return () => {
      isMounted = false;
    };
  }, [slug]);

  const handleAddToCart = async () => {
    if (!user) {
      // redirect to login and come back here after login
      navigate("/login", { state: { from: `/products/${slug}` } });
      return;
    }

    if (!product) return;

    setAdding(true);
    setAddedMessage("");
    try {
      await addCartItem(user.id, product, 1);
      setAddedMessage("Added to cart!");
    } catch (err) {
      console.error(err);
      setAddedMessage("Failed to add to cart.");
    } finally {
      setAdding(false);
      setTimeout(() => setAddedMessage(""), 2500);
    }
  };

  if (loading) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-slate-600">Loading product...</p>
      </section>
    );
  }

  if (error || !product) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-red-600">{error || "Product not found."}</p>
      </section>
    );
  }

  return (
    <section className="max-w-4xl mx-auto px-4 py-8 grid gap-6 md:grid-cols-2">
      <div className="bg-white rounded-lg shadow-sm p-4 flex items-center justify-center">
        <div className="w-full aspect-[4/3] bg-slate-100 rounded flex items-center justify-center overflow-hidden">
          <ProductImage
            src={product.imageUrls?.[0]}
            alt={product.name}
            category={product.categorySlug}
            className="w-full h-full object-cover"
          />
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-4">
        <h1 className="text-2xl font-semibold mb-2">{product.name}</h1>
        <p className="text-sm text-slate-500 mb-3">
          {product.brand} • {product.categorySlug}
        </p>

        <p className="text-2xl font-bold text-slate-900 mb-4">
          {product.currency} {product.price}
        </p>

        <p className="text-sm text-slate-700 mb-4">{product.description}</p>

        <p className="text-sm text-slate-600 mb-2">
          {product.stockQuantity > 0
            ? `${product.stockQuantity} in stock`
            : "Out of stock"}
        </p>

        {product.attributes && (
          <div className="mt-4">
            <h2 className="text-sm font-semibold mb-2">Specifications</h2>
            <ul className="text-sm text-slate-600 space-y-1">
              {Object.entries(product.attributes).map(([key, value]) => (
                <li key={key}>
                  <span className="font-medium capitalize">{key}:</span> {value}
                </li>
              ))}
            </ul>
          </div>
        )}

        {addedMessage && (
          <p className="text-xs mt-3 text-green-600">{addedMessage}</p>
        )}

        <button
          disabled={product.stockQuantity <= 0 || adding}
          onClick={handleAddToCart}
          className="mt-6 px-4 py-2 rounded bg-blue-600 text-white text-sm disabled:opacity-50"
        >
          {product.stockQuantity <= 0
            ? "Out of stock"
            : adding
            ? "Adding..."
            : "Add to Cart"}
        </button>
      </div>
    </section>
  );
}

export default ProductDetail;
