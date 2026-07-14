import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { placeOrder, fetchIndicativePrice } from './ordersApi'
import type { PlaceOrderRequest } from './ordersApi'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { post: vi.fn(), get: vi.fn() },
}))

const mockPost = vi.mocked(axiosInstance.post)
const mockGet = vi.mocked(axiosInstance.get)

const sampleRequest: PlaceOrderRequest = {
  accountId: 'acc-1',
  userId: 'user-1',
  ticker: 'AAPL',
  quantity: '2',
  orderType: 'MARKET',
  priceSnapshot: '180.000',
  side: 'BUY',
}

describe('placeOrder', () => {
  beforeEach(() => vi.clearAllMocks())

  it('placeOrder - success FILLED - POSTs to /v1/stock-orders with correct body and Idempotency-Key header', async () => {
    const response = {
      orderId: 'order-1',
      status: 'FILLED' as const,
      ticker: 'AAPL',
      quantity: '2',
      executionPrice: '181.000',
      totalCost: '362.000',
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPost.mockResolvedValueOnce({ data: response })

    const result = await placeOrder('idem-key-1', sampleRequest)

    expect(result).toEqual(response)
    expect(mockPost).toHaveBeenCalledWith('/v1/stock-orders', sampleRequest, {
      headers: { 'Idempotency-Key': 'idem-key-1' },
    })
  })

  it('placeOrder - success REJECTED - POSTs and returns REJECTED response', async () => {
    const response = {
      orderId: 'order-2',
      status: 'REJECTED' as const,
      ticker: 'AAPL',
      quantity: '2',
      executionPrice: null,
      totalCost: null,
      rejectionReason: 'Insufficient funds',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPost.mockResolvedValueOnce({ data: response })

    const result = await placeOrder('idem-key-2', sampleRequest)

    expect(result.status).toBe('REJECTED')
    expect(result.rejectionReason).toBe('Insufficient funds')
    expect(result.executionPrice).toBeNull()
    expect(result.totalCost).toBeNull()
  })

  it('placeOrder - request body includes userId', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        orderId: 'order-3',
        status: 'FILLED',
        ticker: 'AAPL',
        quantity: '1',
        executionPrice: '100.000',
        totalCost: '100.000',
        rejectionReason: null,
        accountId: 'acc-1',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })

    await placeOrder('my-unique-key', sampleRequest)

    const sentBody = mockPost.mock.calls[0][1] as PlaceOrderRequest
    expect(sentBody.userId).toBe('user-1')
  })

  it('placeOrder - uses provided idempotency key as Idempotency-Key header', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        orderId: 'order-3',
        status: 'FILLED',
        ticker: 'AAPL',
        quantity: '1',
        executionPrice: '100.000',
        totalCost: '100.000',
        rejectionReason: null,
        accountId: 'acc-1',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })

    await placeOrder('my-unique-key', sampleRequest)

    const callArgs = mockPost.mock.calls[0]
    expect(callArgs[2]).toEqual({ headers: { 'Idempotency-Key': 'my-unique-key' } })
  })

  it('placeOrder - 400 response - throws error', async () => {
    const error = Object.assign(new Error('Bad Request'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(placeOrder('idem-key-3', sampleRequest)).rejects.toMatchObject({
      response: { status: 400 },
    })
  })

  it('placeOrder - 409 response - throws error', async () => {
    const error = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    mockPost.mockRejectedValueOnce(error)

    await expect(placeOrder('idem-key-dup', sampleRequest)).rejects.toMatchObject({
      response: { status: 409 },
    })
  })

  it('placeOrder - side SELL - POSTs with side: SELL in request body', async () => {
    const sellRequest: PlaceOrderRequest = {
      ...sampleRequest,
      side: 'SELL',
    }
    mockPost.mockResolvedValueOnce({
      data: {
        orderId: 'order-sell-1',
        status: 'FILLED' as const,
        ticker: 'AAPL',
        quantity: '2',
        executionPrice: '182.000',
        totalCost: null,
        totalProceeds: 364,
        side: 'SELL' as const,
        rejectionReason: null,
        accountId: 'acc-1',
        createdAt: '2026-01-01T00:00:00Z',
      },
    })

    const result = await placeOrder('idem-key-sell', sellRequest)

    expect(result.side).toBe('SELL')
    expect(result.totalProceeds).toBe(364)
    const sentBody = mockPost.mock.calls[0][1] as PlaceOrderRequest
    expect(sentBody.side).toBe('SELL')
  })
})

describe('fetchIndicativePrice', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchIndicativePrice - happy path - GETs /v1/stock-orders/indicative-price with ticker param', async () => {
    const response = { ticker: 'AAPL', indicativePrice: 182.5 }
    mockGet.mockResolvedValueOnce({ data: response })

    const result = await fetchIndicativePrice('AAPL')

    expect(result).toEqual(response)
    expect(mockGet).toHaveBeenCalledWith('/v1/stock-orders/indicative-price', {
      params: { ticker: 'AAPL' },
    })
  })

  it('fetchIndicativePrice - error - throws when GET fails', async () => {
    const error = Object.assign(new Error('Service Unavailable'), {
      isAxiosError: true,
      response: { status: 503 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchIndicativePrice('AAPL')).rejects.toMatchObject({
      response: { status: 503 },
    })
  })
})
