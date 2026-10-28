// Thin fetch wrapper for the Spring Boot API.
// - Sends the session cookie (same origin) and echoes the XSRF-TOKEN cookie as X-XSRF-TOKEN on writes.
// - Normalises errors to ApiError { status, code, message, fields }.

export class ApiError extends Error {
  constructor(status, code, message, fields) {
    super(message || "Request failed");
    this.status = status;
    this.code = code || "error";
    this.fields = fields || {};
  }
}

function readCookie(name) {
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : null;
}

export async function api(path, { method = "GET", body, signal, raw = false } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET" && method !== "HEAD") {
    const token = readCookie("XSRF-TOKEN");
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin",
      signal,
    });
  } catch (e) {
    if (e.name === "AbortError") throw e;
    throw new ApiError(0, "network", "Could not reach the server. Check your connection and try again.");
  }
  if (raw && res.ok) return res;
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    const err = data && data.error ? data.error : {};
    if (res.status === 401 && !path.startsWith("/api/auth/")) {
      window.dispatchEvent(new CustomEvent("hc:unauthorized"));
    }
    const fallback = res.status === 403 ? "You do not have permission to do that." : `Request failed (${res.status}).`;
    throw new ApiError(res.status, err.code, err.message || fallback, err.fields);
  }
  return data;
}

export const get = (path, opts) => api(path, { ...opts, method: "GET" });
export const post = (path, body, opts) => api(path, { ...opts, method: "POST", body: body ?? {} });
export const put = (path, body, opts) => api(path, { ...opts, method: "PUT", body });
export const patch = (path, body, opts) => api(path, { ...opts, method: "PATCH", body });
export const del = (path, opts) => api(path, { ...opts, method: "DELETE" });

export function qs(params) {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "" && v !== false) sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? "?" + s : "";
}
