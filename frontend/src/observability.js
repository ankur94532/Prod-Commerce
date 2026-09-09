const MAX_TEXT = 300;

export function safeClientEvent(type, details = {}, location = globalThis.location) {
  const clean = Object.fromEntries(Object.entries(details).map(([key, value]) => [
    key,
    typeof value === "string" ? value.slice(0, MAX_TEXT) : value,
  ]));
  return {
    type,
    path: location?.pathname || "",
    timestamp: new Date().toISOString(),
    ...clean,
  };
}

export function reportClientEvent(type, details = {}) {
  const endpoint = import.meta.env?.VITE_RUM_ENDPOINT;
  if (!endpoint) return false;
  const body = JSON.stringify(safeClientEvent(type, details));
  if (globalThis.navigator?.sendBeacon?.(endpoint, new Blob([body], { type: "application/json" }))) {
    return true;
  }
  globalThis.fetch?.(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
    credentials: "omit",
  }).catch(() => {});
  return true;
}

export function installClientObservability(target = globalThis) {
  target.addEventListener?.("error", event => reportClientEvent("client_error", {
    name: event.error?.name || "Error",
    message: event.error?.message || event.message || "Unknown client error",
  }));
  target.addEventListener?.("unhandledrejection", event => reportClientEvent("unhandled_rejection", {
    name: event.reason?.name || "Error",
    message: event.reason?.message || String(event.reason || "Unknown rejection"),
  }));

  if (!target.PerformanceObserver) return;
  if (target.PerformanceObserver.supportedEntryTypes?.includes("largest-contentful-paint")) {
    new target.PerformanceObserver(list => {
      const entry = list.getEntries().at(-1);
      if (entry) reportClientEvent("web_vital", { metric: "LCP", value: Math.round(entry.startTime) });
    }).observe({ type: "largest-contentful-paint", buffered: true });
  }
  if (target.PerformanceObserver.supportedEntryTypes?.includes("layout-shift")) {
    let cumulative = 0;
    new target.PerformanceObserver(list => {
      for (const entry of list.getEntries()) if (!entry.hadRecentInput) cumulative += entry.value;
      reportClientEvent("web_vital", { metric: "CLS", value: Number(cumulative.toFixed(4)) });
    }).observe({ type: "layout-shift", buffered: true });
  }
}
