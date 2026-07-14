import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useSellPanel } from './useSellPanel'
import { usePortfolioStore } from '../../portfolio/hooks/usePortfolioStore'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import type { PlaceOrderResponse } from '../api/ordersApi'

vi.mock('../api/ordersApi', () => ({
  fetchIndicativePrice: vi.fn(),
  placeOrder: vi.fn(),
  ORDERS_QUERY_KEY: ['orders'],
}))

vi.mock('../../portfolio/api/portfolioApi', () => ({
  PORTFOLIO_HOLDINGS_KEY: 'portfolioHoldings',
}))

vi.mock('../../ledger/api/accountApi', () => ({
  ACCOUNTS_QUERY_KEY: 'accounts',
}))

import { fetchIndicativePrice, placeOrder } from '../api/ordersApi'
const mockFetchIndicativePrice = vi.mocked(fetchIndicativePrice)
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

const mockUser = {
  userId: 'user-1',
  firstName: 'Jane',
  lastName: 'Doe',
  address: '123 Main St',
  email: 'jane@example.com',
  status: 'active' as const,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('useSellPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => {
      useSessionStore.getState().clearSession()
      usePortfolioStore.setState({ selectedAccountId: null })
    })
    // Reset the sell panel store to initial state before each test
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })
    act(() => result.current.closeSellPanel())
  })

  it('useSellPanel - initial state - isOpen is false and all fields are null/empty', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    expect(result.current.isOpen).toBe(false)
    expect(result.current.ticker).toBeNull()
    expect(result.current.maxQuantity).toBeNull()
    expect(result.current.priceSnapshot).toBeNull()
    expect(result.current.quantity).toBe('')
    expect(result.current.validationError).toBeNull()
    expect(result.current.isFetchingPrice).toBe(false)
    expect(result.current.priceError).toBeNull()
    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.submitError).toBeNull()
    expect(result.current.result).toBeNull()
  })

  it('useSellPanel - openSellPanel success - sets isOpen, priceSnapshot, idempotencyKey', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    expect(result.current.isOpen).toBe(true)
    expect(result.current.ticker).toBe('AAPL')
    expect(result.current.maxQuantity).toBe(10)
    expect(result.current.priceSnapshot).toBe(182.5)
    expect(result.current.idempotencyKey).not.toBeNull()
    expect(typeof result.current.idempotencyKey).toBe('string')
    expect(result.current.isFetchingPrice).toBe(false)
    expect(result.current.priceError).toBeNull()
  })

  it('useSellPanel - openSellPanel failure - sets priceError and keeps isOpen false', async () => {
    mockFetchIndicativePrice.mockRejectedValueOnce(new Error('Network error'))

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    expect(result.current.isOpen).toBe(false)
    expect(result.current.priceError).toBe('Could not fetch indicative price.')
    expect(result.current.isFetchingPrice).toBe(false)
  })

  it('useSellPanel - openSellPanel - sets isFetchingPrice true during fetch', async () => {
    let resolveFetch!: (value: { ticker: string; indicativePrice: number }) => void
    mockFetchIndicativePrice.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFetch = resolve
      })
    )

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => {
      void result.current.openSellPanel('AAPL', 10)
    })

    await waitFor(() => expect(result.current.isFetchingPrice).toBe(true))

    await act(async () => {
      resolveFetch({ ticker: 'AAPL', indicativePrice: 182.5 })
    })

    await waitFor(() => expect(result.current.isFetchingPrice).toBe(false))
  })

  it('useSellPanel - closeSellPanel - resets all state to initial values', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })
    act(() => {
      result.current.setQuantity('5')
    })

    expect(result.current.isOpen).toBe(true)

    act(() => {
      result.current.closeSellPanel()
    })

    expect(result.current.isOpen).toBe(false)
    expect(result.current.ticker).toBeNull()
    expect(result.current.maxQuantity).toBeNull()
    expect(result.current.priceSnapshot).toBeNull()
    expect(result.current.quantity).toBe('')
    expect(result.current.validationError).toBeNull()
    expect(result.current.result).toBeNull()
  })

  it('useSellPanel - setQuantity valid - clears validationError', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => result.current.setQuantity('abc'))
    expect(result.current.validationError).toBe('Please enter a valid number.')

    act(() => result.current.setQuantity('5'))
    expect(result.current.validationError).toBeNull()
    expect(result.current.quantity).toBe('5')
  })

  it('useSellPanel - setQuantity non-numeric - sets validationError', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => result.current.setQuantity('abc'))

    expect(result.current.validationError).toBe('Please enter a valid number.')
  })

  it('useSellPanel - setQuantity zero - sets validationError greater than zero', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => result.current.setQuantity('0'))

    expect(result.current.validationError).toBe('Quantity must be greater than zero.')
  })

  it('useSellPanel - setQuantity negative - sets validationError greater than zero', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => result.current.setQuantity('-1'))

    expect(result.current.validationError).toBe('Quantity must be greater than zero.')
  })

  it('useSellPanel - setQuantity exceeds maxQuantity - sets validationError with maxQuantity', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 5)
    })

    act(() => result.current.setQuantity('10'))

    expect(result.current.validationError).toBe(
      'Quantity cannot exceed your holding of 5 shares.'
    )
  })

  it('useSellPanel - confirmSell FILLED - sets result, invalidates portfolio and accounts queries', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const filledResponse: PlaceOrderResponse = {
      orderId: 'order-1',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '3',
      executionPrice: '183.00',
      totalCost: null,
      totalProceeds: 549,
      side: 'SELL',
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(filledResponse)

    act(() => {
      useSessionStore.getState().setSession(mockUser)
      usePortfolioStore.setState({ selectedAccountId: 'acc-1' })
    })

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => result.current.setQuantity('3'))

    await act(async () => {
      await result.current.confirmSell()
    })

    expect(result.current.result).toEqual(filledResponse)
    expect(result.current.isSubmitting).toBe(false)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolioHoldings'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] })
  })

  it('useSellPanel - confirmSell REJECTED - sets result with rejection reason, no cache invalidation', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const rejectedResponse: PlaceOrderResponse = {
      orderId: 'order-2',
      status: 'REJECTED',
      ticker: 'AAPL',
      quantity: '200',
      executionPrice: null,
      totalCost: null,
      totalProceeds: null,
      side: 'SELL',
      rejectionReason: 'Insufficient holdings',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(rejectedResponse)

    act(() => {
      useSessionStore.getState().setSession(mockUser)
      usePortfolioStore.setState({ selectedAccountId: 'acc-1' })
    })

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => result.current.setQuantity('200'))

    await act(async () => {
      await result.current.confirmSell()
    })

    expect(result.current.result).toEqual(rejectedResponse)
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('useSellPanel - confirmSell 409 - generates new idempotencyKey and sets submitError', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const conflictError = Object.assign(new Error('Conflict'), {
      response: { status: 409 },
    })
    mockPlaceOrder.mockRejectedValueOnce(conflictError)

    act(() => {
      useSessionStore.getState().setSession(mockUser)
      usePortfolioStore.setState({ selectedAccountId: 'acc-1' })
    })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    const firstIdempotencyKey = result.current.idempotencyKey

    act(() => result.current.setQuantity('3'))

    await act(async () => {
      await result.current.confirmSell()
    })

    expect(result.current.submitError).toBe('Duplicate order detected. Please try again.')
    expect(result.current.idempotencyKey).not.toBe(firstIdempotencyKey)
    expect(result.current.isSubmitting).toBe(false)
  })

  it('useSellPanel - confirmSell other error - sets submitError', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const networkError = new Error('Network error')
    mockPlaceOrder.mockRejectedValueOnce(networkError)

    act(() => {
      useSessionStore.getState().setSession(mockUser)
      usePortfolioStore.setState({ selectedAccountId: 'acc-1' })
    })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => result.current.setQuantity('3'))

    await act(async () => {
      await result.current.confirmSell()
    })

    expect(result.current.submitError).toBe('Something went wrong. Please try again.')
    expect(result.current.isSubmitting).toBe(false)
  })
})
