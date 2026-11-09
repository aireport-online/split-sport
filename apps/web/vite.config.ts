import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// In development the API runs on its own port; in production the API server serves this build.
const apiTarget = process.env.SPLIT_API_URL ?? "http://localhost:8787";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": apiTarget },
  },
  build: { outDir: "dist", sourcemap: true },
});
