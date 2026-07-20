import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useTransactions } from './useTransactions'

vi.mock('../api/transactionApi', () => ({
  fetchTransactions: vi.fn(),
  TRANSACTION_KEYS: {
    all: ['transactions'],
    list: (accountId: string, userId: string, page: number) => [
      'transactions',
      accountId,
      userId,
      page,
    ],
  },
}))

import { fetchTransactions } from '../api/transactionApi'
const mockFetchTransactions = vi.mocked(fetchTransactions)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

const mockTransactionListResponse = {
  transactions: [
    {
      id: 'txn-1',
      type: 'CREDIT' as const,
      assetType: 'CASH' as const,
      amount: 1000,
      currency: 'USD',
      ticker: null,
      shares: null,
      description: 'Initial deposit',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  totalPages: 3,
  totalCount: 75,
}

describe('useTransactions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useTransactions - on mount - isLoading is true before data arrives', async () => {
    mockFetchTransactions.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockTransactionListResponse), 100)),
    )

    const { result } = renderHook(() => useTransactions('acc-1', 'user-1', 0), {
      wrapper: createWrapper(),
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.data).toBeUndefined()

    await waitFor(() => expect(result.current.isLoading).toBe(false))
  })

  it('useTransactions - success - returns data from fetchTransactions', async () => {
    mockFetchTransactions.mockResolvedValueOnce(mockTransactionListResponse)

    const { result } = renderHook(() => useTransactions('acc-1', 'user-1', 0), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(false)
    expect(result.current.data).toEqual(mockTransactionListResponse)
    expect(mockFetchTransactions).toHaveBeenCalledWith('acc-1', 'user-1', 0)
  })

  it('useTransactions - success - passes correct arguments to fetchTransactions', async () => {
    mockFetchTransactions.mockResolvedValueOnce({
      transactions: [],
      page: 2,
      totalPages: 5,
      totalCount: 120,
    })

    const { result } = renderHook(() => useTransactions('acc-42', 'user-99', 2), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(mockFetchTransactions).toHaveBeenCalledWith('acc-42', 'user-99', 2)
    expect(result.current.data?.page).toBe(2)
    expect(result.current.data?.totalPages).toBe(5)
  })

  it('useTransactions - error - isError is true and error is populated', async () => {
    const serverError = Object.assign(new Error('Internal Server Error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockFetchTransactions.mockRejectedValueOnce(serverError)

    const { result } = renderHook(() => useTransactions('acc-1', 'user-1', 0), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.isLoading).toBe(false)
    expect(result.current.error).toBe(serverError)
    expect(result.current.data).toBeUndefined()
  })

  it('useTransactions - 404 error - isError is true', async () => {
    const notFoundError = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockFetchTransactions.mockRejectedValueOnce(notFoundError)

    const { result } = renderHook(() => useTransactions('acc-missing', 'user-1', 0), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.error).toBe(notFoundError)
  })

  it('useTransactions - 403 error - isError is true', async () => {
    const forbiddenError = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockFetchTransactions.mockRejectedValueOnce(forbiddenError)

    const { result } = renderHook(() => useTransactions('acc-1', 'user-other', 0), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.error).toBe(forbiddenError)
  })

  it('useTransactions - empty userId - does not call fetchTransactions', async () => {
    const { result } = renderHook(() => useTransactions('acc-1', '', 0), {
      wrapper: createWrapper(),
    })

    // Give TanStack Query a tick to potentially fire
    await new Promise((r) => setTimeout(r, 50))

    expect(mockFetchTransactions).not.toHaveBeenCalled()
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toBeUndefined()
  })

  it('useTransactions - empty accountId - does not call fetchTransactions', async () => {
    const { result } = renderHook(() => useTransactions('', 'user-1', 0), {
      wrapper: createWrapper(),
    })

    await new Promise((r) => setTimeout(r, 50))

    expect(mockFetchTransactions).not.toHaveBeenCalled()
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toBeUndefined()
  })
})
