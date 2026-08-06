import axios from 'axios'
import { SESSION_STORAGE_KEY } from '../../domains/user/types/user'
import type { Session } from '../../domains/user/types/user'

// A 10-second timeout ensures that a hanging or unreachable backend request
// fails with a network error rather than waiting indefinitely. Without this,
// TanStack Query stays in status:'pending' forever and the loading spinner
// never clears — the UI appears frozen (bug #62).
const axiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach Bearer token from localStorage session on every request.
// No-op if no session exists or token is absent.
axiosInstance.interceptors.request.use((config) => {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    if (raw) {
      const session = JSON.parse(raw) as Session
      if (session.accessToken) {
        config.headers['Authorization'] = `Bearer ${session.accessToken}`
      }
    }
  } catch {
    // localStorage unavailable or session malformed — proceed without token
  }

  // DEBUG logging — helps diagnose auth issues in the browser console
  console.debug(
    `[axios] --> ${config.method?.toUpperCase()} ${config.baseURL ?? ''}${config.url ?? ''}`,
    '\n  Authorization:', (config.headers['Authorization'] as string | undefined)
      ? `Bearer ${String(config.headers['Authorization']).slice(7, 17)}…`
      : 'NONE',
  )

  return config
})

// Log every response (status + url) so failures are visible without DevTools Network tab
axiosInstance.interceptors.response.use(
  (response) => {
    console.debug(
      `[axios] <-- ${response.status} ${response.config.method?.toUpperCase()} ${
        response.config.baseURL ?? ''
      }${response.config.url ?? ''}`,
    )
    return response
  },
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      console.error(
        `[axios] <-- ${error.response?.status ?? 'ERR'} ${
          error.config?.method?.toUpperCase() ?? '?'
        } ${error.config?.baseURL ?? ''}${error.config?.url ?? ''}`,
        '\n  response body:', error.response?.data,
      )
    }
    return Promise.reject(error)
  },
)

export default axiosInstance
