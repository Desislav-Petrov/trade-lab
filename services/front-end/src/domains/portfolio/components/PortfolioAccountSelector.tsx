import type { AccountResponse } from '../../ledger/types/account'

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
    return <p>No accounts available. Open an account first.</p>
  }

  function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    onAccountChange(event.target.value)
  }

  return (
    <select
      value={selectedAccountId ?? ''}
      onChange={handleChange}
      aria-label="Select account"
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
