import { useState } from 'react'

interface OpenAccountFormProps {
  onSubmit: (currency: 'USD' | 'GBP' | 'EUR', name?: string) => void
  isLoading: boolean
  error?: string
  onCancel: () => void
}

export function OpenAccountForm({ onSubmit, isLoading, error, onCancel }: OpenAccountFormProps) {
  const [validationError, setValidationError] = useState<string | null>(null)

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const currency = (form.elements.namedItem('currency') as HTMLSelectElement).value as
      | 'USD'
      | 'GBP'
      | 'EUR'
      | ''
    const name =
      ((form.elements.namedItem('name') as HTMLInputElement).value ?? '').trim() || undefined

    if (!currency) {
      setValidationError('Please select a base currency.')
      return
    }

    setValidationError(null)
    onSubmit(currency, name)
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
        <label
          htmlFor="currency"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Base currency
        </label>
        <select
          id="currency"
          name="currency"
          defaultValue=""
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        >
          <option value="" disabled>
            — select currency —
          </option>
          <option value="USD">USD</option>
          <option value="GBP">GBP</option>
          <option value="EUR">EUR</option>
        </select>
        {validationError && (
          <p role="alert" className="text-xs text-[var(--color-danger)]">
            {validationError}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="name"
          className="text-xs uppercase tracking-wider text-[var(--color-text-muted)]"
        >
          Account name (optional)
        </label>
        <input
          id="name"
          name="name"
          type="text"
          className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
      </div>

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isLoading}
          className="flex-1 rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? 'Opening…' : 'Open account'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-[var(--color-border)] px-4 py-2 text-xs text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)]"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
