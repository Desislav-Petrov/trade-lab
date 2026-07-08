import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BuyPanel } from './BuyPanel'
import type { PlaceOrderResponse } from '../api/ordersApi'

vi.mock('../hooks/usePlaceOrder', () => ({
  usePlaceOrder: vi.fn(),
}))

import { usePlaceOrder } from '../hooks/usePlaceOrder'
const mockUsePlaceOrder = vi.mocked(usePlaceOrder)

const defaultProps = {
  ticker: 'AAPL',
  companyName: 'Apple Inc.',
  priceSnapshot: '180.000',
  accountId: 'acc-1',
  onClose: vi.fn(),
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

function renderBuyPanel(props = defaultProps) {
  return render(<BuyPanel {...props} />, { wrapper: createWrapper() })
}

function setupMockMutation(overrides: {
  mutate?: ReturnType<typeof vi.fn>
  isPending?: boolean
  isSuccess?: boolean
  isError?: boolean
  data?: PlaceOrderResponse
  error?: unknown
} = {}) {
  mockUsePlaceOrder.mockReturnValue({
    mutate: overrides.mutate ?? vi.fn(),
    isPending: overrides.isPending ?? false,
    isSuccess: overrides.isSuccess ?? false,
    isError: overrides.isError ?? false,
    data: overrides.data,
    error: overrides.error ?? null,
    mutateAsync: vi.fn(),
    reset: vi.fn(),
    status: 'idle',
    variables: undefined,
    context: undefined,
    failureCount: 0,
    failureReason: null,
    submittedAt: 0,
    isPaused: false,
  } as unknown as ReturnType<typeof usePlaceOrder>)
}

describe('BuyPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    defaultProps.onClose = vi.fn()
  })

  it('BuyPanel - initial render - shows ticker, company name, order type dropdown, quantity input and estimated cost', () => {
    setupMockMutation()
    renderBuyPanel()

    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    // Order type must be a disabled select showing MARKET
    const orderTypeSelect = screen.getByRole('combobox', { name: 'Order Type' })
    expect(orderTypeSelect).toBeInTheDocument()
    expect(orderTypeSelect).toBeDisabled()
    expect((orderTypeSelect as HTMLSelectElement).value).toBe('MARKET')
    expect(screen.getByLabelText('Quantity')).toBeInTheDocument()
    expect(screen.getByText(/Estimated cost:/)).toBeInTheDocument()
  })

  it('BuyPanel - initial render - Confirm button is a green tick icon and is disabled with empty quantity', () => {
    setupMockMutation()
    renderBuyPanel()

    const confirmBtn = screen.getByRole('button', { name: 'Confirm buy' })
    expect(confirmBtn).toBeDisabled()
    expect(confirmBtn).toBeInTheDocument()
  })

  it('BuyPanel - initial render - Decline button is a red cross icon and is enabled', () => {
    setupMockMutation()
    renderBuyPanel()

    const declineBtn = screen.getByRole('button', { name: 'Decline buy' })
    expect(declineBtn).toBeEnabled()
    expect(declineBtn).toBeInTheDocument()
  })

  it('BuyPanel - entering valid quantity - updates estimated cost in real time', () => {
    setupMockMutation()
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '2' } })

    // 2 × 180.000 = 360.000
    expect(screen.getByText(/360\.000/)).toBeInTheDocument()
  })

  it('BuyPanel - entering valid quantity - enables Confirm button', () => {
    setupMockMutation()
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '1.5' } })

    expect(screen.getByRole('button', { name: 'Confirm buy' })).toBeEnabled()
  })

  it('BuyPanel - entering zero quantity - shows inline error and disables Confirm', () => {
    setupMockMutation()
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '0' } })
    fireEvent.blur(screen.getByLabelText('Quantity'))

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Quantity must be greater than zero.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm buy' })).toBeDisabled()
  })

  it('BuyPanel - entering negative quantity - shows inline error and disables Confirm', () => {
    setupMockMutation()
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '-1' } })
    fireEvent.blur(screen.getByLabelText('Quantity'))

    expect(screen.getByText('Quantity must be greater than zero.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm buy' })).toBeDisabled()
  })

  it('BuyPanel - entering non-numeric quantity - shows valid number error and disables Confirm', () => {
    setupMockMutation()
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: 'abc' } })
    fireEvent.blur(screen.getByLabelText('Quantity'))

    expect(screen.getByText('Please enter a valid number.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm buy' })).toBeDisabled()
  })

  it('BuyPanel - Confirm clicked with valid quantity - calls usePlaceOrder mutate with correct args', () => {
    const mutate = vi.fn()
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: 'acc-1',
        ticker: 'AAPL',
        quantity: '3',
        orderType: 'MARKET',
        priceSnapshot: '180.000',
      }),
      expect.any(Object),
    )
  })

  it('BuyPanel - Confirm clicked - idempotencyKey is included in mutation call', () => {
    const mutate = vi.fn()
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    const callArgs = mutate.mock.calls[0][0]
    expect(callArgs).toHaveProperty('idempotencyKey')
    expect(typeof callArgs.idempotencyKey).toBe('string')
    expect(callArgs.idempotencyKey.length).toBeGreaterThan(0)
  })

  it('BuyPanel - FILLED response - shows fill confirmation with executionPrice and totalCost', async () => {
    const filledResponse: PlaceOrderResponse = {
      orderId: 'order-1',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '3',
      executionPrice: '181.000',
      totalCost: '543.000',
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }

    const mutate = vi.fn((_vars, callbacks: { onSuccess?: (data: PlaceOrderResponse) => void }) => {
      callbacks.onSuccess?.(filledResponse)
    })
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    await waitFor(() => {
      expect(screen.getByText(/Order filled ✓/)).toBeInTheDocument()
    })
    expect(screen.getByText(/181\.000/)).toBeInTheDocument()
    expect(screen.getByText(/543\.000/)).toBeInTheDocument()
  })

  it('BuyPanel - REJECTED response - shows rejection message with reason', async () => {
    const rejectedResponse: PlaceOrderResponse = {
      orderId: 'order-2',
      status: 'REJECTED',
      ticker: 'AAPL',
      quantity: '100',
      executionPrice: null,
      totalCost: null,
      rejectionReason: 'Insufficient funds',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }

    const mutate = vi.fn((_vars, callbacks: { onSuccess?: (data: PlaceOrderResponse) => void }) => {
      callbacks.onSuccess?.(rejectedResponse)
    })
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '100' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    await waitFor(() => {
      expect(screen.getByText(/Order rejected: Insufficient funds/)).toBeInTheDocument()
    })
  })

  it('BuyPanel - network error - shows generic error message and re-enables buttons', async () => {
    const mutate = vi.fn((_vars, callbacks: { onError?: () => void }) => {
      callbacks.onError?.()
    })
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument()
    })
    // Buttons should be re-enabled (stage back to 'error' allows interaction)
    expect(screen.getByRole('button', { name: 'Confirm buy' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Decline buy' })).toBeEnabled()
  })

  it('BuyPanel - network error - generates new idempotencyKey for retry', async () => {
    const mutate = vi.fn((_vars, callbacks: { onError?: () => void }) => {
      callbacks.onError?.()
    })
    setupMockMutation({ mutate })
    renderBuyPanel()

    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument()
    })

    const firstKey = mutate.mock.calls[0][0].idempotencyKey

    // Retry: click confirm again
    fireEvent.click(screen.getByRole('button', { name: 'Confirm buy' }))

    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(2))

    const secondKey = mutate.mock.calls[1][0].idempotencyKey
    expect(firstKey).not.toBe(secondKey)
  })

  it('BuyPanel - Decline clicked - calls onClose without API call', () => {
    const mutate = vi.fn()
    const onClose = vi.fn()
    setupMockMutation({ mutate })
    render(
      <BuyPanel
        ticker="AAPL"
        companyName="Apple Inc."
        priceSnapshot="180.000"
        accountId="acc-1"
        onClose={onClose}
      />,
      { wrapper: createWrapper() },
    )

    fireEvent.click(screen.getByRole('button', { name: 'Decline buy' }))

    expect(onClose).toHaveBeenCalledOnce()
    expect(mutate).not.toHaveBeenCalled()
  })
})
