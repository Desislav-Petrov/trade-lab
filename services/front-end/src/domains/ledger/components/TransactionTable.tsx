import { useState } from 'react'
import type { TransactionResponse } from '../types/transaction'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table'
import { Alert, AlertDescription } from '@/shared/components/ui/alert'
import { Skeleton } from '@/shared/components/ui/skeleton'

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
  const [sortColumn, setSortColumn] = useState<SortColumn>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('none')

  function handleHeaderClick(column: SortColumn) {
    if (sortColumn === column) {
      const next = nextSortDirection(sortDirection)
      setSortDirection(next)
      if (next === 'none') setSortColumn(null)
    } else {
      setSortColumn(column)
      setSortDirection('asc')
    }
  }

  if (isLoading) {
    return (
      <div role="status" aria-label="Loading transactions" className="flex flex-col gap-2 py-4">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <Alert variant="destructive" role="alert">
        <AlertDescription>Could not load transactions.</AlertDescription>
      </Alert>
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
    <Table>
      <TableHeader>
        <TableRow>
          {COLUMNS.map(({ key, label }) => (
            <TableHead
              key={label}
              onClick={() => handleHeaderClick(key)}
              className="cursor-pointer select-none hover:text-[hsl(var(--foreground))]"
            >
              {label}
              {sortColumn === key ? sortIndicator(sortDirection) : ''}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((tx) => (
          <TableRow key={tx.id}>
            <TableCell>{tx.type}</TableCell>
            <TableCell>{tx.assetType}</TableCell>
            <TableCell>{formatValue(tx.amount, tx.currency)}</TableCell>
            <TableCell>{tx.ticker ?? ''}</TableCell>
            <TableCell>
              {tx.shares !== null && tx.shares !== undefined ? tx.shares.toString() : ''}
            </TableCell>
            <TableCell>{tx.description ?? ''}</TableCell>
            <TableCell>{formatDate(tx.createdAt)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
