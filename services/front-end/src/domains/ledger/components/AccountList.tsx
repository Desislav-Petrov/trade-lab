import { useNavigate } from 'react-router-dom'
import type { AccountResponse } from '../types/account'

interface AccountListProps {
  accounts: AccountResponse[]
  onTopUp: (account: AccountResponse) => void
}

export function AccountList({ accounts, onTopUp }: AccountListProps) {
  const navigate = useNavigate()

  if (accounts.length === 0) {
    return (
      <p className="text-xs text-[var(--color-text-muted)]">
        No accounts yet. Open one to get started.
      </p>
    )
  }

  function handleTransactions(account: AccountResponse) {
    navigate(`/accounts/${account.id}/transactions`, {
      state: { accountName: account.name, currency: account.currency },
    })
  }

  return (
    <ul className="flex flex-col gap-3">
      {accounts.map((account) => (
        <li
          key={account.id}
          className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
        >
          <dl className="flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <dt className="sr-only">Name</dt>
              <dd className="text-xs font-medium text-[var(--color-text-primary)]">
                {account.name}
              </dd>
              <dt className="sr-only">Currency</dt>
              <dd className="text-xs text-[var(--color-text-muted)]">{account.currency}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-xs text-[var(--color-text-muted)]">Balance</dt>
              <dd className="text-xs text-[var(--color-text-primary)]">
                {account.balance.toFixed(2)}
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-xs text-[var(--color-text-muted)]">Status</dt>
              <dd className="text-xs text-[var(--color-text-primary)]">{account.status}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-xs text-[var(--color-text-muted)]">Opened</dt>
              <dd className="text-xs text-[var(--color-text-primary)]">
                {new Date(account.createdAt).toLocaleDateString()}
              </dd>
            </div>
          </dl>
          <div className="mt-3 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => handleTransactions(account)}
              className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1 text-xs text-[var(--color-text-primary)] hover:border-[var(--color-text-muted)]"
            >
              Transactions
            </button>
            <button
              type="button"
              onClick={() => onTopUp(account)}
              className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1 text-xs text-[var(--color-text-primary)] hover:border-[var(--color-text-muted)]"
            >
              Top Up
            </button>
          </div>
        </li>
      ))}
    </ul>
  )
}
