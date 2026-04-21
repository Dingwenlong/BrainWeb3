import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // The Brain3D heatmap is route-scoped and lazy-loaded, so its Three.js core chunk is
    // intentionally larger than the app's normal page bundles.
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/three/examples/jsm')) {
            return 'vendor-three-extras'
          }
          if (id.includes('node_modules/three')) {
            return 'vendor-three-core'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router')) {
            return 'vendor-vue'
          }
        },
      },
    },
  },
})
