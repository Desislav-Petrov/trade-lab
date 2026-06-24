import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAccounts, createAccount, topUpAccount, ACCOUNTS_QUERY_KEY } from '../api/accountApi'
import type { TopUpAccountRequest } from '../types/account'
import { useSessionStore } from '../../user/hooks/useSessionStore'

export function useAccounts() {
  const userId = useSessionStore((s) => s.user?.userId)

  return useQuery({
    queryKey: [ACCOUNTS_QUERY_KEY, userId],
    queryFn: () => fetchAccounts(userId!),
    enabled: !!userId,
    staleTime: 0,
  })
}

export function useOpenAccount() {
  const queryClient = useQueryClient()
  const userId = useSessionStore((s) => s.user?.userId)

  return useMutation({
    mutationFn: createAccount,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY, userId] })
    },
  })
}

export function useTopUpAccount() {
  const queryClient = useQueryClient()
  const userId = useSessionStore((s) => s.user?.userId)

  return useMutation({
    mutationFn: ({ accountId, request }: { accountId: string; request: TopUpAccountRequest }) =>
      topUpAccount(accountId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [ACCOUNTS_QUERY_KEY, userId] })
    },
  })
}
