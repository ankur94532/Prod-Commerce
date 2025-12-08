// src/components/layout/Navbar.jsx
import React from "react";
import { Link, NavLink } from "react-router-dom";

function Navbar() {
  const linkBase =
    "text-sm px-2 py-1 rounded hover:text-blue-600 hover:bg-slate-100";

  return (
    <header className="bg-white shadow-sm">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <Link to="/" className="text-xl font-bold text-slate-900">
          GoCommerce
        </Link>

        <nav className="flex items-center gap-3">
          <NavLink
            to="/products"
            className={({ isActive }) =>
              `${linkBase} ${
                isActive ? "text-blue-600 font-semibold" : "text-slate-700"
              }`
            }
          >
            Shop
          </NavLink>
          <NavLink
            to="/cart"
            className={({ isActive }) =>
              `${linkBase} ${
                isActive ? "text-blue-600 font-semibold" : "text-slate-700"
              }`
            }
          >
            Cart
          </NavLink>
          <NavLink
            to="/orders"
            className={({ isActive }) =>
              `${linkBase} ${
                isActive ? "text-blue-600 font-semibold" : "text-slate-700"
              }`
            }
          >
            Orders
          </NavLink>
          <NavLink
            to="/admin"
            className={({ isActive }) =>
              `${linkBase} ${
                isActive ? "text-blue-600 font-semibold" : "text-slate-700"
              }`
            }
          >
            Admin
          </NavLink>
          <NavLink
            to="/login"
            className={({ isActive }) =>
              `${linkBase} ${
                isActive ? "text-blue-600 font-semibold" : "text-slate-700"
              }`
            }
          >
            Login
          </NavLink>
        </nav>
      </div>
    </header>
  );
}

export default Navbar;
