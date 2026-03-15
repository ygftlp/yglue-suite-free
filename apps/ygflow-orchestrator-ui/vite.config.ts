import { defineConfig } from "vite"
import vue from "@vitejs/plugin-vue"

export default defineConfig(() => {
  const apiTarget = "http://localhost:8091"

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    optimizeDeps: {
      include: [
        "@codemirror/view",
        "@codemirror/state",
        "@codemirror/commands",
        "@codemirror/autocomplete",
      ],
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes("node_modules")) {
              return
            }

            if (id.includes("@codemirror")) {
              return "codemirror"
            }

            if (id.includes("@vue-flow")) {
              return "vue-flow"
            }

            if (id.includes("vue-router")) {
              return "vue-router"
            }

            if (id.includes("lucide-vue-next")) {
              return "icons"
            }

            if (id.includes("/vue/")) {
              return "vue-core"
            }
          },
        },
      },
    },
  }
})
