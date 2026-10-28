import { createContext, useCallback, useContext, useMemo, useState } from "react";
import Icon from "./Icon.jsx";

const ToastContext = createContext(null);
let nextId = 1;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback((kind, message) => {
    const id = nextId++;
    setToasts((t) => [...t.slice(-3), { id, kind, message }]);
    setTimeout(() => dismiss(id), kind === "error" ? 7000 : 4000);
  }, [dismiss]);
  const api = useMemo(() => ({
    success: (m) => push("success", m),
    error: (m) => push("error", typeof m === "string" ? m : m?.message || "Something went wrong."),
    info: (m) => push("info", m),
  }), [push]);
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" aria-live="polite" aria-atomic="false">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`} role={t.kind === "error" ? "alert" : "status"}>
            <Icon name={t.kind === "error" ? "alert" : t.kind === "success" ? "check" : "info"} />
            <span>{t.message}</span>
            <button onClick={() => dismiss(t.id)} aria-label="Dismiss notification"><Icon name="x" size={16} /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
