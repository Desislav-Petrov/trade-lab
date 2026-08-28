import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import {
  fetchFillHistory,
  fetchPortfolioHoldings,
  FILL_HISTORY_QUERY_KEY,
  PORTFOLIO_HOLDINGS_KEY,
} from './portfolioApi'
import type { FillHistoryResponse, PortfolioHoldingsResponse } from '../types/portfolio.types'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)

const mockResponse: PortfolioHoldingsResponse = {
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

describe('fetchPortfolioHoldings', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchPortfolioHoldings - success - returns typed PortfolioHoldingsResponse', async () => {
    mockGet.mockResolvedValueOnce({ data: mockResponse })

    const result = await fetchPortfolioHoldings('acc-1', 'u1')

    expect(result).toEqual(mockResponse)
    expect(mockGet).toHaveBeenCalledWith('/v1/portfolio/holdings', {
      params: { accountId: 'acc-1', userId: 'u1' },
    })
  })

  it('fetchPortfolioHoldings - success - portfolioPercent null - returns null values', async () => {
    const responseWithNulls: PortfolioHoldingsResponse = {
      holdings: [
        {
          ticker: 'TSLA',
          quantity: 5,
          currentPrice: 200.0,
          currentValue: 1000.0,
          minPrice: 190.0,
          maxPrice: 210.0,
          avgPrice: 195.0,
          portfolioPercent: null,
          unrealisedPnL: 25.0,
        },
      ],
      cash: {
        balance: 0.0,
        currency: 'USD',
        portfolioPercent: null,
      },
    }
    mockGet.mockResolvedValueOnce({ data: responseWithNulls })

    const result = await fetchPortfolioHoldings('acc-2', 'u1')

    expect(result.holdings[0].portfolioPercent).toBeNull()
    expect(result.cash.portfolioPercent).toBeNull()
  })

  it('fetchPortfolioHoldings - axios error - propagates error', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-1', 'u1')).rejects.toMatchObject({
      response: { status: 401 },
    })
  })

  it('fetchPortfolioHoldings - 502 error - propagates error', async () => {
    const error = Object.assign(new Error('Bad Gateway'), {
      isAxiosError: true,
      response: { status: 502 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-1', 'u1')).rejects.toMatchObject({
      response: { status: 502 },
    })
  })
})

describe('PORTFOLIO_HOLDINGS_KEY', () => {
  it('PORTFOLIO_HOLDINGS_KEY - is defined as expected string', () => {
    expect(PORTFOLIO_HOLDINGS_KEY).toBe('portfolioHoldings')
  })
})

const mockFillHistoryResponse: FillHistoryResponse = {
  page: 0,
  size: 100,
  totalPages: 1,
  totalElements: 2,
  fills: [
    {
      ticker: 'AAPL',
      dataPoints: [
        {
          filledAt: '2026-08-28T10:00:00Z',
          executionPrice: 150,
          quantity: 2,
          side: 'BUY',
        },
        {
          filledAt: '2026-08-28T11:00:00Z',
          executionPrice: 155,
          quantity: 1,
          side: 'SELL',
        },
      ],
    },
  ],
}

describe('fetchFillHistory', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchFillHistory - defaults - sends accountId page and size query params', async () => {
    mockGet.mockResolvedValueOnce({ data: mockFillHistoryResponse })

    const result = await fetchFillHistory('acc-1')

    expect(result).toEqual(mockFillHistoryResponse)
    expect(mockGet).toHaveBeenCalledWith('/v1/portfolio/fills', {
      params: { accountId: 'acc-1', page: 0, size: 100 },
    })
  })

  it('fetchFillHistory - custom paging - sends provided page and size query params', async () => {
    mockGet.mockResolvedValueOnce({ data: { ...mockFillHistoryResponse, page: 2, size: 50 } })

    await fetchFillHistory('acc-2', 2, 50)

    expect(mockGet).toHaveBeenCalledWith('/v1/portfolio/fills', {
      params: { accountId: 'acc-2', page: 2, size: 50 },
    })
  })
})

describe('FILL_HISTORY_QUERY_KEY', () => {
  it('FILL_HISTORY_QUERY_KEY - is defined as expected string', () => {
    expect(FILL_HISTORY_QUERY_KEY).toBe('fillHistory')
  })
})
