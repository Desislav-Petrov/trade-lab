import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { usePlaceOrder } from './usePlaceOrder'
import type { PlaceOrderResponse } from '../api/ordersApi'

vi.mock('../api/ordersApi', () => ({
  placeOrder: vi.fn(),
  ORDERS_QUERY_KEY: ['orders'],
}))

vi.mock('../../ledger/api/accountApi', () => ({
  ACCOUNTS_QUERY_KEY: 'accounts',
}))

import { placeOrder } from '../api/ordersApi'
const mockPlaceOrder = vi.mocked(placeOrder)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children),
    queryClient,
  }
}

describe('usePlaceOrder', () => {
  beforeEach(() => vi.clearAllMocks())

  it('usePlaceOrder - success FILLED - invalidates ACCOUNTS_QUERY_KEY on success', async () => {
    const filledResponse: PlaceOrderResponse = {
      orderId: 'order-1',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '2',
      executionPrice: '181.000',
      totalCost: '362.000',
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(filledResponse)

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => usePlaceOrder(), { wrapper })

    act(() => {
      result.current.mutate({
        idempotencyKey: 'idem-key-1',
        accountId: 'acc-1',
        ticker: 'AAPL',
        quantity: '2',
        orderType: 'MARKET',
        priceSnapshot: '180.000',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(filledResponse)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] })
  })

  it('usePlaceOrder - error - error state accessible and invalidateQueries not called', async () => {
    const error = Object.assign(new Error('Network error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockPlaceOrder.mockRejectedValueOnce(error)

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => usePlaceOrder(), { wrapper })

    act(() => {
      result.current.mutate({
        idempotencyKey: 'idem-key-err',
        accountId: 'acc-1',
        ticker: 'AAPL',
        quantity: '2',
        orderType: 'MARKET',
        priceSnapshot: '180.000',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeDefined()
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('usePlaceOrder - mutation variables include idempotencyKey and PlaceOrderRequest', async () => {
    const filledResponse: PlaceOrderResponse = {
      orderId: 'order-2',
      status: 'FILLED',
      ticker: 'MSFT',
      quantity: '1',
      executionPrice: '300.000',
      totalCost: '300.000',
      rejectionReason: null,
      accountId: 'acc-2',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(filledResponse)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => usePlaceOrder(), { wrapper })

    act(() => {
      result.current.mutate({
        idempotencyKey: 'idem-key-2',
        accountId: 'acc-2',
        ticker: 'MSFT',
        quantity: '1',
        orderType: 'MARKET',
        priceSnapshot: '299.000',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(mockPlaceOrder).toHaveBeenCalledWith('idem-key-2', {
      accountId: 'acc-2',
      ticker: 'MSFT',
      quantity: '1',
      orderType: 'MARKET',
      priceSnapshot: '299.000',
    })
  })
})
