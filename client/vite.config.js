import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// The production build is written into the Spring Boot jar's static resources.
// In development, `npm run dev` proxies API and OAuth calls to the Spring Boot server on :8080.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: fileURLToPath(new URL("../target/classes/static", import.meta.url)),
    emptyOutDir: true,
    chunkSizeWarningLimit: 4000,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/oauth2": "http://localhost:8080",
      "/login/oauth2": "http://localhost:8080",
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.js"],
  },
});
