import { useSellPanel } from '../hooks/useSellPanel'

export interface SellPanelProps {
  ticker: string
  companyName: string
  maxQuantity: number
}

function computeEstimatedProceeds(quantity: string, priceSnapshot: number | null): string {
  const qty = parseFloat(quantity)
  if (isNaN(qty) || priceSnapshot === null) return '—'
  return (qty * priceSnapshot).toFixed(2)
}

function computeTotalProceeds(
  totalProceeds: number | null,
  executionPrice: string | null,
  quantity: string
): string {
  if (totalProceeds !== null) return totalProceeds.toFixed(2)
  const price = parseFloat(executionPrice ?? '')
  const qty = parseFloat(quantity)
  if (isNaN(price) || isNaN(qty)) return '—'
  return (price * qty).toFixed(2)
}

export function SellPanel({ ticker, companyName, maxQuantity }: SellPanelProps) {
  const {
    result,
    quantity,
    validationError,
    priceSnapshot,
    isFetchingPrice,
    isSubmitting,
    submitError,
    setQuantity,
    confirmSell,
    closeSellPanel,
  } = useSellPanel()

  const isLoading = isFetchingPrice || isSubmitting

  if (result?.status === 'FILLED') {
    const totalProceeds = computeTotalProceeds(
      result.totalProceeds,
      result.executionPrice,
      result.quantity
    )
    return (
      <div role="dialog" aria-label="Sell Panel">
        <p>Order filled ✓</p>
        <p>Ticker: {result.ticker}</p>
        <p>Quantity: {result.quantity}</p>
        <p>Execution price: {result.executionPrice}</p>
        <p>Total proceeds: {totalProceeds}</p>
        <button type="button" aria-label="Close" onClick={closeSellPanel}>
          Close
        </button>
      </div>
    )
  }

  if (result?.status === 'REJECTED') {
    return (
      <div role="dialog" aria-label="Sell Panel">
        <p>Order rejected: {result.rejectionReason}</p>
        <button type="button" aria-label="Close" onClick={closeSellPanel}>
          Close
        </button>
      </div>
    )
  }

  const estimatedProceeds = computeEstimatedProceeds(quantity, priceSnapshot)
  const isConfirmDisabled = validationError !== null || quantity === '' || isLoading

  return (
    <div role="dialog" aria-label="Sell Panel">
      <p>{ticker}</p>
      <p>{companyName}</p>

      <select aria-label="Order Type" value="MARKET" disabled onChange={() => undefined}>
        <option value="MARKET">MARKET</option>
      </select>

      <input
        type="number"
        aria-label="Quantity"
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        disabled={isLoading}
      />
      {validationError !== null && <p role="alert">{validationError}</p>}

      <p>Max: {maxQuantity} shares</p>
      <p>Estimated proceeds: {estimatedProceeds}</p>

      {submitError !== null && <p role="alert">{submitError}</p>}

      <button
        type="button"
        aria-label="Confirm sell"
        disabled={isConfirmDisabled}
        onClick={() => void confirmSell()}
      >
        {isSubmitting ? '⏳' : 'Confirm'}
      </button>

      <button
        type="button"
        aria-label="Decline sell"
        disabled={isLoading}
        onClick={closeSellPanel}
      >
        Decline
      </button>
    </div>
  )
}
