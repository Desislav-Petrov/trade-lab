import { useState } from 'react'
import type { SubscriptionResponse } from '../../marketdata/types/subscription'
import { Button } from '@/shared/components/ui/button'
import { Input } from '@/shared/components/ui/input'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Card } from '@/shared/components/ui/card'

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
    <Card className="flex flex-col gap-4 p-4">
      {errorMessage && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
      )}

      <Input
        type="text"
        placeholder="Filter tickers…"
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
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
        <Button
          type="button"
          className="flex-1"
          onClick={handleAdd}
          disabled={selectedTickers.length === 0 || isLoading}
        >
          {isLoading ? 'Adding…' : 'Add'}
        </Button>
        <Button type="button" variant="outline" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </Card>
  )
}
