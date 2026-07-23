import { describe, it, expect, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { useFetchUserProfile } from './useFetchUserProfile'
import * as userApi from '../api/userApi'
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

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useFetchUserProfile', () => {
  it('useFetchUserProfile - success - calls setSession with profile data', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockResolvedValueOnce(mockResponse)
    act(() => useSessionStore.getState().clearSession())

    const onSuccess = vi.fn()
    const { result } = renderHook(() => useFetchUserProfile({ onSuccess }), { wrapper })

    act(() => {
      result.current.mutate('u1')
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const { user, settings } = useSessionStore.getState()
    expect(user).toEqual({
      userId: 'u1',
      firstName: 'Jane',
      lastName: 'Doe',
      address: '123 Main St',
      email: 'jane@example.com',
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    })
    expect(settings).toEqual({ feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' })
    expect(onSuccess).toHaveBeenCalledOnce()
  })

  it('useFetchUserProfile - error - does not call setSession, calls onError', async () => {
    vi.spyOn(userApi, 'fetchUserById').mockRejectedValueOnce(new Error('network error'))
    act(() => useSessionStore.getState().clearSession())

    const onError = vi.fn()
    const { result } = renderHook(() => useFetchUserProfile({ onError }), { wrapper })

    act(() => {
      result.current.mutate('u1')
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(useSessionStore.getState().user).toBeNull()
    expect(onError).toHaveBeenCalledOnce()
  })
})
