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
  fetchSupportedTickers: vi.fn(),
  SUBSCRIPTIONS_QUERY_KEY: 'subscriptions',
  SUPPORTED_TICKERS_QUERY_KEY: 'supportedTickers',
}))

import {
  fetchSubscriptions,
  bulkAddSubscriptions,
  bulkRemoveSubscriptions,
  fetchSupportedTickers,
} from '../api/subscriptionApi'

const mockFetchSubscriptions = vi.mocked(fetchSubscriptions)
const mockBulkAddSubscriptions = vi.mocked(bulkAddSubscriptions)
const mockBulkRemoveSubscriptions = vi.mocked(bulkRemoveSubscriptions)
const mockFetchSupportedTickers = vi.mocked(fetchSupportedTickers)

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
  beforeEach(() => vi.clearAllMocks())

  it('useSupportedTickers - success - returns data from API', async () => {
    const tickers = [
      { ticker: 'AAPL', companyName: 'Apple Inc.' },
      { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
    ]
    mockFetchSupportedTickers.mockResolvedValueOnce(tickers)

    const { result } = renderHook(() => useSupportedTickers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(tickers)
    expect(mockFetchSupportedTickers).toHaveBeenCalledOnce()
  })

  it('useSupportedTickers - server error - isError is true', async () => {
    const error = Object.assign(new Error('Internal Server Error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockFetchSupportedTickers.mockRejectedValueOnce(error)

    const { result } = renderHook(() => useSupportedTickers(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
