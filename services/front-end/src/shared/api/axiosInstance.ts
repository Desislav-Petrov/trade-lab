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
        config.headers.set('Authorization', `Bearer ${session.accessToken}`)
      }
    }
  } catch {
    // localStorage unavailable or session malformed — proceed without token
  }
  return config
})

export default axiosInstance
