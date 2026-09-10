import { useQuery } from '@tanstack/react-query'
import { fetchFillHistory, FILL_HISTORY_QUERY_KEY } from '../api/portfolioApi'
import type { FillHistoryEntry, FillHistoryResponse } from '../types/portfolio.types'

function mergeFillPages(pages: FillHistoryResponse[]): FillHistoryEntry[] {
  const byTicker = new Map<string, FillHistoryEntry>()

  for (const page of pages) {
    for (const fill of page.fills) {
      const existing = byTicker.get(fill.ticker)
      if (existing) {
        existing.dataPoints.push(...fill.dataPoints)
      } else {
        byTicker.set(fill.ticker, {
          ticker: fill.ticker,
          dataPoints: [...fill.dataPoints],
        })
      }
    }
  }

  return Array.from(byTicker.values()).map((fill) => ({
    ticker: fill.ticker,
    dataPoints: [...fill.dataPoints].sort((a, b) => a.filledAt.localeCompare(b.filledAt)),
  }))
}

async function fetchAllFillHistoryPages(accountId: string): Promise<FillHistoryEntry[]> {
  const firstPage = await fetchFillHistory(accountId, 0, 100)

  // The first page reveals totalPages; the remaining pages are independent, so
  // fetch them in parallel rather than serially awaiting each one.
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, index) =>
      fetchFillHistory(accountId, index + 1, 100),
    ),
  )

  return mergeFillPages([firstPage, ...remainingPages])
}

export function useFillHistory(accountId: string | null) {
  const query = useQuery<FillHistoryEntry[], Error>({
    queryKey: [FILL_HISTORY_QUERY_KEY, accountId],
    queryFn: () => fetchAllFillHistoryPages(accountId!),
    enabled: accountId !== null,
    staleTime: 0,
  })

  return {
    fills: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
  }
}
