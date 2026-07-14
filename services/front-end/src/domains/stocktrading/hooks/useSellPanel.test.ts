import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useSellPanel, useSellPanelStore } from './useSellPanel'
import { usePortfolioStore } from '../../portfolio/hooks/usePortfolioStore'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { PORTFOLIO_HOLDINGS_KEY } from '../../portfolio/api/portfolioApi'
import { ACCOUNTS_QUERY_KEY } from '../../ledger/api/accountApi'

vi.mock('../../marketdata/api/marketDataApi', () => ({
  fetchIndicativePrice: vi.fn(),
}))

vi.mock('../api/ordersApi', () => ({
  placeOrder: vi.fn(),
}))

vi.mock('../../portfolio/hooks/usePortfolioStore', () => ({
  usePortfolioStore: Object.assign(vi.fn(), {
    getState: vi.fn(),
  }),
}))

vi.mock('../../user/hooks/useSessionStore', () => ({
  useSessionStore: Object.assign(vi.fn(), {
    getState: vi.fn(),
  }),
}))

import { fetchIndicativePrice } from '../../marketdata/api/marketDataApi'
import { placeOrder } from '../api/ordersApi'

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

describe('useSellPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSellPanelStore.getState().closeSellPanel()
    vi.mocked(usePortfolioStore).getState = vi.fn().mockReturnValue({
      selectedAccountId: 'acc-1',
    })
    vi.mocked(useSessionStore).getState = vi.fn().mockReturnValue({
      user: { userId: 'user-1' },
    })
  })

  it('useSellPanel - initial state - all fields at initial values', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    expect(result.current.isOpen).toBe(false)
    expect(result.current.ticker).toBeNull()
    expect(result.current.maxQuantity).toBeNull()
    expect(result.current.priceSnapshot).toBeNull()
    expect(result.current.idempotencyKey).toBeNull()
    expect(result.current.quantity).toBe('')
    expect(result.current.validationError).toBeNull()
    expect(result.current.isFetchingPrice).toBe(false)
    expect(result.current.priceError).toBeNull()
    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.submitError).toBeNull()
    expect(result.current.result).toBeNull()
  })

  it('useSellPanel - openSellPanel success - isOpen true, priceSnapshot set, idempotencyKey is a string', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    expect(result.current.isOpen).toBe(true)
    expect(result.current.priceSnapshot).toBe(182.5)
    expect(typeof result.current.idempotencyKey).toBe('string')
    expect(result.current.ticker).toBe('AAPL')
    expect(result.current.maxQuantity).toBe(10)
    expect(result.current.isFetchingPrice).toBe(false)
    expect(result.current.priceError).toBeNull()
  })

  it('useSellPanel - openSellPanel failure - isOpen false, priceError set', async () => {
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

  it('useSellPanel - closeSellPanel - resets all fields', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => {
      result.current.closeSellPanel()
    })

    expect(result.current.isOpen).toBe(false)
    expect(result.current.ticker).toBeNull()
    expect(result.current.maxQuantity).toBeNull()
    expect(result.current.priceSnapshot).toBeNull()
    expect(result.current.idempotencyKey).toBeNull()
    expect(result.current.quantity).toBe('')
    expect(result.current.validationError).toBeNull()
    expect(result.current.isFetchingPrice).toBe(false)
    expect(result.current.priceError).toBeNull()
    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.submitError).toBeNull()
    expect(result.current.result).toBeNull()
  })

  it('useSellPanel - setQuantity valid - clears validationError', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => {
      result.current.setQuantity('5')
    })

    expect(result.current.quantity).toBe('5')
    expect(result.current.validationError).toBeNull()
  })

  it('useSellPanel - setQuantity non-numeric - sets correct error', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => {
      result.current.setQuantity('abc')
    })

    expect(result.current.validationError).toBe('Please enter a valid number.')
  })

  it('useSellPanel - setQuantity zero - sets correct error', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => {
      result.current.setQuantity('0')
    })

    expect(result.current.validationError).toBe('Quantity must be greater than zero.')
  })

  it('useSellPanel - setQuantity negative - sets correct error', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    act(() => {
      result.current.setQuantity('-1')
    })

    expect(result.current.validationError).toBe('Quantity must be greater than zero.')
  })

  it('useSellPanel - setQuantity exceeds maxQuantity - sets correct error with maxQuantity value', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 5)
    })

    act(() => {
      result.current.setQuantity('10')
    })

    expect(result.current.validationError).toBe(
      'Quantity cannot exceed your holding of 5 shares.'
    )
  })

  it('useSellPanel - confirmSell FILLED - result set, cache invalidated for holdings and accounts', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })
    const filledResponse = {
      orderId: 'order-1',
      status: 'FILLED' as const,
      ticker: 'AAPL',
      quantity: '5',
      side: 'SELL' as const,
      executionPrice: '182.50',
      totalCost: null,
      totalProceeds: 912.5,
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(filledResponse)

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => {
      result.current.setQuantity('5')
    })

    await act(async () => {
      await result.current.confirmSell()
    })

    await waitFor(() => expect(result.current.result).not.toBeNull())

    expect(result.current.result).toEqual(filledResponse)
    expect(result.current.isSubmitting).toBe(false)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [PORTFOLIO_HOLDINGS_KEY] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [ACCOUNTS_QUERY_KEY] })
  })

  it('useSellPanel - confirmSell REJECTED - result set, no cache invalidation', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })
    const rejectedResponse = {
      orderId: 'order-2',
      status: 'REJECTED' as const,
      ticker: 'AAPL',
      quantity: '5',
      side: 'SELL' as const,
      executionPrice: null,
      totalCost: null,
      totalProceeds: null,
      rejectionReason: 'Insufficient shares',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockPlaceOrder.mockResolvedValueOnce(rejectedResponse)

    const { wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => {
      result.current.setQuantity('5')
    })

    await act(async () => {
      await result.current.confirmSell()
    })

    await waitFor(() => expect(result.current.result).not.toBeNull())

    expect(result.current.result).toEqual(rejectedResponse)
    expect(result.current.isSubmitting).toBe(false)
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('useSellPanel - confirmSell 409 - new idempotencyKey generated, submitError set', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })
    const conflictError = Object.assign(new Error('Conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    mockPlaceOrder.mockRejectedValueOnce(conflictError)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    const originalKey = result.current.idempotencyKey

    act(() => {
      result.current.setQuantity('5')
    })

    await act(async () => {
      await result.current.confirmSell()
    })

    await waitFor(() => expect(result.current.submitError).not.toBeNull())

    expect(result.current.submitError).toBe('Duplicate order detected. Please try again.')
    expect(result.current.idempotencyKey).not.toBe(originalKey)
    expect(typeof result.current.idempotencyKey).toBe('string')
    expect(result.current.isSubmitting).toBe(false)
  })

  it('useSellPanel - confirmSell other error - submitError set', async () => {
    mockFetchIndicativePrice.mockResolvedValueOnce({ ticker: 'AAPL', indicativePrice: 182.5 })
    const serverError = Object.assign(new Error('Server Error'), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockPlaceOrder.mockRejectedValueOnce(serverError)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSellPanel(), { wrapper })

    await act(async () => {
      await result.current.openSellPanel('AAPL', 10)
    })

    act(() => {
      result.current.setQuantity('5')
    })

    await act(async () => {
      await result.current.confirmSell()
    })

    await waitFor(() => expect(result.current.submitError).not.toBeNull())

    expect(result.current.submitError).toBe('Something went wrong. Please try again.')
    expect(result.current.isSubmitting).toBe(false)
  })
})
