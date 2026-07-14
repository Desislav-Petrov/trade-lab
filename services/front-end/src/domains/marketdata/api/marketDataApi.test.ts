import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { fetchIndicativePrice } from './marketDataApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)

describe('fetchIndicativePrice', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchIndicativePrice - success - returns ticker and indicativePrice', async () => {
    const responseData = { ticker: 'AAPL', indicativePrice: 182.5 }
    mockGet.mockResolvedValueOnce({ data: responseData })

    const result = await fetchIndicativePrice('AAPL')

    expect(result).toEqual(responseData)
    expect(mockGet).toHaveBeenCalledWith('/v1/market-data/price', { params: { ticker: 'AAPL' } })
  })

  it('fetchIndicativePrice - 404 error - rethrows error', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchIndicativePrice('UNKNOWN')).rejects.toMatchObject({
      response: { status: 404 },
    })
  })
})
