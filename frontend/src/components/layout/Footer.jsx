// src/components/layout/Footer.jsx
import React from "react";

function Footer() {
  return (
    <footer className="border-t bg-white">
      <div className="max-w-6xl mx-auto px-4 py-4 text-xs text-slate-500 flex justify-between">
        <span>© {new Date().getFullYear()} GoCommerce</span>
        <span>Built with React & Spring Boot</span>
      </div>
    </footer>
  );
}

export default Footer;