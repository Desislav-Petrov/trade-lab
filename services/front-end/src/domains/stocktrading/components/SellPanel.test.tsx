import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SellPanel } from './SellPanel'
import type { SellPanelHook } from '../hooks/useSellPanel'
import type { PlaceOrderResponse } from '../api/ordersApi'

vi.mock('../hooks/useSellPanel')

import { useSellPanel } from '../hooks/useSellPanel'

const mockUseSellPanel = vi.mocked(useSellPanel)

function buildHookState(overrides: Partial<SellPanelHook> = {}): SellPanelHook {
  return {
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
    confirmSell: vi.fn(),
    ...overrides,
  }
}

const defaultProps = {
  ticker: 'AAPL',
  companyName: 'Apple Inc.',
  maxQuantity: 10,
}

describe('SellPanel', () => {
  beforeEach(() => vi.clearAllMocks())

  it('SellPanel - renders form - shows ticker, company name, order type, quantity input, max hint', () => {
    mockUseSellPanel.mockReturnValue(buildHookState())

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /order type/i })).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: /quantity/i })).toBeInTheDocument()
    expect(screen.getByText(/max: 10 shares/i)).toBeInTheDocument()
  })

  it('SellPanel - confirm button disabled when validationError set', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ quantity: '0', validationError: 'Quantity must be greater than zero.' })
    )

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByRole('button', { name: /confirm sell/i })).toBeDisabled()
  })

  it('SellPanel - confirm button disabled when quantity is empty', () => {
    mockUseSellPanel.mockReturnValue(buildHookState({ quantity: '' }))

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByRole('button', { name: /confirm sell/i })).toBeDisabled()
  })

  it('SellPanel - confirm button enabled when quantity valid and no validationError', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ quantity: '5', validationError: null })
    )

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByRole('button', { name: /confirm sell/i })).not.toBeDisabled()
  })

  it('SellPanel - confirm button shows ✓ icon when not submitting', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ quantity: '5', validationError: null, isSubmitting: false })
    )

    render(<SellPanel {...defaultProps} />)

    const confirmBtn = screen.getByRole('button', { name: /confirm sell/i })
    expect(confirmBtn).toHaveTextContent('✓')
  })

  it('SellPanel - decline button shows ✗ icon', () => {
    mockUseSellPanel.mockReturnValue(buildHookState())

    render(<SellPanel {...defaultProps} />)

    const declineBtn = screen.getByRole('button', { name: /decline sell/i })
    expect(declineBtn).toHaveTextContent('✗')
  })

  it('SellPanel - decline button calls closeSellPanel', () => {
    const closeSellPanel = vi.fn()
    mockUseSellPanel.mockReturnValue(buildHookState({ closeSellPanel }))

    render(<SellPanel {...defaultProps} />)

    fireEvent.click(screen.getByRole('button', { name: /decline sell/i }))
    expect(closeSellPanel).toHaveBeenCalledOnce()
  })

  it('SellPanel - setQuantity called when quantity input changes', () => {
    const setQuantity = vi.fn()
    mockUseSellPanel.mockReturnValue(buildHookState({ setQuantity }))

    render(<SellPanel {...defaultProps} />)

    fireEvent.change(screen.getByRole('spinbutton', { name: /quantity/i }), {
      target: { value: '3' },
    })
    expect(setQuantity).toHaveBeenCalledWith('3')
  })

  it('SellPanel - validation error shown as role="alert"', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({
        quantity: 'abc',
        validationError: 'Please enter a valid number.',
      })
    )

    render(<SellPanel {...defaultProps} />)

    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((el) => el.textContent === 'Please enter a valid number.')).toBe(true)
  })

  it('SellPanel - estimated proceeds computed from quantity × priceSnapshot', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ quantity: '4', priceSnapshot: 182.5 })
    )

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByText(/estimated proceeds:.*730\.00/i)).toBeInTheDocument()
  })

  it('SellPanel - estimated proceeds shows dash when quantity empty', () => {
    mockUseSellPanel.mockReturnValue(buildHookState({ quantity: '', priceSnapshot: 182.5 }))

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByText(/estimated proceeds:.*—/i)).toBeInTheDocument()
  })

  it('SellPanel - confirm and decline disabled when isFetchingPrice=true', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ isFetchingPrice: true, quantity: '5', validationError: null })
    )

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByRole('button', { name: /confirm sell/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /decline sell/i })).toBeDisabled()
  })

  it('SellPanel - spinner shown when isSubmitting=true', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ isSubmitting: true, quantity: '5', validationError: null })
    )

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByRole('button', { name: /confirm sell/i })).toHaveTextContent('⏳')
  })

  it('SellPanel - fill confirmation view shown correctly', () => {
    const filledResult: PlaceOrderResponse = {
      orderId: 'order-1',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '5',
      side: 'SELL',
      executionPrice: '182.50',
      totalCost: null,
      totalProceeds: 912.5,
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockUseSellPanel.mockReturnValue(buildHookState({ result: filledResult }))

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByText(/order filled ✓/i)).toBeInTheDocument()
    expect(screen.getByText(/ticker: aapl/i)).toBeInTheDocument()
    expect(screen.getByText(/quantity: 5/i)).toBeInTheDocument()
    expect(screen.getByText(/execution price: 182\.50/i)).toBeInTheDocument()
    expect(screen.getByText(/total proceeds: 912\.50/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^close$/i })).toBeInTheDocument()
  })

  it('SellPanel - fill confirmation computes proceeds when totalProceeds is null', () => {
    const filledResult: PlaceOrderResponse = {
      orderId: 'order-2',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '4',
      side: 'SELL',
      executionPrice: '182.50',
      totalCost: null,
      totalProceeds: null,
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockUseSellPanel.mockReturnValue(buildHookState({ result: filledResult }))

    render(<SellPanel {...defaultProps} />)

    // 4 * 182.50 = 730.00
    expect(screen.getByText(/total proceeds: 730\.00/i)).toBeInTheDocument()
  })

  it('SellPanel - rejection view shows rejection reason', () => {
    const rejectedResult: PlaceOrderResponse = {
      orderId: 'order-3',
      status: 'REJECTED',
      ticker: 'AAPL',
      quantity: '5',
      side: 'SELL',
      executionPrice: null,
      totalCost: null,
      totalProceeds: null,
      rejectionReason: 'Insufficient shares',
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockUseSellPanel.mockReturnValue(buildHookState({ result: rejectedResult }))

    render(<SellPanel {...defaultProps} />)

    expect(screen.getByText(/order rejected: insufficient shares/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^close$/i })).toBeInTheDocument()
  })

  it('SellPanel - submitError shown as role="alert"', () => {
    mockUseSellPanel.mockReturnValue(
      buildHookState({ submitError: 'Something went wrong. Please try again.' })
    )

    render(<SellPanel {...defaultProps} />)

    const alerts = screen.getAllByRole('alert')
    expect(
      alerts.some((el) => el.textContent === 'Something went wrong. Please try again.')
    ).toBe(true)
  })

  it('SellPanel - close button calls closeSellPanel in fill view', () => {
    const closeSellPanel = vi.fn()
    const filledResult: PlaceOrderResponse = {
      orderId: 'order-1',
      status: 'FILLED',
      ticker: 'AAPL',
      quantity: '5',
      side: 'SELL',
      executionPrice: '182.50',
      totalCost: null,
      totalProceeds: 912.5,
      rejectionReason: null,
      accountId: 'acc-1',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mockUseSellPanel.mockReturnValue(buildHookState({ result: filledResult, closeSellPanel }))

    render(<SellPanel {...defaultProps} />)

    fireEvent.click(screen.getByRole('button', { name: /^close$/i }))
    expect(closeSellPanel).toHaveBeenCalledOnce()
  })
})
