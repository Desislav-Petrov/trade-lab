import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'

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
    // Intercept the outgoing request config
    let capturedAuth: string | undefined
    instance.interceptors.request.use((config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      // Abort — we only care about the headers, not the actual HTTP call
      return Promise.reject(new axios.Cancel('test abort'))
    })

    await instance.get('/test').catch(() => {})
    expect(capturedAuth).toBe('Bearer stored-token')
  })

  it('does NOT overwrite an Authorization header already set by the caller', async () => {
    const session = { accessToken: 'stale-old-token' }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))

    const { default: instance } = await import('./axiosInstance')
    let capturedAuth: string | undefined
    instance.interceptors.request.use((config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      return Promise.reject(new axios.Cancel('test abort'))
    })

    await instance.get('/test', {
      headers: { Authorization: 'Bearer fresh-bootstrap-token' },
    }).catch(() => {})

    expect(capturedAuth).toBe('Bearer fresh-bootstrap-token')
  })

  it('sends no Authorization header when localStorage is empty', async () => {
    const { default: instance } = await import('./axiosInstance')
    let capturedAuth: string | undefined
    instance.interceptors.request.use((config) => {
      capturedAuth = config.headers['Authorization'] as string | undefined
      return Promise.reject(new axios.Cancel('test abort'))
    })

    await instance.get('/test').catch(() => {})
    expect(capturedAuth).toBeUndefined()
  })
})
