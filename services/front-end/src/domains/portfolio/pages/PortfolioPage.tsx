import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import { usePortfolioHoldings } from '../hooks/usePortfolioHoldings'
import { PortfolioAccountSelector } from '../components/PortfolioAccountSelector'
import { PortfolioHoldingsTable } from '../components/PortfolioHoldingsTable'

export function PortfolioPage() {
  const navigate = useNavigate()
  const user = useSessionStore((s) => s.user)
  const userId = user?.userId ?? ''
  
  const selectedAccountId = usePortfolioStore((s) => s.selectedAccountId)
  const setSelectedAccountId = usePortfolioStore((s) => s.setSelectedAccountId)

  const { data: accountsData, isLoading: isLoadingAccounts, isError: isAccountsError } = useActiveAccounts()
  const accounts = accountsData ?? []

  const {
    data: holdingsData,
    isLoading: isLoadingHoldings,
    isError: isHoldingsError,
    error: holdingsError,
  } = usePortfolioHoldings(selectedAccountId, userId)

  // Apply default account selection on mount
  useEffect(() => {
    if (accounts.length > 0 && selectedAccountId === null) {
      setSelectedAccountId(accounts[0].id)
    }
  }, [accounts, selectedAccountId, setSelectedAccountId])

  // Handle authentication errors
  useEffect(() => {
    if (isHoldingsError && holdingsError) {
      const error = holdingsError as { response?: { status?: number } }
      if (error.response?.status === 401) {
        navigate('/login')
      }
    }
  }, [isHoldingsError, holdingsError, navigate])

  const handleAccountChange = (accountId: string) => {
    setSelectedAccountId(accountId)
  }

  // Account loading state
  if (isLoadingAccounts) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <p className="text-xs text-[var(--color-text-muted)]">Loading accounts...</p>
      </div>
    )
  }

  // Account fetch error
  if (isAccountsError) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <p className="text-xs text-[var(--color-error)]">Could not load accounts.</p>
      </div>
    )
  }

  // No accounts available
  if (accounts.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <PortfolioAccountSelector
          accounts={accounts}
          selectedAccountId={selectedAccountId}
          onAccountChange={handleAccountChange}
        />
      </div>
    )
  }

  // Holdings loading state
  if (isLoadingHoldings) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <PortfolioAccountSelector
          accounts={accounts}
          selectedAccountId={selectedAccountId}
          onAccountChange={handleAccountChange}
        />
        <p className="text-xs text-[var(--color-text-muted)]">Loading portfolio...</p>
      </div>
    )
  }

  // Holdings error handling
  if (isHoldingsError && holdingsError) {
    const error = holdingsError as { response?: { status?: number; data?: { error?: string } } }
    const status = error.response?.status
    const errorMessage = error.response?.data?.error

    let displayMessage = 'Could not load portfolio.'

    if (status === 502) {
      if (errorMessage?.includes('Price') || errorMessage?.includes('price')) {
        displayMessage = 'Could not load portfolio. Price data unavailable.'
      } else if (errorMessage?.includes('Balance') || errorMessage?.includes('balance')) {
        displayMessage = 'Could not load portfolio. Balance data unavailable.'
      }
    } else if (status === 404) {
      displayMessage = 'Account not found.'
    }

    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <PortfolioAccountSelector
          accounts={accounts}
          selectedAccountId={selectedAccountId}
          onAccountChange={handleAccountChange}
        />
        <p className="text-xs text-[var(--color-error)]">{displayMessage}</p>
      </div>
    )
  }

  // Success state
  if (holdingsData) {
    const selectedAccount = accounts.find((acc) => acc.id === selectedAccountId)
    const currency = selectedAccount?.currency ?? 'USD'

    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-lg font-semibold text-[var(--color-text-primary)]">Portfolio</h1>
        <PortfolioAccountSelector
          accounts={accounts}
          selectedAccountId={selectedAccountId}
          onAccountChange={handleAccountChange}
        />
        <PortfolioHoldingsTable
          holdings={holdingsData.holdings}
          cash={holdingsData.cash}
          currency={currency}
        />
      </div>
    )
  }

  return null
}
