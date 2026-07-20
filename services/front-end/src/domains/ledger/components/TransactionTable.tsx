import { useState } from 'react'
import type { TransactionResponse } from '../types/transaction'

export interface TransactionTableProps {
  transactions: TransactionResponse[]
  isLoading: boolean
  isError: boolean
}

type SortDirection = 'asc' | 'desc' | 'none'
type SortColumn = keyof TransactionResponse | null

function formatValue(amount: number, currency: string): string {
  return `${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`
}

function formatDate(utcIso: string): string {
  return new Date(utcIso).toLocaleString()
}

function nextSortDirection(current: SortDirection): SortDirection {
  if (current === 'none') return 'asc'
  if (current === 'asc') return 'desc'
  return 'none'
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
  { key: 'type', label: 'Direction' },
  { key: 'assetType', label: 'Asset Type' },
  { key: 'amount', label: 'Value' },
  { key: 'ticker', label: 'Ticker' },
  { key: 'shares', label: 'Shares' },
  { key: 'description', label: 'Description' },
  { key: 'createdAt', label: 'Date' },
]

function compareRows(
  a: TransactionResponse,
  b: TransactionResponse,
  column: SortColumn,
  direction: SortDirection,
): number {
  if (column === null || direction === 'none') return 0

  const aVal = a[column]
  const bVal = b[column]

  const aStr = aVal === null || aVal === undefined ? '' : String(aVal)
  const bStr = bVal === null || bVal === undefined ? '' : String(bVal)

  const cmp = aStr.localeCompare(bStr, undefined, { numeric: true })
  return direction === 'asc' ? cmp : -cmp
}

export function TransactionTable({ transactions, isLoading, isError }: TransactionTableProps) {
  // Default state: createdAt descending — matches server order, so treated as "none" (unsorted).
  // The comment here: server sends createdAt DESC by default; we don't need to re-sort for that.
  const [sortColumn, setSortColumn] = useState<SortColumn>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('none')

  function handleHeaderClick(column: SortColumn) {
    if (sortColumn === column) {
      const next = nextSortDirection(sortDirection)
      setSortDirection(next)
      if (next === 'none') {
        setSortColumn(null)
      }
    } else {
      setSortColumn(column)
      setSortDirection('asc')
    }
  }

  if (isLoading) {
    return (
      <div
        role="status"
        aria-label="Loading transactions"
        className="flex items-center justify-center py-8"
      >
        <span className="text-xs text-[var(--color-text-muted)]">Loading…</span>
      </div>
    )
  }

  if (isError) {
    return (
      <p
        role="alert"
        className="border-l-2 border-[var(--color-danger)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-danger)]"
      >
        Could not load transactions.
      </p>
    )
  }

  if (transactions.length === 0) {
    return <p className="text-xs text-[var(--color-text-muted)]">No transactions yet.</p>
  }

  const sorted =
    sortColumn !== null && sortDirection !== 'none'
      ? [...transactions].sort((a, b) => compareRows(a, b, sortColumn, sortDirection))
      : transactions

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
          {sorted.map((tx) => (
            <tr
              key={tx.id}
              className="border-b border-[var(--color-border)] hover:bg-[var(--color-surface)]"
            >
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{tx.type}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{tx.assetType}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">
                {formatValue(tx.amount, tx.currency)}
              </td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{tx.ticker ?? ''}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">
                {tx.shares !== null && tx.shares !== undefined ? tx.shares.toString() : ''}
              </td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">{tx.description ?? ''}</td>
              <td className="py-2 px-3 text-[var(--color-text-primary)]">
                {formatDate(tx.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
