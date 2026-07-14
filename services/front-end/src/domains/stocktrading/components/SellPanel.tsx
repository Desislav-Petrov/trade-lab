import { useSellPanel } from '../hooks/useSellPanel'

export interface SellPanelProps {
  ticker: string
  companyName: string
  maxQuantity: number
}

function computeEstimatedProceeds(quantity: string, priceSnapshot: number | null): string {
  if (priceSnapshot === null) return '—'
  const qty = parseFloat(quantity)
  if (isNaN(qty) || qty <= 0) return '—'
  return (qty * priceSnapshot).toFixed(2)
}

export function SellPanel({ ticker, companyName, maxQuantity }: SellPanelProps) {
  const {
    quantity,
    validationError,
    priceSnapshot,
    isFetchingPrice,
    isSubmitting,
    submitError,
    result,
    setQuantity,
    confirmSell,
    closeSellPanel,
  } = useSellPanel()

  const isLoading = isFetchingPrice || isSubmitting

  if (result && result.status === 'FILLED') {
    const executionPrice = parseFloat(result.executionPrice ?? '0')
    const qty = parseFloat(result.quantity)
    const totalProceeds = result.totalProceeds ?? (isNaN(executionPrice) || isNaN(qty) ? 0 : executionPrice * qty)
    return (
      <div
        role="dialog"
        aria-label="Sell Panel"
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
      >
        <p className="mb-2 font-medium text-[var(--color-success)]">Order filled ✓</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Ticker: {result.ticker}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Quantity: {result.quantity}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">
          Execution price: {result.executionPrice ?? '—'}
        </p>
        <p className="mb-3 text-[var(--color-text-primary)]">
          Total proceeds: {totalProceeds.toFixed(2)}
        </p>
        <button
          type="button"
          onClick={closeSellPanel}
          className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
        >
          Close
        </button>
      </div>
    )
  }

  if (result && result.status === 'REJECTED') {
    return (
      <div
        role="dialog"
        aria-label="Sell Panel"
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
      >
        <p className="mb-2 font-medium text-[var(--color-danger)]">
          Order rejected: {result.rejectionReason ?? 'Unknown reason'}
        </p>
        <button
          type="button"
          onClick={closeSellPanel}
          className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
        >
          Close
        </button>
      </div>
    )
  }

  const isConfirmDisabled = validationError !== null || quantity === '' || isLoading
  const estimatedProceeds = computeEstimatedProceeds(quantity, priceSnapshot)

  return (
    <div
      role="dialog"
      aria-label="Sell Panel"
      className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs"
    >
      <p className="mb-1 font-medium text-[var(--color-text-primary)]">{ticker}</p>
      <p className="mb-2 text-[var(--color-text-secondary)]">{companyName}</p>

      <div className="mb-2">
        <label
          htmlFor="sell-panel-order-type"
          className="mb-1 block text-[var(--color-text-primary)]"
        >
          Order Type
        </label>
        <select
          id="sell-panel-order-type"
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
        <label
          htmlFor="sell-panel-quantity"
          className="mb-1 block text-[var(--color-text-primary)]"
        >
          Quantity
        </label>
        <input
          id="sell-panel-quantity"
          type="number"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          disabled={isLoading}
          min="0"
          step="any"
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-2 py-1 text-[var(--color-text-primary)]"
          aria-describedby={validationError ? 'sell-panel-quantity-error' : undefined}
        />
        {validationError && (
          <p
            id="sell-panel-quantity-error"
            role="alert"
            className="mt-1 text-[var(--color-danger)]"
          >
            {validationError}
          </p>
        )}
        <p className="mt-1 text-[var(--color-text-secondary)]">Max: {maxQuantity} shares</p>
      </div>

      <p className="mb-3 text-[var(--color-text-secondary)]">
        Estimated proceeds: {estimatedProceeds}
      </p>

      {submitError && (
        <p role="alert" className="mb-2 text-[var(--color-danger)]">
          {submitError}
        </p>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => void confirmSell()}
          disabled={isConfirmDisabled}
          aria-label="Confirm sell"
          title="Confirm"
          className="flex h-8 w-8 items-center justify-center rounded bg-[var(--color-success)] text-base font-bold text-[var(--color-bg)] disabled:opacity-50"
        >
          {isSubmitting ? (
            <span aria-label="Loading">⏳</span>
          ) : (
            <span aria-hidden="true">✓</span>
          )}
        </button>
        <button
          type="button"
          onClick={closeSellPanel}
          disabled={isLoading}
          aria-label="Decline sell"
          title="Decline"
          className="flex h-8 w-8 items-center justify-center rounded bg-[var(--color-danger)] text-base font-bold text-[var(--color-bg)] disabled:opacity-50"
        >
          <span aria-hidden="true">✗</span>
        </button>
      </div>
    </div>
  )
}
