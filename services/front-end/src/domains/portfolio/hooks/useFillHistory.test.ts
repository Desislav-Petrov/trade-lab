import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useFillHistory } from './useFillHistory'
import type { FillHistoryResponse } from '../types/portfolio.types'

vi.mock('../api/portfolioApi', () => ({
  fetchFillHistory: vi.fn(),
  FILL_HISTORY_QUERY_KEY: 'fillHistory',
}))

import { fetchFillHistory } from '../api/portfolioApi'
const mockFetchFillHistory = vi.mocked(fetchFillHistory)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

function makePage(page: number, totalPages: number, points: number): FillHistoryResponse {
  return {
    page,
    size: 100,
    totalPages,
    totalElements: totalPages * 100,
    fills: [
      {
        ticker: 'AAPL',
        dataPoints: Array.from({ length: points }, (_, index) => ({
          filledAt: `2026-08-28T${String(page).padStart(2, '0')}:${String(index).padStart(2, '0')}:00Z`,
          executionPrice: 150 + page + index,
          quantity: 1,
          side: index % 2 === 0 ? 'BUY' : 'SELL',
        })),
      },
    ],
  }
}

describe('useFillHistory', () => {
  beforeEach(() => vi.clearAllMocks())

  it('useFillHistory - accountId is null - query is disabled', () => {
    const { result } = renderHook(() => useFillHistory(null), { wrapper: createWrapper() })

    expect(result.current.fills).toEqual([])
    expect(result.current.isLoading).toBe(false)
    expect(mockFetchFillHistory).not.toHaveBeenCalled()
  })

  it('useFillHistory - single page - returns fill history', async () => {
    const page = makePage(0, 1, 2)
    mockFetchFillHistory.mockResolvedValueOnce(page)

    const { result } = renderHook(() => useFillHistory('acc-1'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.fills).toHaveLength(1))
    expect(result.current.fills[0].dataPoints).toHaveLength(2)
    expect(mockFetchFillHistory).toHaveBeenCalledWith('acc-1', 0, 100)
  })

  it('useFillHistory - multiple pages - merges data points per ticker', async () => {
    mockFetchFillHistory
      .mockResolvedValueOnce(makePage(0, 2, 100))
      .mockResolvedValueOnce(makePage(1, 2, 100))

    const { result } = renderHook(() => useFillHistory('acc-1'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.fills[0]?.dataPoints).toHaveLength(200))
    expect(result.current.fills).toHaveLength(1)
    expect(mockFetchFillHistory).toHaveBeenNthCalledWith(1, 'acc-1', 0, 100)
    expect(mockFetchFillHistory).toHaveBeenNthCalledWith(2, 'acc-1', 1, 100)
  })

  it('useFillHistory - second page fails - returns isError true', async () => {
    mockFetchFillHistory
      .mockResolvedValueOnce(makePage(0, 2, 100))
      .mockRejectedValueOnce(
        Object.assign(new Error('Server error'), { response: { status: 500 } }),
      )

    const { result } = renderHook(() => useFillHistory('acc-1'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.fills).toEqual([])
  })
})
