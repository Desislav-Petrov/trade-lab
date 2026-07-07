import type { AccountResponse } from '../../ledger/types/account'

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
    return <p>Loading accounts…</p>
  }

  if (isError) {
    return <p role="alert">Could not load accounts.</p>
  }

  if (accounts.length === 0) {
    return <p>No accounts available. Open an account first.</p>
  }

  function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    onSelect(event.target.value)
  }

  return (
    <select value={selectedAccountId ?? ''} onChange={handleChange} aria-label="Select account">
      {accounts.map((account) => (
        <option key={account.id} value={account.id}>
          {account.name} ({account.currency})
        </option>
      ))}
    </select>
  )
}
