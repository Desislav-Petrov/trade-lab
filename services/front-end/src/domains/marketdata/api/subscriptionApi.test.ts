import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import {
  fetchSubscriptions,
  bulkAddSubscriptions,
  bulkRemoveSubscriptions,
} from './subscriptionApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)
const mockPost = vi.mocked(axiosInstance.post)
const mockDelete = vi.mocked(axiosInstance.delete)

describe('fetchSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchSubscriptions - success - calls GET with correct URL and userId param', async () => {
    const subscriptions = [
      { ticker: 'AAPL', companyName: 'Apple Inc.' },
      { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
    ]
    mockGet.mockResolvedValueOnce({ data: subscriptions })

    const result = await fetchSubscriptions('u1')

    expect(result).toEqual(subscriptions)
    expect(mockGet).toHaveBeenCalledWith('/v1/market-data/subscriptions', {
      params: { userId: 'u1' },
    })
  })

  it('fetchSubscriptions - empty list - returns empty array', async () => {
    mockGet.mockResolvedValueOnce({ data: [] })

    const result = await fetchSubscriptions('u1')

    expect(result).toEqual([])
  })

  it('fetchSubscriptions - 401 response - throws AxiosError with status 401', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchSubscriptions('u1')).rejects.toMatchObject({
      response: { status: 401 },
    })
  })
})

describe('bulkAddSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('bulkAddSubscriptions - success - calls POST with correct URL and body', async () => {
    const response = {
      subscriptions: [
        { ticker: 'AAPL', companyName: 'Apple Inc.' },
        { ticker: 'MSFT', companyName: 'Microsoft Corporation' },
      ],
    }
    mockPost.mockResolvedValueOnce({ data: response })

    const result = await bulkAddSubscriptions({ userId: 'u1', tickers: ['AAPL', 'MSFT'] })

    expect(result).toEqual(response)
    expect(mockPost).toHaveBeenCalledWith('/v1/market-data/subscriptions', {
      userId: 'u1',
      tickers: ['AAPL', 'MSFT'],
    })
  })

  it('bulkAddSubscriptions - 400 response - throws AxiosError with status 400', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(
      bulkAddSubscriptions({ userId: 'u1', tickers: ['XYZ'] })
    ).rejects.toMatchObject({ response: { status: 400 } })
  })

  it('bulkAddSubscriptions - 409 response - throws AxiosError with status 409', async () => {
    const error = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(
      bulkAddSubscriptions({ userId: 'u1', tickers: ['AAPL'] })
    ).rejects.toMatchObject({ response: { status: 409 } })
  })
})

describe('bulkRemoveSubscriptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('bulkRemoveSubscriptions - success - calls DELETE with correct URL and body', async () => {
    mockDelete.mockResolvedValueOnce({ data: undefined })

    await bulkRemoveSubscriptions({ userId: 'u1', tickers: ['AAPL'] })

    expect(mockDelete).toHaveBeenCalledWith('/v1/market-data/subscriptions', {
      data: { userId: 'u1', tickers: ['AAPL'] },
    })
  })

  it('bulkRemoveSubscriptions - 404 response - throws AxiosError with status 404', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockDelete.mockRejectedValueOnce(error)

    await expect(
      bulkRemoveSubscriptions({ userId: 'u1', tickers: ['AAPL'] })
    ).rejects.toMatchObject({ response: { status: 404 } })
  })

  it('bulkRemoveSubscriptions - 401 response - throws AxiosError with status 401', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockDelete.mockRejectedValueOnce(error)

    await expect(
      bulkRemoveSubscriptions({ userId: 'u1', tickers: ['AAPL'] })
    ).rejects.toMatchObject({ response: { status: 401 } })
  })
})
