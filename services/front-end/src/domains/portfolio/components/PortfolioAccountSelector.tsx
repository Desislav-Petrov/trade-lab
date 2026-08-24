import type { AccountResponse } from '../../ledger/types/account'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

interface PortfolioAccountSelectorProps {
  accounts: AccountResponse[]
  selectedAccountId: string | null
  onAccountChange: (accountId: string) => void
}

export function PortfolioAccountSelector({
  accounts,
  selectedAccountId,
  onAccountChange,
}: PortfolioAccountSelectorProps) {
  if (accounts.length === 0) {
    return (
      <Alert>
        <AlertDescription>No accounts available. Open an account first.</AlertDescription>
      </Alert>
    )
  }

  function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    onAccountChange(event.target.value)
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
