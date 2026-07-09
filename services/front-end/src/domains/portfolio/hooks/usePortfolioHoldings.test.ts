import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { usePortfolioHoldings } from './usePortfolioHoldings'

vi.mock('../api/portfolioApi', () => ({
  fetchPortfolioHoldings: vi.fn(),
  PORTFOLIO_HOLDINGS_KEY: 'portfolioHoldings',
}))

import { fetchPortfolioHoldings } from '../api/portfolioApi'
const mockFetchPortfolioHoldings = vi.mocked(fetchPortfolioHoldings)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

const mockPortfolioHoldingsResponse = {
  holdings: [
    {
      ticker: 'AAPL',
      quantity: 10,
      currentPrice: 150.0,
      currentValue: 1500.0,
      minPrice: 145.0,
      maxPrice: 155.0,
      avgPrice: 148.0,
      portfolioPercent: 60.0,
      unrealisedPnL: 20.0,
    },
    {
      ticker: 'GOOGL',
      quantity: 5,
      currentPrice: 100.0,
      currentValue: 500.0,
      minPrice: 95.0,
      maxPrice: 105.0,
      avgPrice: 98.0,
      portfolioPercent: 20.0,
      unrealisedPnL: 10.0,
    },
  ],
  cash: {
    balance: 500.0,
    currency: 'USD' as const,
    portfolioPercent: 20.0,
  },
}

describe('usePortfolioHoldings', () => {
  beforeEach(() => vi.clearAllMocks())

  it('usePortfolioHoldings - accountId is null - query is disabled', async () => {
    const { result } = renderHook(() => usePortfolioHoldings(null, 'user-1'), {
      wrapper: createWrapper(),
    })

    // Give TanStack Query a tick to potentially fire
    await new Promise((r) => setTimeout(r, 50))

    expect(mockFetchPortfolioHoldings).not.toHaveBeenCalled()
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toBeUndefined()
  })

  it('usePortfolioHoldings - happy path - returns PortfolioHoldingsResponse data', async () => {
    mockFetchPortfolioHoldings.mockResolvedValueOnce(mockPortfolioHoldingsResponse)

    const { result } = renderHook(() => usePortfolioHoldings('acc-1', 'user-1'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(false)
    expect(result.current.data).toEqual(mockPortfolioHoldingsResponse)
    expect(mockFetchPortfolioHoldings).toHaveBeenCalledWith('acc-1', 'user-1')
  })

  it('usePortfolioHoldings - error state - isError is true when API call fails', async () => {
    const serverError = Object.assign(new Error('Internal Server Error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockFetchPortfolioHoldings.mockRejectedValueOnce(serverError)

    const { result } = renderHook(() => usePortfolioHoldings('acc-1', 'user-1'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.isLoading).toBe(false)
    expect(result.current.error).toBe(serverError)
    expect(result.current.data).toBeUndefined()
  })
})
