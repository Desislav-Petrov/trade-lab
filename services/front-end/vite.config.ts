import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  server: {
    proxy: {
      // All /api/* XHR/fetch calls are proxied to the backend.
      // The /api prefix is stripped so requests reach the correct
      // backend routes at /v1/... rather than /api/v1/...
      // Note: window.location.href navigations (e.g. OAuth2 redirects) bypass
      // this proxy — those must use an absolute URL pointing to localhost:8080.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        ws: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
  },
})
