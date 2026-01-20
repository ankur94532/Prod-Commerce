import React, { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext.jsx";

function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState("");

  const isAdmin = user?.role === "ADMIN";

  const linkBase =
    "text-sm px-2 py-1 rounded hover:text-blue-600 hover:bg-slate-100";

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    const trimmed = searchTerm.trim();
    if (!trimmed) return;
    navigate(`/search?q=${encodeURIComponent(trimmed)}`);
  };

  return (
    <header className="bg-white shadow-sm">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between gap-4">
        {/* Brand */}
        <Link to="/" className="text-xl font-bold text-slate-900">
          GoCommerce
        </Link>

        {/* Middle: nav links + search */}
        <div className="flex items-center gap-4 flex-1 justify-center">
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

            {isAdmin && (
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
            )}
          </nav>

          {/* Search bar */}
          <form
            onSubmit={handleSearchSubmit}
            className="flex items-center gap-2 max-w-xs w-full"
          >
            <input
              type="text"
              placeholder="Search products…"
              className="w-full border border-slate-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </form>
        </div>

        {/* Right: auth */}
        <div className="flex items-center gap-3">
          {user ? (
            <>
              <span className="text-sm text-slate-700">
                Hi, {user.fullName.split(" ")[0]}
              </span>
              <button
                onClick={logout}
                className="text-sm px-2 py-1 rounded bg-slate-100 text-slate-700 hover:bg-slate-200"
              >
                Logout
              </button>
            </>
          ) : (
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
          )}
        </div>
      </div>
    </header>
  );
}

export default Navbar;
