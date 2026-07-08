import { describe, it, expect } from 'vitest'
import axiosInstance from './axiosInstance'

describe('axiosInstance', () => {
  it('axiosInstance - timeout - is configured to 10000ms', () => {
    expect(axiosInstance.defaults.timeout).toBe(10_000)
  })

  it('axiosInstance - baseURL - is /api', () => {
    expect(axiosInstance.defaults.baseURL).toBe('/api')
  })

  it('axiosInstance - Content-Type - is application/json', () => {
    expect(axiosInstance.defaults.headers['Content-Type']).toBe('application/json')
  })
})
