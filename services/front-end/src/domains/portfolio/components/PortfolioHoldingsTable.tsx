import { useState } from 'react'
import type { StockHolding, CashHolding } from '../types/portfolio.types'

export interface PortfolioHoldingsTableProps {
  holdings: StockHolding[]
  cash: CashHolding
  currency: string
}

type SortDirection = 'asc' | 'desc' | 'default'
type SortColumn = keyof StockHolding | null

function formatCurrency(value: number): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatPercent(value: number | null): string {
  if (value === null) return '—'
  return `${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
}

function formatShares(value: number): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 4 })
}

function sortIndicator(direction: SortDirection): string {
  if (direction === 'asc') return ' ↑'
  if (direction === 'desc') return ' ↓'
  return ''
}

interface Column {
  key: SortColumn
  label: string
}

const COLUMNS: Column[] = [
  { key: 'ticker', label: 'Ticker' },
  { key: 'quantity', label: 'Shares' },
  { key: 'currentValue', label: 'Current Value' },
  { key: 'minPrice', label: 'Min Share Price' },
  { key: 'maxPrice', label: 'Max Share Price' },
  { key: 'avgPrice', label: 'Avg Bought Price' },
  { key: 'portfolioPercent', label: '% of Portfolio' },
  { key: 'unrealisedPnL', label: 'Unrealised P&L' },
]

function compareRows(
  a: StockHolding,
  b: StockHolding,
  column: SortColumn,
  direction: SortDirection,
): number {
  if (column === null || direction === 'default') return 0

  const aVal = a[column]
  const bVal = b[column]

  // Handle null values (portfolioPercent can be null)
  if (aVal === null && bVal === null) return 0
  if (aVal === null) return 1
  if (bVal === null) return -1

  const aStr = String(aVal)
  const bStr = String(bVal)

  const cmp = aStr.localeCompare(bStr, undefined, { numeric: true })
  return direction === 'asc' ? cmp : -cmp
}

export function PortfolioHoldingsTable({ holdings, cash, currency }: PortfolioHoldingsTableProps) {
  // Default sort: ticker ascending
  const [sortColumn, setSortColumn] = useState<SortColumn>('ticker')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')

  function handleHeaderClick(column: SortColumn) {
    if (sortColumn === column) {
      // Cycle through: asc → desc → default (ticker asc)
      if (sortDirection === 'asc') {
        setSortDirection('desc')
      } else if (sortDirection === 'desc') {
        // Reset to default
        setSortColumn('ticker')
        setSortDirection('asc')
      }
    } else {
      setSortColumn(column)
      setSortDirection('asc')
    }
  }

  // Sort stock holdings
  const sortedHoldings =
    sortColumn !== null && sortDirection !== 'default'
      ? [...holdings].sort((a, b) => compareRows(a, b, sortColumn, sortDirection))
      : holdings

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-xs">
        <thead>
          <tr>
            {COLUMNS.map(({ key, label }) => (
              <th
                key={label}
                onClick={() => handleHeaderClick(key)}
                className="cursor-pointer border-b border-[var(--color-border)] py-2 px-3 text-left text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] select-none"
              >
                {label}
                {sortColumn === key ? sortIndicator(sortDirection) : ''}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedHoldings.map((holding) => (
            <tr
              key={holding.ticker}
              className="border-b border-[var(--color-border)] hover:bg-[var(--color-surface)]"
            >
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{holding.ticker}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatShares(holding.quantity)}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatCurrency(holding.currentValue)}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatCurrency(holding.minPrice)}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatCurrency(holding.maxPrice)}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatCurrency(holding.avgPrice)}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatPercent(holding.portfolioPercent)}</td>
              <td
                className={`py-2 px-3 ${
                  holding.unrealisedPnL > 0
                    ? 'text-[var(--color-success)]'
                    : holding.unrealisedPnL < 0
                      ? 'text-[var(--color-danger)]'
                      : 'text-[var(--color-text-primary)]'
                }`}
              >
                {formatCurrency(holding.unrealisedPnL)}
              </td>
            </tr>
          ))}
          {/* Cash row - always pinned at the bottom */}
          <tr className="border-t-2 border-[var(--color-border)] bg-[var(--color-surface)]">
            <td className="py-2 px-3 font-semibold text-[var(--color-text-primary)]">{currency}</td>
            <td className="py-2 px-3 text-[var(--color-text-muted)]">—</td>
            <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatCurrency(cash.balance)}</td>
            <td className="py-2 px-3 text-[var(--color-text-muted)]">—</td>
            <td className="py-2 px-3 text-[var(--color-text-muted)]">—</td>
            <td className="py-2 px-3 text-[var(--color-text-muted)]">—</td>
            <td className="py-2 px-3 text-[var(--color-text-primary)]">{formatPercent(cash.portfolioPercent)}</td>
            <td className="py-2 px-3 text-[var(--color-text-muted)]">—</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}
