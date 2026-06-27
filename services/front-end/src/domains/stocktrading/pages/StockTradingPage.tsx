import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import {
  useSubscriptions,
  useBulkAddSubscriptions,
  useBulkRemoveSubscriptions,
  useSupportedTickers,
} from '../../marketdata/hooks/useSubscriptions'
import { useMarketDataFeed } from '../../marketdata/hooks/useMarketDataFeed'
import { SubscriptionList } from '../components/SubscriptionList'
import { AddTickerPanel } from '../components/AddTickerPanel'
import { RemoveTickerBar } from '../components/RemoveTickerBar'
import { MarketDataGrid } from '../components/MarketDataGrid'

function extractErrorMessage(error: unknown): string {
  const axiosError = error as AxiosError<{ error?: string }>
  return axiosError?.response?.data?.error ?? 'Something went wrong. Please try again.'
}

export function StockTradingPage() {
  const user = useSessionStore((s) => s.user)

  const [selectedTickers, setSelectedTickers] = useState<string[]>([])
  const [isAddPanelOpen, setIsAddPanelOpen] = useState(false)
  const [removeError, setRemoveError] = useState<string | null>(null)
  const [addError, setAddError] = useState<string | null>(null)

  const { data: subscriptionsData, isLoading, error: loadError } = useSubscriptions(user?.userId ?? '')
  const { data: supportedTickersData } = useSupportedTickers()
  const bulkAdd = useBulkAddSubscriptions()
  const bulkRemove = useBulkRemoveSubscriptions()

  const subscribedTickers = subscriptionsData?.map(s => s.ticker) ?? []
  const { rows, feedStatus } = useMarketDataFeed(user?.userId ?? '', subscribedTickers)

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const subscriptions = subscriptionsData ?? []
  const supportedTickers = supportedTickersData ?? []

  const subscribedSet = new Set(subscriptions.map((s) => s.ticker))
  const availableTickers = supportedTickers.filter((t) => !subscribedSet.has(t.ticker))

  function handleAdd(tickers: string[]) {
    setAddError(null)
    bulkAdd.mutate(
      { userId: user!.userId, tickers },
      {
        onSuccess: () => {
          setIsAddPanelOpen(false)
        },
        onError: (err) => {
          setAddError(extractErrorMessage(err))
        },
      },
    )
  }

  function handleRemove() {
    setRemoveError(null)
    bulkRemove.mutate(
      { userId: user!.userId, tickers: selectedTickers },
      {
        onSuccess: () => {
          setSelectedTickers([])
        },
        onError: (err) => {
          setRemoveError(extractErrorMessage(err))
        },
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
          <button
            type="button"
            onClick={() => setIsAddPanelOpen(true)}
            className="rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)]"
          >
            Add tickers
          </button>
        )}
      </div>

      {loadError && (
        <p role="alert" className="mb-4 text-xs text-[var(--color-danger)]">
          Failed to load subscriptions. Please try again.
        </p>
      )}

      {removeError && (
        <p role="alert" className="mb-4 text-xs text-[var(--color-danger)]">
          {removeError}
        </p>
      )}

      {isAddPanelOpen && (
        <div className="mb-6">
          <AddTickerPanel
            availableTickers={availableTickers}
            onAdd={handleAdd}
            onClose={() => {
              setIsAddPanelOpen(false)
              setAddError(null)
            }}
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

      <div
        data-testid="subscriptions-grid-divider"
        className="my-6 border-t border-[var(--color-border)] pt-6"
      >
        <MarketDataGrid rows={rows} feedStatus={feedStatus} />
      </div>
    </div>
  )
}
