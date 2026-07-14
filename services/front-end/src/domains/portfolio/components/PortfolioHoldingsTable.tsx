import { useState, useEffect, useRef } from 'react'
import type { StockHolding, CashHolding } from '../types/portfolio.types'

interface PortfolioHoldingsTableProps {
  holdings: StockHolding[]
  cash: CashHolding
  currency: string
  onSell?: (ticker: string, maxQuantity: number) => void
}

interface ContextMenuState {
  x: number
  y: number
  ticker: string
  maxQuantity: number
}

type SortableColumn =
  | 'ticker'
  | 'quantity'
  | 'currentValue'
  | 'currentPrice'
  | 'minPrice'
  | 'maxPrice'
  | 'avgPrice'
  | 'portfolioPercent'
  | 'unrealisedPnL'

type SortDirection = 'asc' | 'desc' | 'default'

interface SortState {
  column: SortableColumn
  direction: SortDirection
}

const COLUMNS: { key: SortableColumn; label: string }[] = [
  { key: 'ticker', label: 'Ticker' },
  { key: 'quantity', label: 'Shares' },
  { key: 'currentValue', label: 'Current Value' },
  { key: 'minPrice', label: 'Min Share Price' },
  { key: 'maxPrice', label: 'Max Share Price' },
  { key: 'avgPrice', label: 'Avg Bought Price' },
  { key: 'portfolioPercent', label: '% of Portfolio' },
  { key: 'unrealisedPnL', label: 'Unrealised P&L' },
]

const DEFAULT_SORT: SortState = { column: 'ticker', direction: 'asc' }

function sortHoldings(holdings: StockHolding[], sort: SortState): StockHolding[] {
  const effectiveSort: SortState =
    sort.direction === 'default' ? DEFAULT_SORT : sort

  return [...holdings].sort((a, b) => {
    const col = effectiveSort.column
    const aVal = a[col] ?? -Infinity
    const bVal = b[col] ?? -Infinity

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      const cmp = aVal.localeCompare(bVal)
      return effectiveSort.direction === 'desc' ? -cmp : cmp
    }

    const aNum = aVal as number
    const bNum = bVal as number
    return effectiveSort.direction === 'desc' ? bNum - aNum : aNum - bNum
  })
}

function formatMoney(value: number): string {
  return value.toFixed(2)
}

function nextDirection(current: SortState, column: SortableColumn): SortDirection {
  if (current.column !== column) return 'asc'
  if (current.direction === 'asc') return 'desc'
  if (current.direction === 'desc') return 'default'
  return 'asc'
}

function sortIndicator(sort: SortState, column: SortableColumn): string {
  if (sort.column !== column || sort.direction === 'default') return ''
  return sort.direction === 'asc' ? ' ▲' : ' ▼'
}

export function PortfolioHoldingsTable({
  holdings,
  cash,
  currency,
  onSell,
}: PortfolioHoldingsTableProps) {
  const [sort, setSort] = useState<SortState>(DEFAULT_SORT)
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null)
  const contextMenuRef = useRef<HTMLDivElement>(null)

  function handleHeaderClick(column: SortableColumn) {
    setSort({ column, direction: nextDirection(sort, column) })
  }

  function handleStockRowContextMenu(
    e: React.MouseEvent,
    ticker: string,
    maxQuantity: number
  ) {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, ticker, maxQuantity })
  }

  function closeContextMenu() {
    setContextMenu(null)
  }

  function handleSellClick() {
    if (contextMenu && onSell) {
      onSell(contextMenu.ticker, contextMenu.maxQuantity)
    }
    closeContextMenu()
  }

  useEffect(() => {
    if (!contextMenu) return

    function handleClickOutside(e: MouseEvent) {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target as Node)) {
        closeContextMenu()
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        closeContextMenu()
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [contextMenu])

  const sortedHoldings = sortHoldings(holdings, sort)

  return (
    <>
    <table>
      <thead>
        <tr>
          {COLUMNS.map(({ key, label }) => (
            <th
              key={key}
              onClick={() => handleHeaderClick(key)}
              style={{ cursor: 'pointer' }}
              aria-sort={
                sort.column === key && sort.direction !== 'default'
                  ? sort.direction === 'asc'
                    ? 'ascending'
                    : 'descending'
                  : 'none'
              }
            >
              {label}
              {sortIndicator(sort, key)}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {sortedHoldings.map((holding) => (
          <tr
            key={holding.ticker}
            onContextMenu={(e) =>
              handleStockRowContextMenu(e, holding.ticker, holding.quantity)
            }
          >
            <td>{holding.ticker}</td>
            <td>{holding.quantity}</td>
            <td>{formatMoney(holding.currentValue)}</td>
            <td>{formatMoney(holding.minPrice)}</td>
            <td>{formatMoney(holding.maxPrice)}</td>
            <td>{formatMoney(holding.avgPrice)}</td>
            <td>
              {holding.portfolioPercent !== null
                ? formatMoney(holding.portfolioPercent)
                : '—'}
            </td>
            <td
              className={
                holding.unrealisedPnL > 0
                  ? 'pnl-positive'
                  : holding.unrealisedPnL < 0
                    ? 'pnl-negative'
                    : ''
              }
            >
              {formatMoney(holding.unrealisedPnL)}
            </td>
          </tr>
        ))}
        <tr data-testid="cash-row">
          <td>{currency}</td>
          <td>—</td>
          <td>{formatMoney(cash.balance)}</td>
          <td>—</td>
          <td>—</td>
          <td>—</td>
          <td>
            {cash.portfolioPercent !== null ? formatMoney(cash.portfolioPercent) : '—'}
          </td>
          <td>—</td>
        </tr>
      </tbody>
    </table>

    {contextMenu && (
      <div
        ref={contextMenuRef}
        role="menu"
        aria-label="Stock row context menu"
        style={{ position: 'fixed', top: contextMenu.y, left: contextMenu.x, zIndex: 1000 }}
        className="rounded border border-[var(--color-border)] bg-[var(--color-surface)] py-1 shadow-md"
      >
        <button
          type="button"
          role="menuitem"
          onClick={handleSellClick}
          className="block w-full px-4 py-2 text-left text-xs text-[var(--color-text-primary)] hover:bg-[var(--color-bg)]"
        >
          Sell
        </button>
      </div>
    )}
    </>
  )
}
