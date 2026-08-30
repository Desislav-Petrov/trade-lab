import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'
import type { IncomingMessage, ClientRequest } from 'http'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  resolve: {
    alias: {
      // Matches the @/* path alias in tsconfig.json.
      // Required so shadcn component imports (e.g. "@/shared/lib/utils") resolve.
      '@': resolve(__dirname, 'src'),
    },
  },
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
          // Suppress EPIPE / ECONNRESET errors that occur when the backend closes
          // a WebSocket connection while Vite's proxy is still writing to the socket.
          // These are expected during WS reconnects and do not affect functionality.
          proxy.on('error', (err: NodeJS.ErrnoException, _req, res) => {
            if (err.code === 'EPIPE' || err.code === 'ECONNRESET') return
            // For any other error, let the response know if it's still writable
            if (res && 'writeHead' in res && typeof (res as import('http').ServerResponse).writeHead === 'function') {
              const serverRes = res as import('http').ServerResponse
              if (!serverRes.headersSent) {
                serverRes.writeHead(502)
                serverRes.end('Bad Gateway')
              }
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
