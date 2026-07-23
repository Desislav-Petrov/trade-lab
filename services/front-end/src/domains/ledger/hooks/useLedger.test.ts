import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useAccounts, useActiveAccounts, useOpenAccount, useTopUpAccount } from './useLedger'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { UserProfile } from '../../user/types/user'

vi.mock('../api/accountApi', () => ({
  fetchAccounts: vi.fn(),
  createAccount: vi.fn(),
  topUpAccount: vi.fn(),
  ACCOUNTS_QUERY_KEY: 'accounts',
  TOP_UP_ACCOUNT_KEY: 'topUpAccount',
}))

import { fetchAccounts, createAccount, topUpAccount } from '../api/accountApi'
const mockFetchAccounts = vi.mocked(fetchAccounts)
const mockCreateAccount = vi.mocked(createAccount)
const mockTopUpAccount = vi.mocked(topUpAccount)

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
        id: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 0,
        status: 'ACTIVE',
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

  it('useAccounts - staleTime 0 - refetches on every mount', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const accounts = [
      {
        id: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 0,
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]
    mockFetchAccounts.mockResolvedValue({ accounts })

    const wrapper = createWrapper()
    const { result, unmount } = renderHook(() => useAccounts(), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    unmount()

    const { result: result2 } = renderHook(() => useAccounts(), { wrapper: createWrapper() })
    await waitFor(() => expect(result2.current.isSuccess).toBe(true))

    expect(mockFetchAccounts).toHaveBeenCalledTimes(2)
  })
})

describe('useActiveAccounts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('useActiveAccounts - no session - query is disabled', async () => {
    const { result } = renderHook(() => useActiveAccounts(), { wrapper: createWrapper() })

    expect(result.current.fetchStatus).toBe('idle')
    expect(mockFetchAccounts).not.toHaveBeenCalled()
  })

  it('useActiveAccounts - session exists - fetches active accounts with status ACTIVE', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const accounts = [
      {
        id: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 0,
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]
    mockFetchAccounts.mockResolvedValueOnce({ accounts })

    const { result } = renderHook(() => useActiveAccounts(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.accounts).toEqual(accounts)
    expect(mockFetchAccounts).toHaveBeenCalledWith('u1', 'ACTIVE')
  })

  it('useActiveAccounts - fetch error - isError is true', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    mockFetchAccounts.mockRejectedValueOnce(new Error('Network error'))

    const { result } = renderHook(() => useActiveAccounts(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('useActiveAccounts - staleTime 0 - refetches on every mount', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const accounts = [
      {
        id: 'acc-1',
        name: 'My Account',
        currency: 'USD',
        balance: 0,
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
      },
    ]
    mockFetchAccounts.mockResolvedValue({ accounts })

    const wrapper = createWrapper()
    const { result, unmount } = renderHook(() => useActiveAccounts(), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    unmount()

    const { result: result2 } = renderHook(() => useActiveAccounts(), { wrapper: createWrapper() })
    await waitFor(() => expect(result2.current.isSuccess).toBe(true))

    expect(mockFetchAccounts).toHaveBeenCalledTimes(2)
    expect(mockFetchAccounts).toHaveBeenCalledWith('u1', 'ACTIVE')
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
      id: 'acc-1',
      name: 'My Account',
      currency: 'USD',
      balance: 0,
      status: 'ACTIVE',
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

describe('useTopUpAccount', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => useSessionStore.getState().clearSession())
  })

  it('useTopUpAccount - success - invalidates accounts query', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const topUpResponse = {
      accountId: 'acc-1',
      newBalance: 500,
      currency: 'USD',
      ledgerEntryId: 'le-1',
      timestamp: '2026-01-01T00:00:00Z',
    }
    mockTopUpAccount.mockResolvedValueOnce(topUpResponse)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined
    const OriginalQueryClient = QueryClient

    const wrapper = (() => {
      const queryClient = new OriginalQueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useTopUpAccount(), { wrapper })

    act(() => {
      result.current.mutate({ accountId: 'acc-1', request: { userId: 'u1', amount: 500 } })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockTopUpAccount).toHaveBeenCalledWith('acc-1', { userId: 'u1', amount: 500 })
    expect(capturedInvalidate).toHaveBeenCalledWith({ queryKey: ['accounts', 'u1'] })
  })

  it('useTopUpAccount - error - isError is true and invalidateQueries not called', async () => {
    act(() => useSessionStore.getState().setSession(mockProfile))
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockTopUpAccount.mockRejectedValueOnce(error)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined

    const wrapper = (() => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useTopUpAccount(), { wrapper })

    act(() => {
      result.current.mutate({ accountId: 'acc-1', request: { userId: 'u1', amount: 500 } })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(capturedInvalidate).not.toHaveBeenCalled()
  })
})
