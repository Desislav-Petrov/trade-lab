import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { useSessionStore } from './useSessionStore'
import type { UserResponse } from '../types/user'

const mockResponse: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

describe('useSessionStore', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('useSessionStore - initial state - user is null', () => {
    const { user, settings, loggedInAt } = useSessionStore.getState()
    expect(user).toBeNull()
    expect(settings).toBeNull()
    expect(loggedInAt).toBeNull()
  })

  it('useSessionStore - setSession - stores user and sets loggedInAt', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))

    const { user, loggedInAt } = useSessionStore.getState()
    expect(user).toEqual({
      userId: 'u1',
      firstName: 'Jane',
      lastName: 'Doe',
      address: '123 Main St',
      email: 'jane@example.com',
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    })
    expect(loggedInAt).not.toBeNull()
  })

  it('useSessionStore - setSession - populates settings from response', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))

    const { settings } = useSessionStore.getState()
    expect(settings).toEqual({ feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' })
  })

  it('useSessionStore - clearSession - clears user and loggedInAt', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    act(() => useSessionStore.getState().clearSession())

    const { user, loggedInAt } = useSessionStore.getState()
    expect(user).toBeNull()
    expect(loggedInAt).toBeNull()
  })

  it('useSessionStore - clearSession - sets settings to null', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    act(() => useSessionStore.getState().clearSession())

    const { settings } = useSessionStore.getState()
    expect(settings).toBeNull()
  })

  it('useSessionStore - updateSettings - replaces settings in store', () => {
    act(() => useSessionStore.getState().setSession(mockResponse))
    act(() =>
      useSessionStore.getState().updateSettings({ feedType: 'REAL', updatedAt: '2026-06-01T00:00:00Z' }),
    )

    const { settings } = useSessionStore.getState()
    expect(settings).toEqual({ feedType: 'REAL', updatedAt: '2026-06-01T00:00:00Z' })
  })
})
