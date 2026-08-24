import type { AccountResponse } from '../types/account'
import { Button } from '@/shared/components/ui/button'
import { Badge } from '@/shared/components/ui/badge'
import { Card, CardContent } from '@/shared/components/ui/card'

interface AccountListProps {
  accounts: AccountResponse[]
  onTopUp: (account: AccountResponse) => void
  onTransactions: (account: AccountResponse) => void
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'outline' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'SUSPENDED') return 'warning'
  if (status === 'CLOSED') return 'danger'
  return 'outline'
}

export function AccountList({ accounts, onTopUp, onTransactions }: AccountListProps) {
  if (accounts.length === 0) {
    return (
      <p className="text-xs text-[var(--color-text-muted)]">
        No accounts yet. Open one to get started.
      </p>
    )
  }

  return (
    <ul className="flex flex-col gap-3">
      {accounts.map((account) => (
        <li key={account.id}>
          <Card>
            <CardContent className="pt-4">
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
                  <dd>
                    <Badge variant={statusVariant(account.status)}>{account.status}</Badge>
                  </dd>
                </div>
                <div className="flex items-center justify-between">
                  <dt className="text-xs text-[var(--color-text-muted)]">Opened</dt>
                  <dd className="text-xs text-[var(--color-text-primary)]">
                    {new Date(account.createdAt).toLocaleDateString()}
                  </dd>
                </div>
              </dl>
              <div className="mt-3 flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => onTransactions(account)}>
                  Transactions
                </Button>
                <Button variant="outline" size="sm" onClick={() => onTopUp(account)}>
                  Top Up
                </Button>
              </div>
            </CardContent>
          </Card>
        </li>
      ))}
    </ul>
  )
}
