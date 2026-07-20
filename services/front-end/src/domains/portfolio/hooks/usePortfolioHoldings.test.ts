import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { usePortfolioHoldings } from './usePortfolioHoldings'
import type { PortfolioHoldingsResponse } from '../types/portfolio.types'

vi.mock('../api/portfolioApi', () => ({
  fetchPortfolioHoldings: vi.fn(),
  PORTFOLIO_HOLDINGS_KEY: 'portfolioHoldings',
}))

import { fetchPortfolioHoldings } from '../api/portfolioApi'
const mockFetchPortfolioHoldings = vi.mocked(fetchPortfolioHoldings)

const mockHoldingsResponse: PortfolioHoldingsResponse = {
  holdings: [
    {
      ticker: 'AAPL',
      quantity: 10,
      currentPrice: 150.0,
      currentValue: 1500.0,
      minPrice: 140.0,
      maxPrice: 160.0,
      avgPrice: 145.0,
      portfolioPercent: 75.0,
      unrealisedPnL: 50.0,
    },
  ],
  cash: {
    balance: 500.0,
    currency: 'USD',
    portfolioPercent: 25.0,
  },
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('usePortfolioHoldings', () => {
  beforeEach(() => vi.clearAllMocks())

  it('usePortfolioHoldings - accountId is null - query is disabled', async () => {
    const { result } = renderHook(() => usePortfolioHoldings(null, 'u1'), {
      wrapper: createWrapper(),
    })

    expect(result.current.fetchStatus).toBe('idle')
    expect(mockFetchPortfolioHoldings).not.toHaveBeenCalled()
  })

  it('usePortfolioHoldings - accountId provided - returns PortfolioHoldingsResponse data', async () => {
    mockFetchPortfolioHoldings.mockResolvedValueOnce(mockHoldingsResponse)

    const { result } = renderHook(() => usePortfolioHoldings('acc-1', 'u1'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockHoldingsResponse)
    expect(mockFetchPortfolioHoldings).toHaveBeenCalledWith('acc-1', 'u1')
  })

  it('usePortfolioHoldings - accountId provided - includes accountId and userId in queryKey', async () => {
    mockFetchPortfolioHoldings.mockResolvedValueOnce(mockHoldingsResponse)

    const { result } = renderHook(() => usePortfolioHoldings('acc-42', 'user-99'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockFetchPortfolioHoldings).toHaveBeenCalledWith('acc-42', 'user-99')
  })

  it('usePortfolioHoldings - API call fails - isError is true', async () => {
    const error = Object.assign(new Error('Server Error'), {
      isAxiosError: true,
      response: { status: 502 },
    })
    mockFetchPortfolioHoldings.mockRejectedValueOnce(error)

    const { result } = renderHook(() => usePortfolioHoldings('acc-1', 'u1'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })

  it('usePortfolioHoldings - accountId switches from null to value - query fires', async () => {
    mockFetchPortfolioHoldings.mockResolvedValue(mockHoldingsResponse)

    const { result, rerender } = renderHook(
      ({ id }: { id: string | null }) => usePortfolioHoldings(id, 'u1'),
      { wrapper: createWrapper(), initialProps: { id: null as string | null } },
    )

    expect(result.current.fetchStatus).toBe('idle')

    rerender({ id: 'acc-1' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockFetchPortfolioHoldings).toHaveBeenCalledWith('acc-1', 'u1')
  })
})
