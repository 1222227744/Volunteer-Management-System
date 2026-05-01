import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [vue()],
    server: {
      port: Number(env.VITE_DEV_PORT || 5173),
      host: env.VITE_DEV_HOST || "0.0.0.0",
      proxy: {
        "/api": {
          target: env.VITE_BACKEND_ORIGIN || "http://127.0.0.1:8080",
          changeOrigin: true
        }
      }
    },
    preview: {
      port: Number(env.VITE_PREVIEW_PORT || 4173),
      host: env.VITE_PREVIEW_HOST || "0.0.0.0"
    }
  };
});
