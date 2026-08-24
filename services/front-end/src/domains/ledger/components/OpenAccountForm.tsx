import { useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Label } from '@/shared/components/ui/label'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

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
        <Alert variant="destructive" role="alert">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="currency">Base currency</Label>
        <select
          id="currency"
          name="currency"
          defaultValue=""
          className="flex h-8 w-full rounded border border-[hsl(var(--border))] bg-transparent px-3 py-1 text-xs text-[hsl(var(--foreground))] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[hsl(var(--ring))]"
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

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="name">Account name (optional)</Label>
        <Input id="name" name="name" type="text" />
      </div>

      <div className="flex gap-2">
        <Button type="submit" className="flex-1" disabled={isLoading}>
          {isLoading ? 'Opening…' : 'Open account'}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
