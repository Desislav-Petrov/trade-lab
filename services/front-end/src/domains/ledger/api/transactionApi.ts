import axiosInstance from '../../../shared/api/axiosInstance'
import type { TransactionListResponse } from '../types/transaction'

export const TRANSACTION_KEYS = {
  all: ['transactions'] as const,
  list: (accountId: string, userId: string, page: number) =>
    ['transactions', accountId, userId, page] as const,
}

export async function fetchTransactions(
  accountId: string,
  userId: string,
  page: number,
): Promise<TransactionListResponse> {
  const response = await axiosInstance.get<TransactionListResponse>(
    `/v1/accounts/${accountId}/transactions`,
    {
      params: {
        userId,
        page,
        size: 25,
      },
    },
  )
  return response.data
}
