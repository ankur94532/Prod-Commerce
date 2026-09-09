// src/pages/Checkout.jsx
import React, { useEffect, useRef, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/authContextValue.js";
import { tokenizeCard, PaymentTokenizationError } from "../payments/processor";
import { getCart, clearCart } from "../api/cart";
import { createOrder, fetchCheckoutAttempt } from "../api/orders";
import { submitCheckout, inspectAttempt, startNewAttempt, cleanupPaidCart, withCheckoutLock } from "../checkout/attempt";

function Checkout() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [cart, setCart] = useState({ userId: null, items: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [cardNumber, setCardNumber] = useState("");
  const [cardExpiry, setCardExpiry] = useState("");
  const [cardCvc, setCardCvc] = useState("");

  const submitting = useRef(false);
  const [placing, setPlacing] = useState(false);
  const [paymentError, setPaymentError] = useState("");
  const [previousOrder, setPreviousOrder] = useState(null);

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
        const previous = await inspectAttempt(user.id, localStorage, fetchCheckoutAttempt);
        if (isMounted) setPreviousOrder(previous);
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

  if (previousOrder) {
    const terminal = ["PAID", "CANCELLED"].includes(previousOrder.status);
    return (
      <section className="max-w-4xl mx-auto px-4 py-8 space-y-4">
        <h2 className="text-2xl font-semibold">Previous checkout</h2>
        <p role="status">Order #{previousOrder.id}: {previousOrder.status}.</p>
        <p>{terminal ? "Review your cart before starting a new purchase. A paid order may still have items in your cart." : "Checkout is being recovered. Check its status before starting another purchase."}</p>
        {paymentError && <p role="alert">{paymentError}</p>}
        <Link to="/orders" className="block text-blue-600 underline">My Orders</Link>
        <Link to="/cart" className="block text-blue-600 underline">Review cart</Link>
        {previousOrder.status === "PAID" && <button className="block text-blue-600 underline" onClick={async () => {
          try {
            await withCheckoutLock(user.id, localStorage, () => cleanupPaidCart({
              userId: user.id, items, storage: localStorage, previousOrder, clearCart,
            }));
            navigate("/orders", { state: { checkoutNotice: "Order placed. Cart cleared." } });
          } catch { setPaymentError("Could not clear the purchased cart. Review your cart before retrying."); }
        }}>Retry cart cleanup</button>}
        <button className="px-4 py-2 rounded bg-blue-600 text-white" onClick={async () => {
          try {
            if (terminal) {
              await withCheckoutLock(user.id, localStorage,
                () => startNewAttempt(user.id, localStorage, previousOrder));
              setPreviousOrder(null);
              setPaymentError("");
            } else {
              setPreviousOrder(await inspectAttempt(user.id, localStorage, fetchCheckoutAttempt));
            }
          } catch { setPaymentError("Could not update checkout status. Please retry."); }
        }}>{terminal ? "Start a new checkout" : "Check order status"}</button>
      </section>
    );
  }

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

    if (submitting.current) return;
    submitting.current = true;
    let paymentToken;
    try {
      setPlacing(true);
      // The card is exchanged for a token in the browser and never sent to our API.
      paymentToken = await tokenizeCard({ number: cardNumber, expiry: cardExpiry, cvc: cardCvc });
    } catch (err) {
      setPaymentError(err instanceof PaymentTokenizationError ? err.message : "Could not verify the card.");
      setPlacing(false);
      submitting.current = false;
      return;
    }
    try {
      const submit = () => submitCheckout({
        userId: user.id, items, cartRevision: cart.revision, payment: { paymentToken },
        storage: localStorage, createOrder, clearCart, uuid: () => crypto.randomUUID(),
      });
      const { order, warning } = await withCheckoutLock(user.id, localStorage, submit);
      if (order.status !== "PAID") {
        setPreviousOrder(order);
        setPaymentError(warning);
        return;
      }
      navigate("/orders", { state: { checkoutNotice: warning || `Order #${order.id} placed.` } });
    } catch (err) {
      const backendMessage =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        "Failed to place order. Please try again.";
      setPaymentError(backendMessage);
    } finally {
      submitting.current = false;
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
              <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="cc-card-number">
                Card number
              </label>
              <input
                id="cc-card-number"
                type="text"
                value={cardNumber}
                onChange={(e) => setCardNumber(e.target.value)}
                placeholder="4242 4242 4242 4242"
                className="w-full border rounded px-3 py-2 text-sm"
              />
            </div>

            <div className="flex gap-3">
              <div className="flex-1">
                <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="cc-expiry">
                  Expiry
                </label>
                <input
                  id="cc-expiry"
                  type="text"
                  value={cardExpiry}
                  onChange={(e) => setCardExpiry(e.target.value)}
                  placeholder="12/28"
                  className="w-full border rounded px-3 py-2 text-sm"
                />
              </div>
              <div className="w-24">
                <label className="block text-xs font-medium text-slate-700 mb-1" htmlFor="cc-cvc">
                  CVC
                </label>
                <input
                  id="cc-cvc"
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
