import { useRef, useState } from 'react'
import { Navigate, useLocation, useParams } from 'react-router-dom'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useTransactions } from '../hooks/useTransactions'
import { TransactionTable } from '../components/TransactionTable'
import { PaginationControls } from '../components/PaginationControls'

interface LocationState {
  accountName?: string
  currency?: string
}

export function TransactionListPage() {
  const { accountId } = useParams<{ accountId: string }>()
  const user = useSessionStore((s) => s.user)
  const location = useLocation()
  const state = location.state as LocationState | null

  const accountName = state?.accountName
  const currency = state?.currency

  const [page, setPage] = useState(0)
  const tableRef = useRef<HTMLDivElement>(null)

  const { data, isLoading, isError } = useTransactions(accountId!, user?.userId ?? '', page)

  if (!user) {
    return <Navigate to="/login" replace />
  }

  function handlePageChange(newPage: number) {
    setPage(newPage)
    tableRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const heading =
    accountName && currency ? `${accountName} — ${currency}` : accountId ?? ''

  return (
    <div className="max-w-4xl">
      <div className="mb-6">
        <p className="mb-1 text-xs tracking-widest text-[var(--color-accent)]">LEDGER</p>
        <h1 className="text-sm font-medium text-[var(--color-text-primary)]">{heading}</h1>
      </div>

      <div ref={tableRef}>
        <TransactionTable
          transactions={data?.transactions ?? []}
          isLoading={isLoading}
          isError={isError}
        />
      </div>

      <div className="mt-4">
        <PaginationControls
          currentPage={page}
          totalPages={data?.totalPages ?? 0}
          onPageChange={handlePageChange}
        />
      </div>
    </div>
  )
}
