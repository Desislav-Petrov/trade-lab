import { useQuery } from '@tanstack/react-query'
import { fetchTransactions, TRANSACTION_KEYS } from '../api/transactionApi'
import type { TransactionListResponse } from '../types/transaction'

export function useTransactions(
  accountId: string,
  userId: string,
  page: number,
): {
  data: TransactionListResponse | undefined
  isLoading: boolean
  isError: boolean
  error: unknown
} {
  return useQuery({
    queryKey: TRANSACTION_KEYS.list(accountId, userId, page),
    queryFn: () => fetchTransactions(accountId, userId, page),
    enabled: !!accountId && !!userId,
    staleTime: 0,
  })
}
