import { useState } from 'react'
import type { AccountResponse } from '../types/account'

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
        <p
          role="alert"
          className="border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
        >
          {error}
        </p>
      )}

      <div className="flex flex-col gap-1">
        <p className="text-xs text-[var(--color-text-muted)]">
          Account: <span className="text-[var(--color-text-primary)]">{account.name}</span>
          &nbsp;&middot;&nbsp;
          <span className="text-[var(--color-text-primary)]">{account.currency}</span>
        </p>
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="top-up-amount"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Amount
        </label>
        <input
          id="top-up-amount"
          name="amount"
          type="text"
          inputMode="numeric"
          value={amountRaw}
          onChange={handleAmountChange}
          disabled={isLoading}
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none disabled:cursor-not-allowed disabled:opacity-40"
        />
        {touched && validationError && (
          <p role="alert" className="text-xs text-[var(--color-danger)]">
            {validationError}
          </p>
        )}
      </div>

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isConfirmDisabled}
          className="flex-1 rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? 'Confirming…' : 'Confirm'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="rounded border border-[var(--color-border)] px-4 py-2 text-xs text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)]"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
