import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import type { IncomingMessage, ClientRequest } from 'http'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  server: {
    proxy: {
      // All /api/* XHR/fetch calls are proxied to the backend.
      // Note: window.location.href navigations (e.g. OAuth2 redirects) bypass
      // this proxy — those must use an absolute URL pointing to localhost:8080.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
        // Explicitly forward the Authorization header.
        // Some versions of http-proxy (used by Vite internally) strip
        // the Authorization header when changeOrigin is true.
        // The configure hook runs once on proxy creation; proxyReq runs
        // on every request and copies the header from the original request.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq: ClientRequest, req: IncomingMessage) => {
            const auth = (req as IncomingMessage & { headers: Record<string, string | string[] | undefined> }).headers['authorization']
            if (auth) {
              const authValue = Array.isArray(auth) ? auth[0] : auth
              proxyReq.setHeader('Authorization', authValue)
            }
          })
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
  },
})
