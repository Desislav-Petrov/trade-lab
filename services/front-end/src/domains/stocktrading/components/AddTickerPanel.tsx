import { useState } from 'react'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'

interface AddTickerPanelProps {
  availableTickers: SubscriptionResponse[]
  onAdd: (tickers: string[]) => void
  onClose: () => void
  isLoading: boolean
  errorMessage: string | null
}

export function AddTickerPanel({
  availableTickers,
  onAdd,
  onClose,
  isLoading,
  errorMessage,
}: AddTickerPanelProps) {
  const [filterText, setFilterText] = useState('')
  const [selectedTickers, setSelectedTickers] = useState<string[]>([])

  const filteredTickers = availableTickers.filter((sub) =>
    sub.ticker.toLowerCase().includes(filterText.toLowerCase()),
  )

  function handleCheckboxChange(ticker: string, checked: boolean) {
    if (checked) {
      setSelectedTickers((prev) => [...prev, ticker])
    } else {
      setSelectedTickers((prev) => prev.filter((t) => t !== ticker))
    }
  }

  function handleAdd() {
    onAdd(selectedTickers)
  }

  return (
    <div className="flex flex-col gap-4 rounded border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      {errorMessage && (
        <p
          role="alert"
          className="border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
        >
          {errorMessage}
        </p>
      )}

      <input
        type="text"
        placeholder="Filter tickers…"
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
        className="w-full rounded border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        aria-label="Filter tickers"
      />

      <ul className="flex flex-col gap-1">
        {filteredTickers.map((sub) => {
          const isChecked = selectedTickers.includes(sub.ticker)
          return (
            <li
              key={sub.ticker}
              className="flex items-center gap-3 rounded px-2 py-1 hover:bg-[var(--color-surface-raised)]"
            >
              <input
                type="checkbox"
                id={`add-${sub.ticker}`}
                checked={isChecked}
                onChange={(e) => handleCheckboxChange(sub.ticker, e.target.checked)}
                className="accent-[var(--color-accent)]"
              />
              <label
                htmlFor={`add-${sub.ticker}`}
                className="flex flex-1 cursor-pointer items-center gap-2 text-xs"
              >
                <span className="font-medium text-[var(--color-text-primary)]">{sub.ticker}</span>
                <span className="text-[var(--color-text-muted)]">{sub.companyName}</span>
              </label>
            </li>
          )
        })}
      </ul>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={handleAdd}
          disabled={selectedTickers.length === 0 || isLoading}
          className="flex-1 rounded bg-[var(--color-accent)] px-4 py-2 text-xs font-medium text-[var(--color-bg)] transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? 'Adding…' : 'Add'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="rounded border border-[var(--color-border)] px-4 py-2 text-xs text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)]"
        >
          Cancel
        </button>
      </div>
    </div>
  )
}
