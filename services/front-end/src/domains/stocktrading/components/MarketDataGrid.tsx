import { memo, useEffect, useRef, useState } from 'react'
import type { MarketDataUpdate } from '../../marketdata/api/marketDataFeedApi'

interface MarketDataGridProps {
  rows: MarketDataUpdate[]
  feedStatus: 'connecting' | 'connected' | 'error' | 'lost'
  onBuy?: (ticker: string, companyName: string, priceSnapshot: string) => void
}

interface ContextMenuState {
  x: number
  y: number
  ticker: string
  companyName: string
  priceSnapshot: string
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

// Wrapped in React.memo so the grid only re-renders when its props actually
// change. Combined with useDeferredValue in StockTradingPage this prevents
// rapid WebSocket tick updates from scheduling synchronous re-renders that
// could block user-initiated events such as sidebar navigation clicks.
export const MarketDataGrid = memo(function MarketDataGrid({ rows, feedStatus, onBuy }: MarketDataGridProps) {
  const [sortColumn, setSortColumn] = useState<keyof MarketDataUpdate | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('none')
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null)
  const previousPricesRef = useRef<Map<string, number>>(new Map())
  const contextMenuRef = useRef<HTMLDivElement | null>(null)

  const previousPrices = previousPricesRef.current

  useEffect(() => {
    previousPricesRef.current = new Map(rows.map((row) => [row.ticker, row.currentPrice]))
  }, [rows])

  useEffect(() => {
    if (!contextMenu) return

    function handleClickOutside(event: MouseEvent) {
      if (contextMenuRef.current && !contextMenuRef.current.contains(event.target as Node)) {
        setContextMenu(null)
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setContextMenu(null)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [contextMenu])

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

  function handleRowContextMenu(
    event: React.MouseEvent<HTMLTableRowElement>,
    row: MarketDataUpdate,
  ) {
    if (!onBuy) return
    event.preventDefault()
    setContextMenu({
      x: event.clientX,
      y: event.clientY,
      ticker: row.ticker,
      companyName: row.companyName,
      priceSnapshot: row.currentPrice.toFixed(3),
    })
  }

  function handleBuyClick() {
    if (contextMenu && onBuy) {
      onBuy(contextMenu.ticker, contextMenu.companyName, contextMenu.priceSnapshot)
    }
    setContextMenu(null)
  }

  const sortedRows = sortRows(rows, sortColumn, sortDirection)

  return (
    <div className="relative overflow-x-auto overflow-y-auto rounded border border-[var(--color-border)] bg-[var(--color-surface)]">
      <table className="min-w-full border-collapse text-left text-xs">
        <thead className="bg-[var(--color-surface-raised)]">
          <tr>
            {COLUMNS.map(({ key, label }) => (
              <th
                key={key}
                scope="col"
                onClick={() => handleHeaderClick(key)}
                className="cursor-pointer select-none border border-b border-[var(--color-border)] px-3 py-2 font-medium text-[var(--color-text-primary)] hover:bg-[var(--color-surface)]"
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
              <tr
                key={row.ticker}
                className="bg-[var(--color-bg)]"
                onContextMenu={(e) => handleRowContextMenu(e, row)}
              >
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

      {contextMenu && onBuy && (
        <div
          ref={contextMenuRef}
          role="menu"
          style={{ position: 'fixed', top: contextMenu.y, left: contextMenu.x }}
          className="z-50 rounded border border-[var(--color-border)] bg-[var(--color-surface)] py-1 shadow-lg"
        >
          <button
            role="menuitem"
            type="button"
            onClick={handleBuyClick}
            className="block w-full px-4 py-2 text-left text-xs text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]"
          >
            Buy
          </button>
        </div>
      )}
    </div>
  )
})
