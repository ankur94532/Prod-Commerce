// src/App.jsx
import React from "react";
import { Routes, Route } from "react-router-dom";
import Navbar from "./components/layout/Navbar.jsx";
import Footer from "./components/layout/Footer.jsx";

import Home from "./pages/Home.jsx";
import Products from "./pages/Products.jsx";
import ProductDetail from "./pages/ProductDetail.jsx";
import Cart from "./pages/Cart.jsx";
import Checkout from "./pages/Checkout.jsx";
import Login from "./pages/Login.jsx";
import Register from "./pages/Register.jsx";
import AdminDashboard from "./pages/AdminDashboard.jsx";
import SearchResultsPage from "./pages/SearchResultsPage";
import OrdersPage from "./pages/OrdersPage";
import AdminAnalyticsPage from "./pages/AdminAnalyticsPage.jsx";
import AdminProductsPage from "./pages/AdminProductsPage.jsx";
import AdminUsersPage from "./pages/AdminUsersPage.jsx";

function App() {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar />
      <main className="flex-1">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/products" element={<Products />} />
          <Route path="/products/:slug" element={<ProductDetail />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/checkout" element={<Checkout />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          <Route path="/admin" element={<AdminDashboard />} />
          <Route path="/admin/analytics" element={<AdminAnalyticsPage />} />
          <Route path="/admin/products" element={<AdminProductsPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />

          <Route path="/search" element={<SearchResultsPage />} />
          <Route path="/orders" element={<OrdersPage />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}

export default App;
