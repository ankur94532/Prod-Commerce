// src/pages/Cart.jsx
import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { getCart } from "../api/cart";
import ProductImage from "../components/ProductImage";

function Cart() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [cart, setCart] = useState({ userId: null, items: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [orderError, setOrderError] = useState("");

  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }

    let isMounted = true;

    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const c = await getCart(user.id);
        if (isMounted) {
          setCart(c || { userId: user.id, items: [] });
        }
      } catch (err) {
        console.error(err);
        if (isMounted) setError("Failed to load cart.");
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    load();

    return () => {
      isMounted = false;
    };
  }, [user, navigate]);

  if (!user) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-slate-700 mb-4">
          You need to be logged in to view your cart.
        </p>
        <button
          onClick={() => navigate("/login", { state: { from: "/cart" } })}
          className="px-4 py-2 rounded bg-blue-600 text-white text-sm"
        >
          Go to Login
        </button>
      </section>
    );
  }

  if (loading) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-slate-600">Loading your cart...</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-red-600">{error}</p>
      </section>
    );
  }

  const items = cart.items || [];
  const total = items.reduce(
    (sum, item) => sum + Number(item.price) * item.quantity,
    0
  );

  const handleProceedToCheckout = () => {
    setOrderError("");
    if (!items.length) {
      setOrderError("Your cart is empty.");
      return;
    }
    navigate("/checkout");
  };

  return (
    <section className="max-w-4xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">Your Cart</h2>

      {items.length === 0 && (
        <p className="text-slate-500">Your cart is empty.</p>
      )}

      {items.length > 0 && (
        <>
          <div className="space-y-3 mb-6">
            {items.map((item) => (
              <div
                key={item.productId}
                className="flex gap-3 bg-white rounded-lg shadow-sm p-3"
              >
                <div className="w-20 h-20 bg-slate-100 rounded overflow-hidden flex items-center justify-center">
                  <ProductImage
                    src={item.imageUrl}
                    alt={item.name}
                    className="w-full h-full object-cover"
                    placeholderClassName="text-[10px]"
                  />
                </div>
                <div className="flex-1">
                  <h3 className="text-sm font-medium text-slate-900">
                    {item.name}
                  </h3>
                  <p className="text-xs text-slate-500 mb-1">
                    {item.currency} {item.price} × {item.quantity}
                  </p>
                  <p className="text-sm font-semibold text-slate-900">
                    {item.currency} {Number(item.price) * item.quantity}
                  </p>
                </div>
              </div>
            ))}
          </div>

          <div className="flex items-center justify-between border-t pt-4">
            <p className="text-sm font-semibold">
              Total:{" "}
              {items.length > 0 && (
                <>
                  {items[0].currency} {total}
                </>
              )}
            </p>
            <button
              className="px-4 py-2 rounded bg-green-600 text-white text-sm disabled:opacity-60"
              onClick={handleProceedToCheckout}
              disabled={items.length === 0}
            >
              Proceed to Checkout
            </button>
          </div>

          {orderError && (
            <p className="mt-3 text-sm text-red-600">{orderError}</p>
          )}
        </>
      )}
    </section>
  );
}

export default Cart;
