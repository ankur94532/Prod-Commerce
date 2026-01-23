// src/pages/Checkout.jsx
import React, { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { getCart, clearCart } from "../api/cart";
import { createOrder } from "../api/orders";

function Checkout() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [cart, setCart] = useState({ userId: null, items: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [cardNumber, setCardNumber] = useState("");
  const [cardExpiry, setCardExpiry] = useState("");
  const [cardCvc, setCardCvc] = useState("");

  const [placing, setPlacing] = useState(false);
  const [paymentError, setPaymentError] = useState("");

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
      } catch (e) {
        console.error(e);
        if (isMounted) setError("Failed to load cart.");
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    load();

    return () => {
      isMounted = false;
    };
  }, [user]);

  if (!user) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-slate-700 mb-4">
          You need to be logged in to checkout.
        </p>
        <button
          onClick={() => navigate("/login", { state: { from: "/checkout" } })}
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
        <p className="text-slate-600">Loading checkout...</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <p className="text-red-600 mb-4">{error}</p>
        <Link
          to="/cart"
          className="inline-block text-sm text-blue-600 underline"
        >
          Back to cart
        </Link>
      </section>
    );
  }

  const items = cart.items || [];
  const total = items.reduce(
    (sum, item) => sum + Number(item.price) * item.quantity,
    0
  );

  if (!items.length) {
    return (
      <section className="max-w-4xl mx-auto px-4 py-8">
        <h2 className="text-2xl font-semibold mb-2">Checkout</h2>
        <p className="text-slate-600 mb-4">
          Your cart is empty. Add some products before checking out.
        </p>
        <Link
          to="/products"
          className="inline-block text-sm text-blue-600 underline"
        >
          Browse products
        </Link>
      </section>
    );
  }

  const handlePlaceOrder = async (e) => {
    e.preventDefault();
    setPaymentError("");

    if (!cardNumber.trim() || !cardExpiry.trim() || !cardCvc.trim()) {
      setPaymentError("Please enter full card details.");
      return;
    }

    try {
      setPlacing(true);

      await createOrder({
        userId: user.id,
        items,
        payment: {
          cardNumber,
          cardExpiry,
          cardCvc,
        },
      });

      // clear cart backend + local
      await clearCart(user.id);
      setCart({ userId: user.id, items: [] });

      // go to orders page
      navigate("/orders");
    } catch (err) {
      console.error(err);
      const backendMessage =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        "Failed to place order. Please try again.";
      setPaymentError(backendMessage);
    } finally {
      setPlacing(false);
    }
  };

  return (
    <section className="max-w-4xl mx-auto px-4 py-8">
      <h2 className="text-2xl font-semibold mb-4">Checkout</h2>

      <div className="grid gap-8 md:grid-cols-[2fr,1.5fr]">
        {/* Left: Payment form */}
        <form onSubmit={handlePlaceOrder} className="space-y-4">
          <div>
            <h3 className="text-sm font-semibold mb-2">Payment details</h3>
            <p className="text-xs text-slate-500 mb-3">
              This uses a <strong>mock Stripe integration</strong>. Any card
              number works except those ending with <code>0000</code>, which
              simulate a <strong>card declined</strong> error.
            </p>
          </div>

          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Card number
              </label>
              <input
                type="text"
                value={cardNumber}
                onChange={(e) => setCardNumber(e.target.value)}
                placeholder="4242 4242 4242 4242"
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>

            <div className="flex gap-3">
              <div className="flex-1">
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  Expiry
                </label>
                <input
                  type="text"
                  value={cardExpiry}
                  onChange={(e) => setCardExpiry(e.target.value)}
                  placeholder="12/28"
                  className="w-full border rounded px-3 py-2 text-sm"
                />
              </div>
              <div className="w-24">
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  CVC
                </label>
                <input
                  type="password"
                  value={cardCvc}
                  onChange={(e) => setCardCvc(e.target.value)}
                  placeholder="123"
                  className="w-full border rounded px-3 py-2 text-sm"
                />
              </div>
            </div>
          </div>

          {paymentError && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded px-3 py-2">
              {paymentError}
            </p>
          )}

          <button
            type="submit"
            disabled={placing}
            className="mt-2 px-4 py-2 rounded bg-green-600 text-white text-sm disabled:opacity-60"
          >
            {placing ? "Processing payment..." : `Pay INR ${total} and place order`}
          </button>
        </form>

        {/* Right: Order summary */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h3 className="text-sm font-semibold mb-3">Order summary</h3>
          <div className="space-y-2 mb-4 max-h-60 overflow-y-auto pr-1">
            {items.map((item) => (
              <div key={item.productId} className="flex justify-between text-sm">
                <div className="flex-1 mr-2">
                  <p className="text-slate-900 line-clamp-1">{item.name}</p>
                  <p className="text-xs text-slate-500">
                    {item.currency} {item.price} × {item.quantity}
                  </p>
                </div>
                <p className="text-sm font-medium text-slate-900">
                  {item.currency} {Number(item.price) * item.quantity}
                </p>
              </div>
            ))}
          </div>

          <div className="border-t pt-3 flex justify-between text-sm font-semibold">
            <span>Total</span>
            {items.length > 0 && (
              <span>
                {items[0].currency} {total}
              </span>
            )}
          </div>

          <button
            type="button"
            onClick={() => navigate("/cart")}
            className="mt-3 text-xs text-blue-600 underline"
          >
            Back to cart
          </button>
        </div>
      </div>
    </section>
  );
}

export default Checkout;