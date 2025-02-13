import { fileURLToPath, URL } from "node:url";
import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import tsconfigPaths from "vite-tsconfig-paths";
import commonjs from "vite-plugin-commonjs";
import { execSync } from "child_process";

export default ({ mode }) => {
  process.env = { ...process.env, ...loadEnv(mode, process.cwd()) };

  const DELTAFI_DOMAIN = process.env.DELTAFI_DOMAIN || "dev.deltafi.org";

  return defineConfig({
    plugins: [vue(), tsconfigPaths(), commonjs()],
    server: {
      host: "localhost",
      port: 8080,
      proxy: {
        "/api": {
          target: `https://${DELTAFI_DOMAIN}`,
          changeOrigin: true,
          secure: false,
          configure: (proxy, options) => {
            proxy.on("proxyReq", (proxyReq, req, res) => {
              if (req.url === "/api/v2/local-git-branch" && mode === "development") {
                const branch = execSync("git rev-parse --abbrev-ref HEAD").toString().trim();
                res.setHeader("Content-Type", "application/json");
                res.end(JSON.stringify({ branch }));
              }
            });
          },
        },
        "/visualization": {
          target: `https://${DELTAFI_DOMAIN}`,
          changeOrigin: true,
          secure: false
        },
        "/deltafile/ingress": {
          target: `https://ingress.${DELTAFI_DOMAIN}`,
          changeOrigin: true,
          secure: false
        },
        "/deltafile/annotate": {
          target: `https://${DELTAFI_DOMAIN}`,
          changeOrigin: true,
          secure: false
        },
      },
    },
    esbuild: {
      loader: "ts",
    },
    build: {
      rollupOptions: {
        plugins: [],
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          silenceDeprecations: ["mixed-decls", "color-functions", "abs-percent", "global-builtin", "import"],
        },
      },
    },
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
      extensions: [".mjs", ".js", ".ts", ".jsx", ".tsx", ".json", ".vue"],
    },
  });
};
