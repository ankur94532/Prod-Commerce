// src/App.jsx
import React from "react";

function App() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100">
      <div className="bg-white shadow-md rounded-xl p-8 max-w-md w-full">
        <h1 className="text-2xl font-bold mb-2 text-slate-900">
          GoCommerce (Java Edition)
        </h1>
        <p className="text-slate-600 mb-4">
          Frontend skeleton is up and running. Next: routing, layout, and
          connecting to our Spring Boot backend.
        </p>
        <p className="text-sm text-slate-500">
          If you see this, Tailwind + React + Vite are working correctly.
        </p>
      </div>
    </div>
  );
}

export default App;
