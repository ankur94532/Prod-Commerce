import React from "react";
import { reportClientEvent } from "../observability";

export default class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    reportClientEvent("react_error_boundary", {
      name: error?.name || "Error",
      message: error?.message || "Unknown render error",
      componentStack: info?.componentStack || "",
    });
  }

  render() {
    if (this.state.failed) {
      return (
        <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
          <section role="alert" className="max-w-lg rounded bg-white p-6 shadow">
            <h1 className="text-xl font-semibold">This page could not be displayed</h1>
            <p className="mt-2 text-slate-600">Your checkout receipt remains saved. Reload the page to try again.</p>
            <button className="mt-4 rounded bg-blue-600 px-4 py-2 text-white" onClick={() => globalThis.location.reload()}>
              Reload
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
