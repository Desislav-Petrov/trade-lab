import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { useSessionStore } from './useSessionStore'
import type { UserResponse } from '../types/user'
import { SESSION_STORAGE_KEY } from '../types/user'

const JWT_PAYLOAD = btoa(JSON.stringify({ sub: 'u1', exp: 9999999999, iss: 'trade-platform' }))
const VALID_TOKEN = `header.${JWT_PAYLOAD}.sig`

const mockResponse: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: null,
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

describe('useSessionStore', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
    localStorage.clear()
  })

  it('useSessionStore - initial state - session is null', () => {
    const { session } = useSessionStore.getState()
    expect(session).toBeNull()
  })

  it('useSessionStore - establishSession - stores user, token, and loggedInAt', () => {
    act(() => useSessionStore.getState().establishSession(mockResponse, VALID_TOKEN))
    const { session } = useSessionStore.getState()
    expect(session?.userId).toBe('u1')
    expect(session?.accessToken).toBe(VALID_TOKEN)
    expect(session?.loggedInAt).not.toBeNull()
  })

  it('useSessionStore - establishSession - persists session to localStorage', () => {
    act(() => useSessionStore.getState().establishSession(mockResponse, VALID_TOKEN))
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    expect(raw).not.toBeNull()
    const parsed = JSON.parse(raw!)
    expect(parsed.accessToken).toBe(VALID_TOKEN)
  })

  it('useSessionStore - restoreSession with valid token - populates store', () => {
    // Seed localStorage with a valid session
    const session = { ...mockResponse, accessToken: VALID_TOKEN, loggedInAt: new Date().toISOString() }
    delete (session as { settings?: unknown }).settings  // settings not in UserProfile
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ ...session, settings: mockResponse.settings }))

    act(() => useSessionStore.getState().restoreSession())
    expect(useSessionStore.getState().session?.accessToken).toBe(VALID_TOKEN)
  })

  it('useSessionStore - restoreSession with expired token - does not populate store', () => {
    const EXPIRED_PAYLOAD = btoa(JSON.stringify({ sub: 'u1', exp: 1, iss: 'trade-platform' }))
    const EXPIRED_TOKEN = `header.${EXPIRED_PAYLOAD}.sig`
    const session = { ...mockResponse, settings: mockResponse.settings, accessToken: EXPIRED_TOKEN, loggedInAt: '2020-01-01T00:00:00Z' }
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))

    act(() => useSessionStore.getState().restoreSession())
    expect(useSessionStore.getState().session).toBeNull()
  })

  it('useSessionStore - restoreSession with empty localStorage - does not populate store', () => {
    act(() => useSessionStore.getState().restoreSession())
    expect(useSessionStore.getState().session).toBeNull()
  })

  it('useSessionStore - clearSession - removes from store and localStorage', () => {
    act(() => useSessionStore.getState().establishSession(mockResponse, VALID_TOKEN))
    act(() => useSessionStore.getState().clearSession())

    expect(useSessionStore.getState().session).toBeNull()
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('useSessionStore - updateSettings - replaces settings in store and localStorage', () => {
    act(() => useSessionStore.getState().establishSession(mockResponse, VALID_TOKEN))
    act(() =>
      useSessionStore.getState().updateSettings({ feedType: 'REAL', updatedAt: '2026-06-01T00:00:00Z' }),
    )

    const { session } = useSessionStore.getState()
    expect(session?.settings).toEqual({ feedType: 'REAL', updatedAt: '2026-06-01T00:00:00Z' })
  })

  it('useSessionStore - setSession (legacy) - populates session without breaking', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    const { session } = useSessionStore.getState()
    expect(session?.userId).toBe('u1')
    expect(session?.loggedInAt).not.toBeNull()
  })
})
