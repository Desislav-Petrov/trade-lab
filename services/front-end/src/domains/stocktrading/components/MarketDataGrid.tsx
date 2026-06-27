import { useState } from 'react'
import type { MarketDataUpdate } from '../../marketdata/api/marketDataFeedApi'

interface MarketDataGridProps {
  rows: MarketDataUpdate[]
  feedStatus: 'connecting' | 'connected' | 'error' | 'lost'
}

type SortDirection = 'asc' | 'desc' | 'none'

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

export function MarketDataGrid({ rows, feedStatus }: MarketDataGridProps) {
  const [sortColumn, setSortColumn] = useState<keyof MarketDataUpdate | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('none')

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
    <div style={{ overflowX: 'auto', overflowY: 'auto' }}>
      <table>
        <thead>
          <tr>
            {COLUMNS.map(({ key, label }) => (
              <th key={key} onClick={() => handleHeaderClick(key)} style={{ cursor: 'pointer' }}>
                {label}
                {getSortIndicator(key, sortColumn, sortDirection)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedRows.map((row) => (
            <tr key={row.ticker}>
              <td>{row.ticker}</td>
              <td>{row.companyName}</td>
              <td>{row.currentPrice.toFixed(3)}</td>
              <td>{row.open.toFixed(3)}</td>
              <td>{row.dayLow.toFixed(3)}</td>
              <td>{row.fiftyTwoWeekHigh.toFixed(3)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
