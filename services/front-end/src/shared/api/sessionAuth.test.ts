import { beforeEach, describe, expect, it } from 'vitest'
import { getSessionAuthorizationHeader } from './sessionAuth'
import { SESSION_STORAGE_KEY } from '../../domains/user/types/user'

describe('getSessionAuthorizationHeader', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('getSessionAuthorizationHeader - no session - returns undefined', () => {
    expect(getSessionAuthorizationHeader()).toBeUndefined()
  })

  it('getSessionAuthorizationHeader - access token present - returns bearer header', () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'token-123' }))

    const header = getSessionAuthorizationHeader()
    expect(header?.startsWith('Bearer ')).toBe(true)
    expect(header?.endsWith('token-123')).toBe(true)
  })

  it('getSessionAuthorizationHeader - malformed session - returns undefined', () => {
    localStorage.setItem(SESSION_STORAGE_KEY, '{bad json')

    expect(getSessionAuthorizationHeader()).toBeUndefined()
  })
})
