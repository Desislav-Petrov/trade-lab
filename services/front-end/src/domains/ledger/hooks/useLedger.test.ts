import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useAccounts, useOpenAccount } from './useLedger'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserProfile } from '../../user/types/user'

vi.mock('../api/accountApi', () => ({
  fetchAccounts: vi.fn(),
  createAccount: vi.fn(),
  ACCOUNTS_QUERY_KEY: 'accounts',
}))

import { fetchAccounts, createAccount } from '../api/accountApi'
const mockFetchAccounts = vi.mocked(fetchAccounts)
const mockCreateAccount = vi.mocked(createAccount)

const mockProfile: UserProfile = {
  userId: 'u1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active',
  createdAt: '2026-01-01T00:00:00Z',
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useAccounts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('useAccounts - no session - query is disabled', async () => {
    const { result } = renderHook(() => useAccounts(), { wrapper: createWrapper() })

    expect(result.current.fetchStatus).toBe('idle')
    expect(mockFetchAccounts).not.toHaveBeenCalled()
  })

  it('useAccounts - session exists - fetches accounts', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const accounts = [
      {
        accountId: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 0,
        status: 'active',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]
    mockFetchAccounts.mockResolvedValueOnce({ accounts })

    const { result } = renderHook(() => useAccounts(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.accounts).toEqual(accounts)
    expect(mockFetchAccounts).toHaveBeenCalledWith('u1')
  })

  it('useAccounts - fetch error - isError is true', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockFetchAccounts.mockRejectedValueOnce(new Error('Network error'))

    const { result } = renderHook(() => useAccounts(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useOpenAccount', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('useOpenAccount - success - isPending transitions to false after success', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const accountResponse = {
      accountId: 'acc-1',
      name: 'My Account',
      currency: 'USD',
      balance: 0,
      status: 'active',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockCreateAccount.mockResolvedValueOnce(accountResponse)

    const { result } = renderHook(() => useOpenAccount(), { wrapper: createWrapper() })

    act(() => {
      result.current.mutate({ userId: 'u1', currency: 'USD', name: 'My Account' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.isPending).toBe(false)
    expect(mockCreateAccount).toHaveBeenCalledWith(
      { userId: 'u1', currency: 'USD', name: 'My Account' },
      expect.any(Object),
    )
  })

  it('useOpenAccount - error - exposes error', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockCreateAccount.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useOpenAccount(), { wrapper: createWrapper() })

    act(() => {
      result.current.mutate({ userId: 'u1', currency: 'USD' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
