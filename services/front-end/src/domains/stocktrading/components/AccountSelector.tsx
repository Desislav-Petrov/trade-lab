import type { AccountResponse } from '../../ledger/types/account'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Skeleton } from '@/shared/components/ui/skeleton'

export interface AccountSelectorProps {
  accounts: AccountResponse[]
  selectedAccountId: string | null
  onSelect: (accountId: string) => void
  isLoading: boolean
  isError: boolean
}

export function AccountSelector({
  accounts,
  selectedAccountId,
  onSelect,
  isLoading,
  isError,
}: AccountSelectorProps) {
  if (isLoading) {
    return <Skeleton className="h-8 w-64" />
  }

  if (isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>Could not load accounts.</AlertDescription>
      </Alert>
    )
  }

  if (accounts.length === 0) {
    return (
      <p className="text-xs text-[var(--color-text-muted)]">
        No accounts available. Open an account first.
      </p>
    )
  }

  function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    onSelect(event.target.value)
  }

  return (
    <select
      value={selectedAccountId ?? ''}
      onChange={handleChange}
      aria-label="Select account"
      className="flex h-8 rounded border border-[hsl(var(--border))] bg-transparent px-3 py-1 text-xs text-[hsl(var(--foreground))] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[hsl(var(--ring))]"
    >
      <option value="" disabled hidden />
      {accounts.map((account) => (
        <option key={account.id} value={account.id}>
          {account.name} ({account.currency})
        </option>
      ))}
    </select>
  )
}
