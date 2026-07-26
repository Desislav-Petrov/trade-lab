import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { SESSION_STORAGE_KEY } from '../../domains/user/types/user'

// axiosInstance is a module singleton — we need to import it fresh for each test
// to pick up different localStorage states. Since Vitest doesn't hot-reload
// modules per test, we mock localStorage before the import.
const SESSION_KEY = SESSION_STORAGE_KEY

describe('axiosInstance', () => {
  it('axiosInstance - timeout - is configured to 10000ms', async () => {
    const { default: axiosInstance } = await import('./axiosInstance')
    expect(axiosInstance.defaults.timeout).toBe(10_000)
  })

  it('axiosInstance - baseURL - is /api', async () => {
    const { default: axiosInstance } = await import('./axiosInstance')
    expect(axiosInstance.defaults.baseURL).toBe('/api')
  })

  it('axiosInstance - Content-Type - is application/json', async () => {
    const { default: axiosInstance } = await import('./axiosInstance')
    expect(axiosInstance.defaults.headers['Content-Type']).toBe('application/json')
  })

  describe('Bearer token interceptor', () => {
    beforeEach(() => {
      localStorage.clear()
    })

    afterEach(() => {
      localStorage.clear()
    })

    it('axiosInstance - session with accessToken in localStorage - attaches Authorization header', async () => {
      const mockSession = { accessToken: 'test-jwt-token' }
      localStorage.setItem(SESSION_KEY, JSON.stringify(mockSession))

      const { default: axiosInstance } = await import('./axiosInstance')

      // Trigger the interceptor by creating a config and running it through
      const interceptor = axiosInstance.interceptors.request as unknown as {
        handlers: Array<{ fulfilled: (config: unknown) => unknown }>
      }
      const lastHandler = interceptor.handlers[interceptor.handlers.length - 1]

      const mockConfig = {
        headers: {} as Record<string, string>,
      }
      lastHandler.fulfilled(mockConfig)

      expect(mockConfig.headers['Authorization']).toBe('Bearer test-jwt-token')
    })

    it('axiosInstance - no session in localStorage - does not attach Authorization header', async () => {
      const { default: axiosInstance } = await import('./axiosInstance')

      const interceptor = axiosInstance.interceptors.request as unknown as {
        handlers: Array<{ fulfilled: (config: unknown) => unknown }>
      }
      const lastHandler = interceptor.handlers[interceptor.handlers.length - 1]

      const mockConfig = {
        headers: {} as Record<string, string>,
      }
      lastHandler.fulfilled(mockConfig)

      expect(mockConfig.headers['Authorization']).toBeUndefined()
    })
  })
})
