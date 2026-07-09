import type { AccountResponse } from '../../ledger/types/account'

export interface PortfolioAccountSelectorProps {
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
      <p className="text-xs text-[var(--color-text-muted)]">
        No accounts available. Open an account first.
      </p>
    )
  }

  const handleChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    onAccountChange(event.target.value)
  }

  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor="portfolio-account-selector"
        className="text-xs font-medium text-[var(--color-text-primary)]"
      >
        Select Account
      </label>
      <select
        id="portfolio-account-selector"
        value={selectedAccountId ?? ''}
        onChange={handleChange}
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-xs text-[var(--color-text-primary)] hover:border-[var(--color-text-muted)] focus:border-[var(--color-text-primary)] focus:outline-none"
      >
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {account.name} ({account.currency})
          </option>
        ))}
      </select>
    </div>
  )
}
