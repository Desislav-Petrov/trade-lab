import { describe, it, expect, beforeEach } from 'vitest'
import { act } from '@testing-library/react'
import { useSessionStore } from './useSessionStore'
import type { UserProfile } from '../types/user'

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

describe('useSessionStore', () => {
  beforeEach(() => {
    act(() => useSessionStore.getState().clearSession())
  })

  it('useSessionStore - initial state - user is null', () => {
    const { user, loggedInAt } = useSessionStore.getState()
    expect(user).toBeNull()
    expect(loggedInAt).toBeNull()
  })

  it('useSessionStore - setSession - stores user and sets loggedInAt', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))

    const { user, loggedInAt } = useSessionStore.getState()
    expect(user).toEqual(mockProfile)
    expect(loggedInAt).not.toBeNull()
  })

  it('useSessionStore - clearSession - clears user and loggedInAt', () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    act(() => useSessionStore.getState().clearSession())

    const { user, loggedInAt } = useSessionStore.getState()
    expect(user).toBeNull()
    expect(loggedInAt).toBeNull()
  })
})
