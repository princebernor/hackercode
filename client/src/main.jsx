import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "@fontsource/poppins/300.css";
import "@fontsource/poppins/400.css";
import "@fontsource/poppins/500.css";
import "@fontsource/poppins/600.css";
import "@fontsource/jetbrains-mono/400.css";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/components.css";
import "./styles/layout.css";
import "./styles/auth.css";
import "./styles/pages.css";
import App from "./App.jsx";
import { AuthProvider } from "./api/auth.jsx";
import { ToastProvider } from "./components/Toasts.jsx";

// After a redeploy, an open tab may request code-split chunks that no longer exist. Reload once to pick up the new build.
window.addEventListener("vite:preloadError", (event) => {
  let reloaded = false;
  try { reloaded = sessionStorage.getItem("hc-chunk-reload") === "1"; sessionStorage.setItem("hc-chunk-reload", "1"); } catch { /* storage unavailable */ }
  if (!reloaded) { event.preventDefault(); window.location.reload(); }
});
window.addEventListener("load", () => { try { sessionStorage.removeItem("hc-chunk-reload"); } catch { /* storage unavailable */ } });

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
