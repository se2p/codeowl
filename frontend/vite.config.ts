import { defineConfig, loadEnv, type ProxyOptions } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");

  // Dev-only proxy (ignored in prod build)
  const DEV_PROXY_TARGET = env.VITE_DEV_PROXY_TARGET;              // e.g. https://scratch.fim.uni-passau.de
  const DEV_PROXY_REWRITE_TO = env.VITE_DEV_PROXY_REWRITE_TO || ""; // "" or "/api"

  // Local dev: forward same-origin "/api" calls (api.ts default base) to the
  // Dockerized backend so `npm run dev` gets HMR while hitting real data.
  // Override the target with VITE_DEV_BACKEND (default: local Docker backend).
  const LOCAL_BACKEND = env.VITE_DEV_BACKEND || "http://localhost:8080";

  const proxy: Record<string, ProxyOptions> = {
    "/api": {
      target: LOCAL_BACKEND,
      changeOrigin: true,
      secure: false,
    },
  };

  // Optional remote backend proxy (only when explicitly configured)
  if (DEV_PROXY_TARGET) {
    proxy["/codeowl-api"] = {
      target: DEV_PROXY_TARGET,
      changeOrigin: true,
      secure: true, // set to false only if cert isn't trusted locally
      rewrite: (p: string) =>
          DEV_PROXY_REWRITE_TO
              ? p.replace(/^\/codeowl-api/, DEV_PROXY_REWRITE_TO)
              : p,
    };
  }

  return {
    // Base path: configurable via VITE_BASE_PATH env var
    // Defaults to "/" for local, set to "/codeowl/" on server
    base: env.VITE_BASE_PATH || "/",

    plugins: [react()],

    server: {
      host: "0.0.0.0",
      port: 5173,
      watch: { usePolling: true },
      proxy, // works in dev only
    },

    preview: {
      host: "0.0.0.0",
      port: 5173,
    },

    build: {
      outDir: "dist",
      sourcemap: false,
    },
  };
});
