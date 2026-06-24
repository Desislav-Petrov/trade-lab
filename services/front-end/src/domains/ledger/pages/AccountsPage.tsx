import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useAccounts, useOpenAccount, useTopUpAccount } from '../hooks/useLedger'
import { AccountList } from '../components/AccountList'
import { OpenAccountForm } from '../components/OpenAccountForm'
import { TopUpModal } from '../components/TopUpModal'
import type { AccountResponse } from '../types/account'

export function AccountsPage() {
  const user = useSessionStore((s) => s.user)
  const navigate = useNavigate()
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | undefined>(undefined)
  const [selectedAccount, setSelectedAccount] = useState<AccountResponse | null>(null)
  const [topUpError, setTopUpError] = useState<string | undefined>(undefined)

  const { data, isLoading: isLoadingAccounts } = useAccounts()
  const openAccount = useOpenAccount()
  const topUpAccount = useTopUpAccount()

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
        <AccountList
          accounts={accounts}
          onTopUp={(account) => {
            topUpAccount.reset()
            setTopUpError(undefined)
            setSelectedAccount(account)
          }}
        />
      )}

      {selectedAccount && (
        <TopUpModal
          account={selectedAccount}
          isLoading={topUpAccount.isPending}
          isSuccess={topUpAccount.isSuccess}
          error={topUpError}
          onConfirm={(amount) => {
            topUpAccount.mutate(
              { accountId: selectedAccount.id, request: { userId: user!.userId, amount } },
              {
                onSuccess: () => {
                  // isSuccess will be true — TopUpModal shows confirmation
                  // onClose will clear selectedAccount after user dismisses
                },
                onError: (err) => {
                  const axiosError = err as AxiosError
                  const status = axiosError?.response?.status
                  if (status === 401) {
                    navigate('/login', { replace: true })
                  } else {
                    setTopUpError(
                      status === 400
                        ? 'Invalid amount. Please check your input.'
                        : status === 403
                          ? 'This account is not available for top-up.'
                          : status === 404
                            ? 'Account not found.'
                            : 'Something went wrong. Please try again.',
                    )
                  }
                },
              },
            )
          }}
          onClose={() => {
            setSelectedAccount(null)
            setTopUpError(undefined)
            topUpAccount.reset()
          }}
        />
      )}
    </div>
  )
}
