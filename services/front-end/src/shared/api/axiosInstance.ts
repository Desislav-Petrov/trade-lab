import axios from 'axios'

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

export default axiosInstance
