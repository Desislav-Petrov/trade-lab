import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import {
  useSubscriptions,
  useBulkAddSubscriptions,
  useBulkRemoveSubscriptions,
  useSupportedTickers,
} from './useSubscriptions'

vi.mock('../api/subscriptionApi', () => ({
  fetchSubscriptions: vi.fn(),
  bulkAddSubscriptions: vi.fn(),
  bulkRemoveSubscriptions: vi.fn(),
  SUBSCRIPTIONS_QUERY_KEY: 'subscriptions',
}))

import {
  fetchSubscriptions,
  bulkAddSubscriptions,
  bulkRemoveSubscriptions,
} from '../api/subscriptionApi'

const mockFetchSubscriptions = vi.mocked(fetchSubscriptions)
const mockBulkAddSubscriptions = vi.mocked(bulkAddSubscriptions)
const mockBulkRemoveSubscriptions = vi.mocked(bulkRemoveSubscriptions)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useSubscriptions - success - returns data', async () => {
    const subscriptions = [
      { ticker: 'AAPL', companyName: 'Apple Inc.' },
      { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
    ]
    mockFetchSubscriptions.mockResolvedValueOnce(subscriptions)

    const { result } = renderHook(() => useSubscriptions('u1'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(subscriptions)
    expect(mockFetchSubscriptions).toHaveBeenCalledWith('u1')
  })

  it('useSubscriptions - server error - isError is true', async () => {
    const error = Object.assign(new Error('Internal Server Error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockFetchSubscriptions.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useSubscriptions('u1'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('useSubscriptions - no userId - query is disabled', () => {
    const { result } = renderHook(() => useSubscriptions(''), { wrapper: createWrapper() })

    expect(result.current.fetchStatus).toBe('idle')
    expect(mockFetchSubscriptions).not.toHaveBeenCalled()
  })
})

describe('useBulkAddSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useBulkAddSubscriptions - success - calls invalidateQueries with correct key', async () => {
    const response = {
      subscriptions: [{ ticker: 'AAPL', companyName: 'Apple Inc.' }],
    }
    mockBulkAddSubscriptions.mockResolvedValueOnce(response)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined

    const wrapper = (() => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useBulkAddSubscriptions(), { wrapper })

    act(() => {
      result.current.mutate({ userId: 'u1', tickers: ['AAPL'] })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(capturedInvalidate).toHaveBeenCalledWith({ queryKey: ['subscriptions'] })
  })

  it('useBulkAddSubscriptions - error - isError is true and invalidateQueries not called', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockBulkAddSubscriptions.mockRejectedValueOnce(error)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined

    const wrapper = (() => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useBulkAddSubscriptions(), { wrapper })

    act(() => {
      result.current.mutate({ userId: 'u1', tickers: ['XYZ'] })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(capturedInvalidate).not.toHaveBeenCalled()
  })
})

describe('useBulkRemoveSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useBulkRemoveSubscriptions - success - calls invalidateQueries with correct key', async () => {
    mockBulkRemoveSubscriptions.mockResolvedValueOnce(undefined)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined

    const wrapper = (() => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useBulkRemoveSubscriptions(), { wrapper })

    act(() => {
      result.current.mutate({ userId: 'u1', tickers: ['AAPL'] })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(capturedInvalidate).toHaveBeenCalledWith({ queryKey: ['subscriptions'] })
  })

  it('useBulkRemoveSubscriptions - error - isError is true and invalidateQueries not called', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockBulkRemoveSubscriptions.mockRejectedValueOnce(error)

    let capturedInvalidate: ReturnType<typeof vi.fn> | undefined

    const wrapper = (() => {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      })
      capturedInvalidate = vi.spyOn(queryClient, 'invalidateQueries') as ReturnType<typeof vi.fn>
      return ({ children }: { children: React.ReactNode }) =>
        createElement(QueryClientProvider, { client: queryClient }, children)
    })()

    const { result } = renderHook(() => useBulkRemoveSubscriptions(), { wrapper })

    act(() => {
      result.current.mutate({ userId: 'u1', tickers: ['AAPL'] })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(capturedInvalidate).not.toHaveBeenCalled()
  })
})

describe('useSupportedTickers', () => {
  afterEach(() => vi.restoreAllMocks())

  it('useSupportedTickers - success - parses CSV lines into SubscriptionResponse array', async () => {
    const csvContent = 'AAPL,Apple Inc.\nMSFT,Microsoft Corporation\nGOOGL,Alphabet Inc.\n'
    const mockFetch = vi.fn().mockResolvedValue({
      text: () => Promise.resolve(csvContent),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { result } = renderHook(() => useSupportedTickers())

    await waitFor(() => expect(result.current.length).toBeGreaterThan(0))

    expect(result.current).toEqual([
      { ticker: 'AAPL', companyName: 'Apple Inc.' },
      { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
      { ticker: 'GOOGL', companyName: 'Alphabet Inc.' },
    ])
    expect(mockFetch).toHaveBeenCalledWith('/supported-tickers.csv')
  })

  it('useSupportedTickers - fetch fails - returns empty array silently', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error('Network error'))
    vi.stubGlobal('fetch', mockFetch)

    const { result } = renderHook(() => useSupportedTickers())

    // Allow time for the effect to run and fail
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50))
    })

    expect(result.current).toEqual([])
  })

  it('useSupportedTickers - CSV with company name containing comma - parses correctly', async () => {
    const csvContent = 'BRK.B,Berkshire Hathaway Inc.\nJPM,JPMorgan Chase & Co.\n'
    const mockFetch = vi.fn().mockResolvedValue({
      text: () => Promise.resolve(csvContent),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { result } = renderHook(() => useSupportedTickers())

    await waitFor(() => expect(result.current.length).toBeGreaterThan(0))

    expect(result.current).toEqual([
      { ticker: 'BRK.B', companyName: 'Berkshire Hathaway Inc.' },
      { ticker: 'JPM', companyName: 'JPMorgan Chase & Co.' },
    ])
  })

  it('useSupportedTickers - starts with empty array while loading', () => {
    const mockFetch = vi.fn().mockReturnValue(new Promise(() => {})) // never resolves
    vi.stubGlobal('fetch', mockFetch)

    const { result } = renderHook(() => useSupportedTickers())

    expect(result.current).toEqual([])
  })
})
