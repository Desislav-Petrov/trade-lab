import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { usePatchUserSettings } from './usePatchUserSettings'
import { useSessionStore } from './useSessionStore'
import type { UserResponse } from '../types/user'

vi.mock('../api/userSettingsApi', () => ({
  patchUserSettings: vi.fn(),
}))

import { patchUserSettings } from '../api/userSettingsApi'
const mockPatchUserSettings = vi.mocked(patchUserSettings)

const mockUserResponse: UserResponse = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
  settings: { feedType: 'SYNTHETIC', updatedAt: '2026-01-01T00:00:00Z' },
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('usePatchUserSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
    act(() => useSessionStore.getState().setSession(mockUserResponse))
  })

  it('usePatchUserSettings - success - updates session store settings', async () => {
    const updatedSettings = { feedType: 'REAL' as const, updatedAt: '2026-06-01T00:00:00Z' }
    mockPatchUserSettings.mockResolvedValueOnce(updatedSettings)

    const { result } = renderHook(() => usePatchUserSettings('u1'), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate({ feedType: 'REAL' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(useSessionStore.getState().settings).toEqual(updatedSettings)
    expect(mockPatchUserSettings).toHaveBeenCalledWith('u1', { feedType: 'REAL' })
  })

  it('usePatchUserSettings - error - exposes error state', async () => {
    const error = Object.assign(new Error('Server error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockPatchUserSettings.mockRejectedValueOnce(error)

    const { result } = renderHook(() => usePatchUserSettings('u1'), {
      wrapper: createWrapper(),
    })

    act(() => {
      result.current.mutate({ feedType: 'REAL' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.error).toBeTruthy()
    expect(useSessionStore.getState().settings).toEqual({
      feedType: 'SYNTHETIC',
      updatedAt: '2026-01-01T00:00:00Z',
    })
  })
})
