import { useEffect, useRef, useState } from 'react'
import type { MarketDataUpdate } from '../../marketdata/api/marketDataFeedApi'

interface MarketDataGridProps {
  rows: MarketDataUpdate[]
  feedStatus: 'connecting' | 'connected' | 'error' | 'lost'
}

type SortDirection = 'asc' | 'desc' | 'none'
type PriceMovement = 'up' | 'down' | 'none'

const COLUMNS: { key: keyof MarketDataUpdate; label: string }[] = [
  { key: 'ticker', label: 'Ticker' },
  { key: 'companyName', label: 'Company Name' },
  { key: 'currentPrice', label: 'Current Price (USD)' },
  { key: 'open', label: 'Open (USD)' },
  { key: 'dayLow', label: 'Day Low (USD)' },
  { key: 'fiftyTwoWeekHigh', label: '52W High (USD)' },
]

function nextDirection(
  column: keyof MarketDataUpdate,
  sortColumn: keyof MarketDataUpdate | null,
  sortDirection: SortDirection,
): SortDirection {
  if (sortColumn !== column) return 'asc'
  if (sortDirection === 'asc') return 'desc'
  if (sortDirection === 'desc') return 'none'
  return 'asc'
}

function sortRows(
  rows: MarketDataUpdate[],
  sortColumn: keyof MarketDataUpdate | null,
  sortDirection: SortDirection,
): MarketDataUpdate[] {
  if (!sortColumn || sortDirection === 'none') return rows

  return [...rows].sort((a, b) => {
    const aVal = a[sortColumn]
    const bVal = b[sortColumn]

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return sortDirection === 'asc' ? aVal - bVal : bVal - aVal
    }

    const aStr = String(aVal)
    const bStr = String(bVal)
    const cmp = aStr.localeCompare(bStr)
    return sortDirection === 'asc' ? cmp : -cmp
  })
}

function getSortIndicator(
  column: keyof MarketDataUpdate,
  sortColumn: keyof MarketDataUpdate | null,
  sortDirection: SortDirection,
): string {
  if (sortColumn !== column || sortDirection === 'none') {
    return ' ⇅'
  }

  return sortDirection === 'asc' ? ' ▲' : ' ▼'
}

function getPriceMovement(
  currentPrice: number,
  previousPrice: number | undefined,
): PriceMovement {
  if (previousPrice === undefined || previousPrice === currentPrice) {
    return 'none'
  }

  return currentPrice > previousPrice ? 'up' : 'down'
}

export function MarketDataGrid({ rows, feedStatus }: MarketDataGridProps) {
  const [sortColumn, setSortColumn] = useState<keyof MarketDataUpdate | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('none')
  const previousPricesRef = useRef<Map<string, number>>(new Map())

  const previousPrices = previousPricesRef.current

  useEffect(() => {
    previousPricesRef.current = new Map(rows.map((row) => [row.ticker, row.currentPrice]))
  }, [rows])

  if (feedStatus === 'connecting') {
    return <p>Connecting…</p>
  }

  if (feedStatus === 'error') {
    return <p role="alert">Unable to connect to price feed.</p>
  }

  if (feedStatus === 'lost') {
    return <p role="alert">Connection lost. Please refresh the page.</p>
  }

  // feedStatus === 'connected'
  if (rows.length === 0) {
    return <p>No price data yet.</p>
  }

  function handleHeaderClick(column: keyof MarketDataUpdate) {
    const next = nextDirection(column, sortColumn, sortDirection)
    if (next === 'none') {
      setSortColumn(null)
      setSortDirection('none')
    } else {
      setSortColumn(column)
      setSortDirection(next)
    }
  }

  const sortedRows = sortRows(rows, sortColumn, sortDirection)

  return (
    <div className="overflow-x-auto overflow-y-auto rounded border border-[var(--color-border)] bg-[var(--color-surface)]">
      <table className="min-w-full border-collapse text-left text-xs">
        <thead className="bg-[var(--color-surface-raised)]">
          <tr>
            {COLUMNS.map(({ key, label }) => (
              <th
                key={key}
                scope="col"
                onClick={() => handleHeaderClick(key)}
                className="cursor-pointer select-none border border-[var(--color-border)] px-3 py-2 font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface)]"
              >
                {label}
                {getSortIndicator(key, sortColumn, sortDirection)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedRows.map((row) => {
            const tickerMovement = getPriceMovement(row.currentPrice, previousPrices.get(row.ticker))

            return (
              <tr key={row.ticker} className="bg-[var(--color-bg)]">
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  <span className="inline-flex items-center gap-1 whitespace-nowrap">
                    <span>{row.ticker}</span>
                    {tickerMovement === 'up' && (
                      <span className="text-[var(--color-success)]" aria-label={`${row.ticker} price increased`}>
                        ↑
                      </span>
                    )}
                    {tickerMovement === 'down' && (
                      <span className="text-[var(--color-danger)]" aria-label={`${row.ticker} price decreased`}>
                        ↓
                      </span>
                    )}
                  </span>
                </td>
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  {row.companyName}
                </td>
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  {row.currentPrice.toFixed(3)}
                </td>
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  {row.open.toFixed(3)}
                </td>
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  {row.dayLow.toFixed(3)}
                </td>
                <td className="border border-[var(--color-border)] px-3 py-2 text-[var(--color-text-primary)]">
                  {row.fiftyTwoWeekHigh.toFixed(3)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
