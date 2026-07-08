import { useState } from 'react'
import { usePlaceOrder } from '../hooks/usePlaceOrder'

interface BuyPanelProps {
  ticker: string
  companyName: string
  priceSnapshot: string
  accountId: string
  onClose: () => void
}

type PanelStage = 'input' | 'loading' | 'filled' | 'rejected' | 'error'

function computeEstimatedCost(quantity: string, priceSnapshot: string): string {
  const qty = parseFloat(quantity)
  const price = parseFloat(priceSnapshot)
  if (isNaN(qty) || isNaN(price)) return '—'
  return (qty * price).toFixed(3)
}

function validateQuantity(value: string): string | null {
  if (value === '' || isNaN(Number(value))) {
    return 'Please enter a valid number.'
  }
  const num = parseFloat(value)
  if (num <= 0) {
    return 'Quantity must be greater than zero.'
  }
  return null
}

export function BuyPanel({ ticker, companyName, priceSnapshot, accountId, onClose }: BuyPanelProps) {
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID())
  const [quantity, setQuantity] = useState<string>('')
  const [quantityError, setQuantityError] = useState<string | null>(null)
  const [stage, setStage] = useState<PanelStage>('input')
  const [filledData, setFilledData] = useState<{
    executionPrice: string
    totalCost: string
    quantity: string
  } | null>(null)
  const [rejectedReason, setRejectedReason] = useState<string | null>(null)

  const mutation = usePlaceOrder()

  function handleQuantityChange(e: React.ChangeEvent<HTMLInputElement>) {
    const value = e.target.value
    setQuantity(value)
    setQuantityError(validateQuantity(value))
  }

  function handleQuantityBlur() {
    setQuantityError(validateQuantity(quantity))
  }

  const isQuantityValid = quantity !== '' && validateQuantity(quantity) === null

  function handleConfirm() {
    const error = validateQuantity(quantity)
    if (error) {
      setQuantityError(error)
      return
    }

    setStage('loading')

    mutation.mutate(
      {
        idempotencyKey,
        accountId,
        ticker,
        quantity,
        orderType: 'MARKET',
        priceSnapshot,
      },
      {
        onSuccess: (data) => {
          if (data.status === 'FILLED') {
            setFilledData({
              executionPrice: data.executionPrice ?? '—',
              totalCost: data.totalCost ?? '—',
              quantity: data.quantity,
            })
            setStage('filled')
          } else {
            setRejectedReason(data.rejectionReason ?? 'Unknown reason')
            setStage('rejected')
          }
        },
        onError: () => {
          setStage('error')
          setIdempotencyKey(crypto.randomUUID())
        },
      },
    )
  }

  function handleDecline() {
    onClose()
  }

  if (stage === 'filled' && filledData) {
    return (
      <div
        role="dialog"
        aria-label="Buy Panel"
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
      >
        <p className="mb-2 font-medium text-[var(--color-success)]">Order filled ✓</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Ticker: {ticker}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Quantity: {filledData.quantity}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Execution price: {filledData.executionPrice}</p>
        <p className="mb-3 text-[var(--color-text-primary)]">Total cost: {filledData.totalCost}</p>
        <button
          type="button"
          onClick={onClose}
          className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
        >
          Close
        </button>
      </div>
    )
  }

  if (stage === 'rejected') {
    return (
      <div
        role="dialog"
        aria-label="Buy Panel"
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
      >
        <p className="mb-2 font-medium text-[var(--color-danger)]">
          Order rejected: {rejectedReason}
        </p>
        <button
          type="button"
          onClick={onClose}
          className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
        >
          Close
        </button>
      </div>
    )
  }

  const isLoading = stage === 'loading'

  return (
    <div
      role="dialog"
      aria-label="Buy Panel"
      className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
    >
      <p className="mb-1 font-medium text-[var(--color-text-primary)]">{ticker}</p>
      <p className="mb-2 text-[var(--color-text-secondary)]">{companyName}</p>

      <div className="mb-2">
        <label
          htmlFor="buy-panel-order-type"
          className="mb-1 block text-[var(--color-text-primary)]"
        >
          Order Type
        </label>
        <select
          id="buy-panel-order-type"
          value="MARKET"
          disabled
          aria-label="Order Type"
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-2 py-1 text-[var(--color-text-primary)] disabled:opacity-70"
          onChange={() => undefined}
        >
          <option value="MARKET">MARKET</option>
        </select>
      </div>

      <div className="mb-2">
        <label htmlFor="buy-panel-quantity" className="mb-1 block text-[var(--color-text-primary)]">
          Quantity
        </label>
        <input
          id="buy-panel-quantity"
          type="number"
          value={quantity}
          onChange={handleQuantityChange}
          onBlur={handleQuantityBlur}
          disabled={isLoading}
          min="0"
          step="any"
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-2 py-1 text-[var(--color-text-primary)]"
          aria-describedby={quantityError ? 'buy-panel-quantity-error' : undefined}
        />
        {quantityError && (
          <p
            id="buy-panel-quantity-error"
            role="alert"
            className="mt-1 text-[var(--color-danger)]"
          >
            {quantityError}
          </p>
        )}
      </div>

      <p className="mb-3 text-[var(--color-text-secondary)]">
        Estimated cost: {isQuantityValid ? computeEstimatedCost(quantity, priceSnapshot) : '—'}
        {' '}(Estimated)
      </p>

      {stage === 'error' && (
        <p role="alert" className="mb-2 text-[var(--color-danger)]">
          Something went wrong. Please try again.
        </p>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={handleConfirm}
          disabled={isLoading || !isQuantityValid}
          aria-label="Confirm buy"
          title="Confirm"
          className="flex h-8 w-8 items-center justify-center rounded bg-[var(--color-success)] text-base font-bold text-[var(--color-bg)] disabled:opacity-50"
        >
          {isLoading ? (
            <span aria-label="Loading">⏳</span>
          ) : (
            <span aria-hidden="true">✓</span>
          )}
        </button>
        <button
          type="button"
          onClick={handleDecline}
          disabled={isLoading}
          aria-label="Decline buy"
          title="Decline"
          className="flex h-8 w-8 items-center justify-center rounded bg-[var(--color-danger)] text-base font-bold text-[var(--color-bg)] disabled:opacity-50"
        >
          <span aria-hidden="true">✗</span>
        </button>
      </div>
    </div>
  )
}
