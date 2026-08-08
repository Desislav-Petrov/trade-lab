import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const SESSION_KEY = 'trade-lab-session'

describe('axiosInstance request interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('sets Authorization from localStorage when no header is pre-set', async () => {
    const session = { accessToken: 'stored-token' }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))

    const { default: instance } = await import('./axiosInstance')
    // Use a custom adapter to capture the final config after all interceptors run
    let capturedAuth: string | undefined
    instance.defaults.adapter = async (config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    await instance.get('/test').catch(() => {})
    expect(capturedAuth).toBe('Bearer stored-token')
  })

  it('does NOT overwrite an Authorization header already set by the caller', async () => {
    const session = { accessToken: 'stale-old-token' }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))

    const { default: instance } = await import('./axiosInstance')
    let capturedAuth: string | undefined
    instance.defaults.adapter = async (config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    await instance.get('/test', {
      headers: { Authorization: 'Bearer fresh-bootstrap-token' },
    }).catch(() => {})

    expect(capturedAuth).toBe('Bearer fresh-bootstrap-token')
  })

  it('sends no Authorization header when localStorage is empty', async () => {
    const { default: instance } = await import('./axiosInstance')
    let capturedAuth: string | undefined
    instance.defaults.adapter = async (config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    await instance.get('/test').catch(() => {})
    expect(capturedAuth).toBeUndefined()
  })
})
