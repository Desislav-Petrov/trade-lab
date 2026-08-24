import { useState } from 'react'
import { usePlaceOrder } from '../hooks/usePlaceOrder'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card'

interface BuyPanelProps {
  ticker: string
  companyName: string
  priceSnapshot: string
  accountId: string
  userId: string
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
  if (value === '' || isNaN(Number(value))) return 'Please enter a valid number.'
  const num = parseFloat(value)
  if (num <= 0) return 'Quantity must be greater than zero.'
  return null
}

export function BuyPanel({
  ticker,
  companyName,
  priceSnapshot,
  accountId,
  userId,
  onClose,
}: BuyPanelProps) {
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
    if (error) { setQuantityError(error); return }
    setStage('loading')
    mutation.mutate(
      { idempotencyKey, accountId, userId, ticker, quantity, side: 'BUY', orderType: 'MARKET', priceSnapshot },
      {
        onSuccess: (data) => {
          if (data.status === 'FILLED') {
            setFilledData({ executionPrice: data.executionPrice ?? '—', totalCost: data.totalCost ?? '—', quantity: data.quantity })
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

  if (stage === 'filled' && filledData) {
    return (
      <Card role="dialog" aria-label="Buy Panel" className="w-80 p-4 text-xs">
        <Alert variant="success" className="mb-3">
          <AlertDescription>Order filled ✓</AlertDescription>
        </Alert>
        <p className="mb-1 text-[var(--color-text-primary)]">Ticker: {ticker}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Quantity: {filledData.quantity}</p>
        <p className="mb-1 text-[var(--color-text-primary)]">Execution price: {filledData.executionPrice}</p>
        <p className="mb-3 text-[var(--color-text-primary)]">Total cost: {filledData.totalCost}</p>
        <Button onClick={onClose}>Close</Button>
      </Card>
    )
  }

  if (stage === 'rejected') {
    return (
      <Card role="dialog" aria-label="Buy Panel" className="w-80 p-4 text-xs">
        <Alert variant="destructive" className="mb-3">
          <AlertDescription>Order rejected: {rejectedReason}</AlertDescription>
        </Alert>
        <Button onClick={onClose}>Close</Button>
      </Card>
    )
  }

  const isLoading = stage === 'loading'

  return (
    <Card role="dialog" aria-label="Buy Panel" className="w-80">
      <CardHeader className="pb-2">
        <CardTitle>{ticker}</CardTitle>
        <p className="text-xs text-[var(--color-text-muted)]">{companyName}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="buy-panel-order-type">Order Type</Label>
          <select
            id="buy-panel-order-type"
            value="MARKET"
            disabled
            aria-label="Order Type"
            className="flex h-8 w-full rounded border border-[hsl(var(--border))] bg-transparent px-3 py-1 text-xs text-[hsl(var(--foreground))] disabled:opacity-70"
            onChange={() => undefined}
          >
            <option value="MARKET">MARKET</option>
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="buy-panel-quantity">Quantity</Label>
          <Input
            id="buy-panel-quantity"
            type="number"
            value={quantity}
            onChange={handleQuantityChange}
            onBlur={handleQuantityBlur}
            disabled={isLoading}
            min="0"
            step="any"
            aria-describedby={quantityError ? 'buy-panel-quantity-error' : undefined}
          />
          {quantityError && (
            <p id="buy-panel-quantity-error" role="alert" className="text-xs text-[var(--color-danger)]">
              {quantityError}
            </p>
          )}
        </div>

        <p className="text-xs text-[var(--color-text-muted)]">
          Estimated cost: {isQuantityValid ? computeEstimatedCost(quantity, priceSnapshot) : '—'} (Estimated)
        </p>

        {stage === 'error' && (
          <Alert variant="destructive" role="alert">
            <AlertDescription>Something went wrong. Please try again.</AlertDescription>
          </Alert>
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
            {isLoading ? <span aria-label="Loading">⏳</span> : <span aria-hidden="true">✓</span>}
          </button>
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            aria-label="Decline buy"
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
