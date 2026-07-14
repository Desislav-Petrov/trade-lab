import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { SellPanel } from './SellPanel'
import { useSellPanel } from '../hooks/useSellPanel'
import type { PlaceOrderResponse } from '../api/ordersApi'

vi.mock('../hooks/useSellPanel', () => ({
  useSellPanel: vi.fn(),
}))

const mockUseSellPanel = vi.mocked(useSellPanel)

const defaultProps = {
  ticker: 'AAPL',
  companyName: 'Apple Inc.',
  maxQuantity: 10,
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

function setupMockHook(overrides: Partial<ReturnType<typeof useSellPanel>> = {}) {
  const defaults: ReturnType<typeof useSellPanel> = {
    isOpen: true,
    ticker: 'AAPL',
    maxQuantity: 10,
    priceSnapshot: 182.5,
    idempotencyKey: 'test-key',
    quantity: '',
    validationError: null,
    isFetchingPrice: false,
    priceError: null,
    isSubmitting: false,
    submitError: null,
    result: null,
    openSellPanel: vi.fn(),
    closeSellPanel: vi.fn(),
    setQuantity: vi.fn(),
    confirmSell: vi.fn().mockResolvedValue(undefined),
  }
  mockUseSellPanel.mockReturnValue({ ...defaults, ...overrides })
}

function renderSellPanel(props = defaultProps) {
  return render(<SellPanel {...props} />, { wrapper: createWrapper() })
}

describe('SellPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('SellPanel - renders form - shows ticker, company name, order type, quantity input, max quantity hint', () => {
    setupMockHook()
    renderSellPanel()

    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    const orderTypeSelect = screen.getByRole('combobox', { name: 'Order Type' })
    expect(orderTypeSelect).toBeInTheDocument()
    expect(orderTypeSelect).toBeDisabled()
    expect((orderTypeSelect as HTMLSelectElement).value).toBe('MARKET')
    expect(screen.getByLabelText('Quantity')).toBeInTheDocument()
    expect(screen.getByText(/Max: 10 shares/)).toBeInTheDocument()
    expect(screen.getByText(/Estimated proceeds:/)).toBeInTheDocument()
  })

  it('SellPanel - renders form - Confirm button disabled with empty quantity', () => {
    setupMockHook({ quantity: '' })
    renderSellPanel()

    expect(screen.getByRole('button', { name: 'Confirm sell' })).toBeDisabled()
  })

  it('SellPanel - renders form - Decline button is enabled', () => {
    setupMockHook()
    renderSellPanel()

    expect(screen.getByRole('button', { name: 'Decline sell' })).toBeEnabled()
  })

  it('SellPanel - inline validation error - shows error message below quantity input', () => {
    setupMockHook({ validationError: 'Quantity must be greater than zero.', quantity: '0' })
    renderSellPanel()

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Quantity must be greater than zero.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm sell' })).toBeDisabled()
  })

  it('SellPanel - estimated proceeds - updates based on quantity and priceSnapshot', () => {
    setupMockHook({ quantity: '3', priceSnapshot: 182.5 })
    renderSellPanel()

    // 3 × 182.5 = 547.50
    expect(screen.getByText(/547\.50/)).toBeInTheDocument()
  })

  it('SellPanel - loading state isFetchingPrice - Confirm and Decline buttons disabled', () => {
    setupMockHook({ isFetchingPrice: true })
    renderSellPanel()

    expect(screen.getByRole('button', { name: 'Confirm sell' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Decline sell' })).toBeDisabled()
  })

  it('SellPanel - loading state isSubmitting - shows spinner on Confirm button', () => {
    setupMockHook({ isSubmitting: true, quantity: '5' })
    renderSellPanel()

    const confirmBtn = screen.getByRole('button', { name: 'Confirm sell' })
    expect(confirmBtn).toBeInTheDocument()
    expect(screen.getByText('⏳')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Decline sell' })).toBeDisabled()
  })

  it('SellPanel - quantity change - calls setQuantity from hook', () => {
    const setQuantity = vi.fn()
    setupMockHook({ setQuantity })
    renderSellPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '5' } })

    expect(setQuantity).toHaveBeenCalledWith('5')
  })

  it('SellPanel - Confirm clicked with valid quantity - calls confirmSell', async () => {
    const confirmSell = vi.fn().mockResolvedValue(undefined)
    setupMockHook({ quantity: '3', validationError: null, confirmSell })
    renderSellPanel()

    fireEvent.click(screen.getByRole('button', { name: 'Confirm sell' }))

    await waitFor(() => expect(confirmSell).toHaveBeenCalledOnce())
  })

  it('SellPanel - Decline clicked - calls closeSellPanel', () => {
    const closeSellPanel = vi.fn()
    setupMockHook({ closeSellPanel })
    renderSellPanel()

    fireEvent.click(screen.getByRole('button', { name: 'Decline sell' }))

    expect(closeSellPanel).toHaveBeenCalledOnce()
  })

  it('SellPanel - fill confirmation - shows ticker, quantity, execution price, total proceeds, Order filled', () => {
    const filledResult: PlaceOrderResponse = {
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
    setupMockHook({ result: filledResult })
    renderSellPanel()

    expect(screen.getByText(/Order filled ✓/)).toBeInTheDocument()
    expect(screen.getByText(/Ticker: AAPL/)).toBeInTheDocument()
    expect(screen.getByText(/Quantity: 3/)).toBeInTheDocument()
    expect(screen.getByText(/Execution price: 183\.00/)).toBeInTheDocument()
    expect(screen.getByText(/Total proceeds: 549\.00/)).toBeInTheDocument()
  })

  it('SellPanel - fill confirmation Close button - calls closeSellPanel', () => {
    const closeSellPanel = vi.fn()
    const filledResult: PlaceOrderResponse = {
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
    setupMockHook({ result: filledResult, closeSellPanel })
    renderSellPanel()

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(closeSellPanel).toHaveBeenCalledOnce()
  })

  it('SellPanel - rejection message - shows Order rejected with reason', () => {
    const rejectedResult: PlaceOrderResponse = {
      orderId: 'order-2',
      status: 'REJECTED',
      ticker: 'AAPL',
      quantity: '100',
      executionPrice: null,
      totalCost: null,
      totalProceeds: null,
      side: 'SELL',
      rejectionReason: 'Insufficient holdings',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    setupMockHook({ result: rejectedResult })
    renderSellPanel()

    expect(screen.getByText(/Order rejected: Insufficient holdings/)).toBeInTheDocument()
  })

  it('SellPanel - submitError non-REJECTED - shows generic error message', () => {
    setupMockHook({ submitError: 'Something went wrong. Please try again.' })
    renderSellPanel()

    expect(
      screen.getByText('Something went wrong. Please try again.')
    ).toBeInTheDocument()
  })

  it('SellPanel - maxQuantity validation error - shows correct message', () => {
    setupMockHook({
      quantity: '15',
      validationError: 'Quantity cannot exceed your holding of 10 shares.',
    })
    renderSellPanel()

    expect(
      screen.getByText('Quantity cannot exceed your holding of 10 shares.')
    ).toBeInTheDocument()
  })

  it('SellPanel - valid quantity - Confirm button is enabled', () => {
    setupMockHook({ quantity: '5', validationError: null })
    act(() => {})
    renderSellPanel()

    expect(screen.getByRole('button', { name: 'Confirm sell' })).toBeEnabled()
  })
})
