import type { SubscriptionResponse } from '../../marketdata/types/subscription'

interface SubscriptionListProps {
  subscriptions: SubscriptionResponse[]
  selectedTickers: string[]
  onSelectionChange: (tickers: string[]) => void
  isLoading: boolean
}

export function SubscriptionList({
  subscriptions,
  selectedTickers,
  onSelectionChange,
  isLoading,
}: SubscriptionListProps) {
  if (isLoading) {
    return <p className="text-xs text-[var(--color-text-muted)]">Loading...</p>
  }

  if (subscriptions.length === 0) {
    return (
      <p className="text-xs text-[var(--color-text-muted)]">You have no subscriptions yet.</p>
    )
  }

  function handleCheckboxChange(ticker: string, checked: boolean) {
    if (checked) {
      onSelectionChange([...selectedTickers, ticker])
    } else {
      onSelectionChange(selectedTickers.filter((t) => t !== ticker))
    }
  }

  return (
    <ul className="flex flex-col gap-2">
      {subscriptions.map((sub) => {
        const isChecked = selectedTickers.includes(sub.ticker)
        return (
          <li
            key={sub.ticker}
            className="flex items-center gap-3 rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3"
          >
            <input
              type="checkbox"
              id={`sub-${sub.ticker}`}
              checked={isChecked}
              onChange={(e) => handleCheckboxChange(sub.ticker, e.target.checked)}
              className="accent-[var(--color-accent)]"
            />
            <label
              htmlFor={`sub-${sub.ticker}`}
              className="flex flex-1 cursor-pointer items-center gap-2 text-xs"
            >
              <span className="font-medium text-[var(--color-text-primary)]">{sub.ticker}</span>
              <span className="text-[var(--color-text-muted)]">{sub.companyName}</span>
            </label>
          </li>
        )
      })}
    </ul>
  )
}
