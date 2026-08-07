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
// Only sets the header when the caller has NOT already supplied one —
// this prevents a stale/expired token in localStorage from overwriting
// an explicit token passed by the caller (e.g. the OAuth2 callback
// bootstrap fetch in AuthCallbackHandler).
axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['Authorization']) {
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
  }
  return config
})

export default axiosInstance
