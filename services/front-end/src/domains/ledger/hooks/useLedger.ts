import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchAccounts, createAccount, ACCOUNTS_QUERY_KEY } from '../api/accountApi'
import { useSessionStore } from '../../user/hooks/useSessionStore'

export function useAccounts() {
  const userId = useSessionStore((s) => s.user?.userId)

  return useQuery({
    queryKey: [ACCOUNTS_QUERY_KEY, userId],
    queryFn: () => fetchAccounts(userId!),
    enabled: !!userId,
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
