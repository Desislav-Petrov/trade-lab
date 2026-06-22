import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useAccounts, useOpenAccount } from '../hooks/useLedger'
import { AccountList } from '../components/AccountList'
import { OpenAccountForm } from '../components/OpenAccountForm'

export function AccountsPage() {
  const user = useSessionStore((s) => s.user)
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | undefined>(undefined)

  const { data, isLoading: isLoadingAccounts } = useAccounts()
  const openAccount = useOpenAccount()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  function handleOpenAccountSubmit(currency: 'USD' | 'GBP' | 'EUR', name?: string) {
    setFormError(undefined)
    openAccount.mutate(
      { userId: user!.userId, currency, name },
      {
        onSuccess: () => {
          setShowForm(false)
        },
        onError: (err) => {
          const axiosError = err as AxiosError
          if (axiosError?.response?.status === 401) {
            navigate('/login', { replace: true })
          } else {
            const message =
              axiosError?.response?.status === 400
                ? 'Invalid request. Please check your input.'
                : axiosError?.response?.status === 403
                  ? 'You are not authorised to open an account.'
                  : 'Something went wrong. Please try again.'
            setFormError(message)
          }
        },
      },
    )
  }

  function handleCancel() {
    setShowForm(false)
    setFormError(undefined)
  }

  const accounts = data?.accounts ?? []

  return (
    <div className="max-w-lg">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">LEDGER</p>
          <h1 className="text-sm font-medium text-[var(--color-text-primary)]">Accounts</h1>
        </div>
        {!showForm && (
          <button
            type="button"
            onClick={() => setShowForm(true)}
            className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
          >
            Open new account
          </button>
        )}
      </div>

      {showForm && (
        <div className="mb-6 rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
          <OpenAccountForm
            onSubmit={handleOpenAccountSubmit}
            isLoading={openAccount.isPending}
            error={formError}
            onCancel={handleCancel}
          />
        </div>
      )}

      {isLoadingAccounts ? (
        <p className="text-xs text-[var(--color-text-muted)]">Loading accounts…</p>
      ) : (
        <AccountList accounts={accounts} />
      )}
    </div>
  )
}
