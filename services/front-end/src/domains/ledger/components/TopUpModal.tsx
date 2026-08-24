import { useState } from 'react'
import type { AccountResponse } from '../types/account'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

interface TopUpModalProps {
  account: AccountResponse
  onConfirm: (amount: number) => void
  onClose: () => void
  isLoading: boolean
  isSuccess: boolean
  error?: string
}

function validateAmount(raw: string): string | null {
  if (raw === '') return null
  if (raw.includes('.')) return 'Amount must be a whole number.'
  const parsed = Number(raw)
  if (!Number.isInteger(parsed)) return 'Amount must be a whole number.'
  if (parsed < 1) return 'Amount must be at least 1.'
  if (parsed > 10_000_000) return 'Amount must not exceed 10,000,000.'
  return null
}

export function TopUpModal({
  account,
  onConfirm,
  onClose,
  isLoading,
  isSuccess,
  error,
}: TopUpModalProps) {
  const [amountRaw, setAmountRaw] = useState<string>('')
  const [touched, setTouched] = useState<boolean>(false)

  const validationError = validateAmount(amountRaw)
  const isFieldEmpty = amountRaw.trim() === ''
  const isConfirmDisabled = isLoading || isFieldEmpty || validationError !== null

  function handleAmountChange(e: React.ChangeEvent<HTMLInputElement>) {
    setTouched(true)
    setAmountRaw(e.target.value)
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setTouched(true)
    if (isConfirmDisabled) return
    onConfirm(parseInt(amountRaw, 10))
  }

  if (isSuccess) {
    return (
      <div className="flex flex-col items-center gap-3 py-4">
        <span className="text-2xl text-[var(--color-accent)]">&#10003;</span>
        <p className="text-xs text-[var(--color-text-primary)]">Top up successful</p>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {error && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <p className="text-xs text-[var(--color-text-muted)]">
        Account: <span className="text-[var(--color-text-primary)]">{account.name}</span>
        &nbsp;&middot;&nbsp;
        <span className="text-[var(--color-text-primary)]">{account.currency}</span>
      </p>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="top-up-amount">Amount</Label>
        <Input
          id="top-up-amount"
          name="amount"
          type="text"
          inputMode="numeric"
          value={amountRaw}
          onChange={handleAmountChange}
          disabled={isLoading}
        />
        {touched && validationError && (
          <p role="alert" className="text-xs text-[var(--color-danger)]">
            {validationError}
          </p>
        )}
      </div>

      <div className="flex gap-2">
        <Button type="submit" className="flex-1" disabled={isConfirmDisabled}>
          {isLoading ? 'Confirming…' : 'Confirm'}
        </Button>
        <Button type="button" variant="outline" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
