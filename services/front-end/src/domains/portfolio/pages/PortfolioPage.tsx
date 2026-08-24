import { useEffect } from 'react'
import { Navigate } from '@tanstack/react-router'
import type { AxiosError } from 'axios'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { usePortfolioStore } from '../hooks/usePortfolioStore'
import { usePortfolioHoldings } from '../hooks/usePortfolioHoldings'
import { PortfolioAccountSelector } from '../components/PortfolioAccountSelector'
import { PortfolioHoldingsTable } from '../components/PortfolioHoldingsTable'
import { InsightsTab } from '../components/InsightsTab'
import { useSellPanel } from '../../stocktrading/hooks/useSellPanel'
import { SellPanel } from '../../stocktrading/components/SellPanel'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/components/ui/tabs'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Skeleton } from '@/shared/components/ui/skeleton'

function getHoldings502ErrorMessage(error: unknown): string {
  const axiosError = error as AxiosError<{ error?: string }>
  const errorText = axiosError?.response?.data?.error ?? ''
  if (/price/i.test(errorText)) return 'Could not load portfolio. Price data unavailable.'
  if (/balance/i.test(errorText)) return 'Could not load portfolio. Balance data unavailable.'
  return 'Could not load portfolio. Price data unavailable.'
}

export function PortfolioPage() {
  const user = useSessionStore((s) => s.user)
  const selectedAccountId = usePortfolioStore((s) => s.selectedAccountId)
  const setSelectedAccountId = usePortfolioStore((s) => s.setSelectedAccountId)

  const { isOpen, ticker, maxQuantity, openSellPanel } = useSellPanel()

  const { data: accountsData, isLoading: isAccountsLoading, isError: isAccountsError } = useActiveAccounts()
  const accounts = accountsData?.accounts ?? []

  useEffect(() => {
    if (selectedAccountId === null && accounts.length > 0) {
      setSelectedAccountId(accounts[0].id)
    }
  }, [accounts, selectedAccountId, setSelectedAccountId])

  const userId = user?.userId ?? ''

  const {
    data: holdingsData,
    insights,
    isLoading: isHoldingsLoading,
    isError: isHoldingsError,
    error: holdingsError,
  } = usePortfolioHoldings(selectedAccountId, userId)

  if (!user) return <Navigate to="/login" replace />

  const holdingsAxiosError = holdingsError as AxiosError | null
  if (holdingsAxiosError?.response?.status === 401) return <Navigate to="/login" replace />

  function handleAccountChange(accountId: string) {
    setSelectedAccountId(accountId)
  }

  function renderAccountSelector() {
    if (isAccountsLoading) return <Skeleton className="h-8 w-64" />
    if (isAccountsError) {
      return (
        <Alert variant="destructive">
          <AlertDescription>Could not load accounts.</AlertDescription>
        </Alert>
      )
    }
    return (
      <PortfolioAccountSelector
        accounts={accounts}
        selectedAccountId={selectedAccountId}
        onAccountChange={handleAccountChange}
      />
    )
  }

  const currency = holdingsData?.cash.currency ?? 'USD'

  function renderHoldingsContent() {
    if (isAccountsError || (accounts.length === 0 && !isAccountsLoading) || selectedAccountId === null) return null
    if (isHoldingsLoading) {
      return (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-8 w-full" />)}
        </div>
      )
    }
    if (isHoldingsError) {
      const status = (holdingsError as AxiosError)?.response?.status
      const message = status === 502 ? getHoldings502ErrorMessage(holdingsError) : 'Could not load portfolio.'
      return (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      )
    }
    if (holdingsData) {
      return (
        <PortfolioHoldingsTable
          holdings={holdingsData.holdings}
          cash={holdingsData.cash}
          currency={holdingsData.cash.currency}
          onSell={openSellPanel}
        />
      )
    }
    return null
  }

  return (
    <div className="max-w-lg">
      <div className="mb-6">
        <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">PORTFOLIO</p>
        <h1 className="text-sm font-medium text-[var(--color-text-primary)]">Portfolio</h1>
      </div>

      <div className="mb-4">{renderAccountSelector()}</div>

      <Tabs defaultValue="insights">
        <TabsList className="mb-6 w-full justify-start">
          <TabsTrigger value="insights">Insights</TabsTrigger>
          <TabsTrigger value="holdings">Holdings</TabsTrigger>
          <TabsTrigger value="advanced">Advanced Insights</TabsTrigger>
        </TabsList>

        <TabsContent value="insights">
          {!isAccountsError && !(accounts.length === 0 && !isAccountsLoading) && selectedAccountId !== null ? (
            <InsightsTab
              insights={insights}
              isLoading={isHoldingsLoading}
              isError={isHoldingsError}
              currency={currency}
            />
          ) : null}
        </TabsContent>

        <TabsContent value="holdings">
          {renderHoldingsContent()}
        </TabsContent>

        <TabsContent value="advanced">
          <div data-testid="advanced-insights-placeholder" />
        </TabsContent>
      </Tabs>

      {isOpen && ticker !== null && maxQuantity !== null && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
          <SellPanel
            ticker={ticker}
            companyName={holdingsData?.holdings.find((h) => h.ticker === ticker)?.ticker ?? ticker}
            maxQuantity={maxQuantity}
          />
        </div>
      )}
    </div>
  )
}
