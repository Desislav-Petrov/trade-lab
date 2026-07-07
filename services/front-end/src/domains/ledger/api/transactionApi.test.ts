import { describe, it, expect, vi, beforeEach } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { fetchTransactions, TRANSACTION_KEYS } from './transactionApi'
import type { TransactionListResponse } from '../types/transaction'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: { get: vi.fn() },
}))

const mockGet = vi.mocked(axiosInstance.get)

describe('fetchTransactions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchTransactions - success - calls correct URL with correct params', async () => {
    const mockData: TransactionListResponse = {
      transactions: [],
      page: 0,
      totalPages: 1,
      totalCount: 0,
    }
    mockGet.mockResolvedValueOnce({ data: mockData })

    const result = await fetchTransactions('acc-1', 'user-1', 0)

    expect(mockGet).toHaveBeenCalledWith('/v1/accounts/acc-1/transactions', {
      params: { userId: 'user-1', page: 0, size: 25 },
    })
    expect(result).toEqual(mockData)
  })

  it('fetchTransactions - page 2 - passes correct page param', async () => {
    const mockData: TransactionListResponse = {
      transactions: [],
      page: 2,
      totalPages: 5,
      totalCount: 110,
    }
    mockGet.mockResolvedValueOnce({ data: mockData })

    await fetchTransactions('acc-2', 'user-2', 2)

    expect(mockGet).toHaveBeenCalledWith('/v1/accounts/acc-2/transactions', {
      params: { userId: 'user-2', page: 2, size: 25 },
    })
  })

  it('fetchTransactions - size is always 25 - hardcoded regardless of other params', async () => {
    mockGet.mockResolvedValueOnce({ data: { transactions: [], page: 0, totalPages: 1, totalCount: 0 } })

    await fetchTransactions('acc-1', 'user-1', 0)

    const callArgs = mockGet.mock.calls[0]
    expect(callArgs[1]).toMatchObject({ params: { size: 25 } })
  })

  it('fetchTransactions - success - returns correctly typed TransactionListResponse with transactions', async () => {
    const transaction = {
      id: 'tx-1',
      type: 'CREDIT' as const,
      assetType: 'CASH' as const,
      amount: 1000,
      currency: 'USD',
      ticker: null,
      shares: null,
      description: 'Top up',
      createdAt: '2026-01-01T00:00:00Z',
    }
    const mockData: TransactionListResponse = {
      transactions: [transaction],
      page: 0,
      totalPages: 1,
      totalCount: 1,
    }
    mockGet.mockResolvedValueOnce({ data: mockData })

    const result = await fetchTransactions('acc-1', 'user-1', 0)

    expect(result.transactions).toHaveLength(1)
    expect(result.transactions[0]).toEqual(transaction)
    expect(result.page).toBe(0)
    expect(result.totalPages).toBe(1)
    expect(result.totalCount).toBe(1)
  })

  it('fetchTransactions - stock transaction - returns ticker and shares', async () => {
    const transaction = {
      id: 'tx-2',
      type: 'DEBIT' as const,
      assetType: 'STOCK_BUY' as const,
      amount: 500,
      currency: 'USD',
      ticker: 'AAPL',
      shares: 2.5,
      description: null,
      createdAt: '2026-01-02T00:00:00Z',
    }
    const mockData: TransactionListResponse = {
      transactions: [transaction],
      page: 0,
      totalPages: 1,
      totalCount: 1,
    }
    mockGet.mockResolvedValueOnce({ data: mockData })

    const result = await fetchTransactions('acc-1', 'user-1', 0)

    expect(result.transactions[0].ticker).toBe('AAPL')
    expect(result.transactions[0].shares).toBe(2.5)
  })

  it('fetchTransactions - 401 response - throws AxiosError with status 401', async () => {
    const error = Object.assign(new Error('Unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchTransactions('acc-1', 'user-1', 0)).rejects.toMatchObject({
      response: { status: 401 },
    })
  })

  it('fetchTransactions - 403 response - throws AxiosError with status 403', async () => {
    const error = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchTransactions('acc-1', 'user-1', 0)).rejects.toMatchObject({
      response: { status: 403 },
    })
  })

  it('fetchTransactions - 404 response - throws AxiosError with status 404', async () => {
    const error = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    mockGet.mockRejectedValueOnce(error)

    await expect(fetchTransactions('acc-1', 'user-1', 0)).rejects.toMatchObject({
      response: { status: 404 },
    })
  })
})

describe('TRANSACTION_KEYS', () => {
  it('TRANSACTION_KEYS - all - returns base key array', () => {
    expect(TRANSACTION_KEYS.all).toEqual(['transactions'])
  })

  it('TRANSACTION_KEYS - list - includes accountId, userId, and page', () => {
    expect(TRANSACTION_KEYS.list('acc-1', 'user-1', 0)).toEqual([
      'transactions',
      'acc-1',
      'user-1',
      0,
    ])
  })
})
