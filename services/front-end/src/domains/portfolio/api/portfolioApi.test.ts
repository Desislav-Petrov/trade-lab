import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { fetchPortfolioHoldings } from './portfolioApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)

describe('fetchPortfolioHoldings', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchPortfolioHoldings - success - returns typed PortfolioHoldingsResponse', async () => {
    const response = {
      holdings: [
        {
          ticker: 'AAPL',
          quantity: 10.5,
          currentPrice: 182.5,
          currentValue: 1916.25,
          minPrice: 175.0,
          maxPrice: 185.0,
          avgPrice: 180.0,
          portfolioPercent: 15.5,
          unrealisedPnL: 26.25,
        },
        {
          ticker: 'GOOGL',
          quantity: 5.0,
          currentPrice: 140.0,
          currentValue: 700.0,
          minPrice: 138.0,
          maxPrice: 142.0,
          avgPrice: 139.0,
          portfolioPercent: 5.67,
          unrealisedPnL: 5.0,
        },
      ],
      cash: {
        balance: 5000.0,
        currency: 'USD',
        portfolioPercent: 78.83,
      },
    }
    mockGet.mockResolvedValueOnce({ data: response })

    const result = await fetchPortfolioHoldings('acc-123', 'user-456')

    expect(result).toEqual(response)
    expect(mockGet).toHaveBeenCalledWith('/v1/portfolio/holdings', {
      params: { accountId: 'acc-123', userId: 'user-456' },
    })
  })

  it('fetchPortfolioHoldings - empty holdings - returns cash only', async () => {
    const response = {
      holdings: [],
      cash: {
        balance: 10000.0,
        currency: 'USD',
        portfolioPercent: 100.0,
      },
    }
    mockGet.mockResolvedValueOnce({ data: response })

    const result = await fetchPortfolioHoldings('acc-123', 'user-456')

    expect(result).toEqual(response)
    expect(result.holdings).toHaveLength(0)
  })

  it('fetchPortfolioHoldings - portfolioPercent null - returns null portfolioPercent', async () => {
    const response = {
      holdings: [
        {
          ticker: 'AAPL',
          quantity: 10.0,
          currentPrice: 150.0,
          currentValue: 1500.0,
          minPrice: 150.0,
          maxPrice: 150.0,
          avgPrice: 150.0,
          portfolioPercent: null,
          unrealisedPnL: 0.0,
        },
      ],
      cash: {
        balance: 0.0,
        currency: 'USD',
        portfolioPercent: null,
      },
    }
    mockGet.mockResolvedValueOnce({ data: response })

    const result = await fetchPortfolioHoldings('acc-123', 'user-456')

    expect(result.holdings[0].portfolioPercent).toBeNull()
    expect(result.cash.portfolioPercent).toBeNull()
  })

  it('fetchPortfolioHoldings - 401 response - throws AxiosError with status 401', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-123', 'user-456')).rejects.toMatchObject({
      response: { status: 401 },
    })
  })

  it('fetchPortfolioHoldings - 403 response - throws AxiosError with status 403', async () => {
    const error = Object.assign(new Error('Access denied'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-123', 'user-456')).rejects.toMatchObject({
      response: { status: 403 },
    })
  })

  it('fetchPortfolioHoldings - 404 response - throws AxiosError with status 404', async () => {
    const error = Object.assign(new Error('Account not found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-123', 'user-456')).rejects.toMatchObject({
      response: { status: 404 },
    })
  })

  it('fetchPortfolioHoldings - 502 response - throws AxiosError with status 502', async () => {
    const error = Object.assign(new Error('Upstream service unavailable'), {
      isAxiosError: true,
      response: { status: 502 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-123', 'user-456')).rejects.toMatchObject({
      response: { status: 502 },
    })
  })

  it('fetchPortfolioHoldings - network error - throws AxiosError', async () => {
    const error = Object.assign(new Error('Network Error'), {
      isAxiosError: true,
      code: 'ERR_NETWORK',
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchPortfolioHoldings('acc-123', 'user-456')).rejects.toMatchObject({
      message: 'Network Error',
    })
  })
})
