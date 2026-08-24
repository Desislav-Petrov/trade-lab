import { useSellPanel } from '../hooks/useSellPanel'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card'

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
  quantity: string,
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
    const totalProceeds = computeTotalProceeds(result.totalProceeds, result.executionPrice, result.quantity)
    return (
      <Card role="dialog" aria-label="Sell Panel" className="w-80 p-4 text-xs">
        <Alert variant="success" className="mb-3">
          <AlertDescription>Order filled ✓</AlertDescription>
        </Alert>
        <p className="mb-1 text-[var(--color-text-primary)]">Ticker: {result.ticker}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Quantity: {result.quantity}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Execution price: {result.executionPrice}</p>
        <p className="mb-3 text-[var(--color-text-primary)]">Total proceeds: {totalProceeds}</p>
        <Button aria-label="Close" onClick={closeSellPanel}>Close</Button>
      </Card>
    )
  }

  if (result?.status === 'REJECTED') {
    return (
      <Card role="dialog" aria-label="Sell Panel" className="w-80 p-4 text-xs">
        <Alert variant="destructive" className="mb-3">
          <AlertDescription>Order rejected: {result.rejectionReason}</AlertDescription>
        </Alert>
        <Button aria-label="Close" onClick={closeSellPanel}>Close</Button>
      </Card>
    )
  }

  const estimatedProceeds = computeEstimatedProceeds(quantity, priceSnapshot)
  const isConfirmDisabled = validationError !== null || quantity === '' || isLoading

  return (
    <Card role="dialog" aria-label="Sell Panel" className="w-80">
      <CardHeader className="pb-2">
        <CardTitle>{ticker}</CardTitle>
        <p className="text-xs text-[var(--color-text-muted)]">{companyName}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="sell-panel-order-type">Order Type</Label>
          <select
            id="sell-panel-order-type"
            aria-label="Order Type"
            value="MARKET"
            disabled
            className="flex h-8 w-full rounded border border-[hsl(var(--border))] bg-transparent px-3 py-1 text-xs text-[hsl(var(--foreground))] disabled:opacity-70"
            onChange={() => undefined}
          >
            <option value="MARKET">MARKET</option>
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="sell-panel-quantity">Quantity</Label>
          <Input
            id="sell-panel-quantity"
            type="number"
            aria-label="Quantity"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            disabled={isLoading}
            min="0"
            step="any"
            aria-describedby={validationError ? 'sell-panel-quantity-error' : undefined}
          />
          {validationError !== null && (
            <p id="sell-panel-quantity-error" role="alert" className="text-xs text-[var(--color-danger)]">
              {validationError}
            </p>
          )}
        </div>

        <p className="text-xs text-[var(--color-text-muted)]">Max: {maxQuantity} shares</p>
        <p className="text-xs text-[var(--color-text-muted)]">Estimated proceeds: {estimatedProceeds}</p>

        {submitError !== null && (
          <Alert variant="destructive" role="alert">
            <AlertDescription>{submitError}</AlertDescription>
          </Alert>
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
            {isSubmitting ? <span aria-label="Loading">⏳</span> : <span aria-hidden="true">✓</span>}
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
      </CardContent>
    </Card>
  )
}
