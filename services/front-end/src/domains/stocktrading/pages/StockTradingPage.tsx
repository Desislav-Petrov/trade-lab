import { useCallback, useEffect, useMemo, useState, useDeferredValue } from 'react'
import { Navigate } from '@tanstack/react-router'
import { getApiErrorMessage } from '@/shared/lib/apiError'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import {
  useSubscriptions,
  useBulkAddSubscriptions,
  useBulkRemoveSubscriptions,
  useSupportedTickers,
} from '../../marketdata/hooks/useSubscriptions'
import { useMarketDataFeed } from '../../marketdata/hooks/useMarketDataFeed'
import { useActiveAccounts } from '../../ledger/hooks/useLedger'
import { useStockTradingStore } from '../hooks/useStockTradingStore'
import { AccountSelector } from '../components/AccountSelector'
import { SubscriptionList } from '../components/SubscriptionList'
import { AddTickerPanel } from '../components/AddTickerPanel'
import { RemoveTickerBar } from '../components/RemoveTickerBar'
import { MarketDataGrid } from '../components/MarketDataGrid'
import { BuyPanel } from '../components/BuyPanel'
import { Button } from '@/shared/components/ui/button'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'

interface BuyContext {
  ticker: string
  companyName: string
  priceSnapshot: string
}

export function StockTradingPage() {
  const user = useSessionStore((s) => s.user)

  const [selectedTickers, setSelectedTickers] = useState<string[]>([])
  const [isAddPanelOpen, setIsAddPanelOpen] = useState(false)
  const [removeError, setRemoveError] = useState<string | null>(null)
  const [addError, setAddError] = useState<string | null>(null)
  const [buyContext, setBuyContext] = useState<BuyContext | null>(null)

  const { data: subscriptionsData, isLoading, error: loadError } = useSubscriptions(user?.userId ?? '')
  const { data: supportedTickersData } = useSupportedTickers()
  const bulkAdd = useBulkAddSubscriptions()
  const bulkRemove = useBulkRemoveSubscriptions()

  const subscribedTickers = useMemo(
    () => subscriptionsData?.map((s) => s.ticker) ?? [],
    [subscriptionsData],
  )

  const { rows, feedStatus } = useMarketDataFeed(user?.userId ?? '', subscribedTickers)
  const deferredRows = useDeferredValue(rows)

  const { data: activeAccountsData, isLoading: isAccountsLoading, isError: isAccountsError } = useActiveAccounts()
  const selectedAccountId = useStockTradingStore((s) => s.selectedAccountId)
  const setSelectedAccountId = useStockTradingStore((s) => s.setSelectedAccountId)

  useEffect(() => {
    if (selectedAccountId === null && (activeAccountsData?.accounts?.length ?? 0) > 0) {
      setSelectedAccountId(activeAccountsData!.accounts[0].id)
    }
  }, [activeAccountsData])

  // Stable handler so MarketDataGrid's React.memo is not defeated by a new
  // function identity on every live-feed tick re-render.
  const handleBuy = useCallback(
    (ticker: string, companyName: string, priceSnapshot: string) =>
      setBuyContext({ ticker, companyName, priceSnapshot }),
    [],
  )

  const availableTickers = useMemo(() => {
    const subscribedSet = new Set((subscriptionsData ?? []).map((s) => s.ticker))
    return (supportedTickersData ?? []).filter((t) => !subscribedSet.has(t.ticker))
  }, [subscriptionsData, supportedTickersData])

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const subscriptions = subscriptionsData ?? []

  function handleAdd(tickers: string[]) {
    setAddError(null)
    bulkAdd.mutate(
      { userId: user!.userId, tickers },
      {
        onSuccess: () => setIsAddPanelOpen(false),
        onError: (err) => setAddError(getApiErrorMessage(err)),
      },
    )
  }

  function handleRemove() {
    setRemoveError(null)
    bulkRemove.mutate(
      { userId: user!.userId, tickers: selectedTickers },
      {
        onSuccess: () => setSelectedTickers([]),
        onError: (err) => setRemoveError(getApiErrorMessage(err)),
      },
    )
  }

  return (
    <div className="max-w-lg">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">MARKET DATA</p>
          <h1 className="text-sm font-medium text-[var(--color-text-primary)]">Stock Trading</h1>
        </div>
        {!isAddPanelOpen && (
          <Button onClick={() => setIsAddPanelOpen(true)}>Add tickers</Button>
        )}
      </div>

      <div className="mb-4">
        <AccountSelector
          accounts={activeAccountsData?.accounts ?? []}
          selectedAccountId={selectedAccountId}
          onSelect={setSelectedAccountId}
          isLoading={isAccountsLoading}
          isError={isAccountsError}
        />
      </div>

      {loadError && (
        <Alert variant="destructive" role="alert" className="mb-4">
          <AlertDescription>Failed to load subscriptions. Please try again.</AlertDescription>
        </Alert>
      )}

      {removeError && (
        <Alert variant="destructive" role="alert" className="mb-4">
          <AlertDescription>{removeError}</AlertDescription>
        </Alert>
      )}

      {isAddPanelOpen && (
        <div className="mb-6">
          <AddTickerPanel
            availableTickers={availableTickers}
            onAdd={handleAdd}
            onClose={() => { setIsAddPanelOpen(false); setAddError(null) }}
            isLoading={bulkAdd.isPending}
            errorMessage={addError}
          />
        </div>
      )}

      <div className="mb-4">
        <RemoveTickerBar
          selectedCount={selectedTickers.length}
          onRemove={handleRemove}
          isLoading={bulkRemove.isPending}
        />
      </div>

      <SubscriptionList
        subscriptions={subscriptions}
        selectedTickers={selectedTickers}
        onSelectionChange={setSelectedTickers}
        isLoading={isLoading}
      />

      <MarketDataGrid
        rows={deferredRows}
        feedStatus={feedStatus}
        onBuy={selectedAccountId ? handleBuy : undefined}
      />

      {buyContext && selectedAccountId && (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center bg-black/40"
          role="presentation"
        >
          <BuyPanel
            ticker={buyContext.ticker}
            companyName={buyContext.companyName}
            priceSnapshot={buyContext.priceSnapshot}
            accountId={selectedAccountId}
            userId={user.userId}
            onClose={() => setBuyContext(null)}
          />
        </div>
      )}
    </div>
  )
}
