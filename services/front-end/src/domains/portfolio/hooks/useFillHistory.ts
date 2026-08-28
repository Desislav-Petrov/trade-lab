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
  const pages = [firstPage]

  for (let page = 1; page < firstPage.totalPages; page += 1) {
    pages.push(await fetchFillHistory(accountId, page, 100))
  }

  return mergeFillPages(pages)
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
