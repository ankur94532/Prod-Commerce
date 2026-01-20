// src/pages/OrdersPage.jsx
import React, { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { fetchOrdersForUser } from "../api/orders";
import { useNavigate } from "react-router-dom";

function OrdersPage() {
  const { user, loading } = useAuth();
  const [orders, setOrders] = useState([]);
  const [state, setState] = useState({
    loading: true,
    error: null,
  });
  const navigate = useNavigate();

  useEffect(() => {
    if (loading) return;

    if (!user) {
      navigate("/login?next=/orders");
      return;
    }

    setState({ loading: true, error: null });

    fetchOrdersForUser(user.id)
      .then((data) => {
        setOrders(Array.isArray(data) ? data : []);
        setState({ loading: false, error: null });
      })
      .catch((err) => {
        console.error(err);
        setState({
          loading: false,
          error: "Failed to load orders.",
        });
      });
  }, [loading, user, navigate]);

  if (loading || state.loading) {
    return (
      <div className="max-w-4xl mx-auto py-6 px-4">
        <p>Loading your orders...</p>
      </div>
    );
  }

  if (state.error) {
    return (
      <div className="max-w-4xl mx-auto py-6 px-4">
        <p className="text-red-600">{state.error}</p>
      </div>
    );
  }

  if (!orders.length) {
    return (
      <div className="max-w-4xl mx-auto py-6 px-4">
        <h1 className="text-2xl font-semibold mb-4">My Orders</h1>
        <p className="text-slate-600">You don’t have any orders yet.</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto py-6 px-4">
      <h1 className="text-2xl font-semibold mb-4">My Orders</h1>

      <div className="space-y-4">
        {orders.map((order) => (
          <div
            key={order.id}
            className="border rounded-lg p-4 bg-white shadow-sm"
          >
            <div className="flex justify-between items-center mb-2">
              <div className="font-medium">Order #{order.id}</div>
              <div className="text-sm text-slate-500">
                {order.createdAt
                  ? new Date(order.createdAt).toLocaleString()
                  : ""}
              </div>
            </div>

            <div className="text-sm text-slate-600 mb-2">
              Status: <span className="font-medium">{order.status}</span>
            </div>

            <div className="divide-y">
              {order.items?.map((item) => (
                <div
                  key={item.id}
                  className="flex justify-between py-1 text-sm"
                >
                  <div>
                    <div>{item.productName}</div>
                    <div className="text-slate-500">
                      Qty: {item.quantity} × ₹{item.unitPrice}
                    </div>
                  </div>
                  <div className="font-medium">₹{item.lineTotal}</div>
                </div>
              ))}
            </div>

            <div className="mt-2 text-right font-semibold">
              Total: ₹{order.totalAmount}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default OrdersPage;
