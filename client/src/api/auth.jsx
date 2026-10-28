import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { get, post, put } from "./client.js";

const AuthContext = createContext(null);

/** Applies appearance and accessibility preferences to <html> so CSS tokens can react to them. */
export function applyPreferences(prefs) {
  const root = document.documentElement;
  const theme = prefs?.appearance?.theme || "system";
  if (theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", theme);
  const motion = prefs?.appearance?.reducedMotion || "system";
  if (motion === "system") root.removeAttribute("data-motion");
  else root.setAttribute("data-motion", motion);
  root.setAttribute("data-density", prefs?.appearance?.dashboardDensity || "comfortable");
  const a = prefs?.accessibility || {};
  root.setAttribute("data-font-scale", a.fontScale || "100");
  root.setAttribute("data-contrast", a.highContrast ? "high" : "normal");
  root.setAttribute("data-underline-links", a.underlineLinks ? "true" : "false");
  root.setAttribute("data-focus", a.alwaysShowFocus ? "always" : "auto");
}

export function prefersReducedMotion(prefs) {
  const m = prefs?.appearance?.reducedMotion || "system";
  if (m === "reduce") return true;
  if (m === "full") return false;
  return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [prefs, setPrefs] = useState(null);
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const [me, cfg] = await Promise.all([get("/api/auth/me"), get("/api/auth/config")]);
      setUser(me.user || null);
      setPrefs(me.preferences || null);
      setConfig(cfg);
      return me.user || null;
    } catch {
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => { applyPreferences(prefs); }, [prefs]);
  useEffect(() => {
    const onUnauthorized = () => { setUser(null); setPrefs(null); };
    window.addEventListener("hc:unauthorized", onUnauthorized);
    return () => window.removeEventListener("hc:unauthorized", onUnauthorized);
  }, []);

  const signIn = useCallback(async (email, password, rememberMe) => {
    const res = await post("/api/auth/login", { email, password, rememberMe });
    setUser(res.user);
    setPrefs(res.preferences);
    return res.user;
  }, []);

  const signUp = useCallback(async (payload) => {
    const res = await post("/api/auth/signup", payload);
    setUser(res.user);
    await refresh();
    return res;
  }, [refresh]);

  const signOut = useCallback(async () => {
    try { await post("/api/auth/logout"); } catch { /* the session may already be gone */ }
    setUser(null);
    setPrefs(null);
    applyPreferences(null);
  }, []);

  const savePrefs = useCallback(async (next) => {
    const saved = await put("/api/account/preferences", next);
    setPrefs(saved);
    return saved;
  }, []);

  const value = useMemo(() => ({
    user, prefs, config, loading, refresh, signIn, signUp, signOut, setUser, setPrefs, savePrefs,
    isStaff: user?.role === "ADMIN" || user?.role === "MODERATOR",
    isAdmin: user?.role === "ADMIN",
  }), [user, prefs, config, loading, refresh, signIn, signUp, signOut, savePrefs]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
