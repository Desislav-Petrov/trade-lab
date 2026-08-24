import { useState } from 'react'
import { Navigate, useNavigate } from '@tanstack/react-router'
import type { AxiosError } from 'axios'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useAccounts, useOpenAccount, useTopUpAccount } from '../hooks/useLedger'
import { AccountList } from '../components/AccountList'
import { OpenAccountForm } from '../components/OpenAccountForm'
import { TopUpModal } from '../components/TopUpModal'
import type { AccountResponse } from '../types/account'
import { Button } from '@/shared/components/ui/button'
import { Card, CardContent } from '@/shared/components/ui/card'
import { Skeleton } from '@/shared/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'

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
            navigate({ to: '/login', replace: true })
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

  function handleTransactions(account: AccountResponse) {
    navigate({
      to: '/accounts/$accountId/transactions',
      params: { accountId: account.id },
      state: (prev) => ({ ...prev, accountName: account.name, currency: account.currency }),
    })
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
          <Button onClick={() => setShowForm(true)}>Open new account</Button>
        )}
      </div>

      {showForm && (
        <Card className="mb-6">
          <CardContent className="pt-4">
            <OpenAccountForm
              onSubmit={handleOpenAccountSubmit}
              isLoading={openAccount.isPending}
              error={formError}
              onCancel={handleCancel}
            />
          </CardContent>
        </Card>
      )}

      {isLoadingAccounts ? (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 w-full" />
          ))}
        </div>
      ) : (
        <AccountList
          accounts={accounts}
          onTopUp={(account) => {
            topUpAccount.reset()
            setTopUpError(undefined)
            setSelectedAccount(account)
          }}
          onTransactions={handleTransactions}
        />
      )}

      <Dialog
        open={selectedAccount !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedAccount(null)
            setTopUpError(undefined)
            topUpAccount.reset()
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Top Up</DialogTitle>
          </DialogHeader>
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
                    onSuccess: () => {},
                    onError: (err) => {
                      const axiosError = err as AxiosError
                      const status = axiosError?.response?.status
                      if (status === 401) {
                        navigate({ to: '/login', replace: true })
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
        </DialogContent>
      </Dialog>
    </div>
  )
}
