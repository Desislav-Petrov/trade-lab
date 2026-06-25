import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  fetchSupportedTickers,
  fetchSubscriptions,
  bulkAddSubscriptions,
  bulkRemoveSubscriptions,
  SUBSCRIPTIONS_QUERY_KEY,
  SUPPORTED_TICKERS_QUERY_KEY,
} from '../api/subscriptionApi'
import type { SubscriptionResponse } from '../types/subscription'

export function useSubscriptions(userId: string) {
  return useQuery({
    queryKey: [SUBSCRIPTIONS_QUERY_KEY, userId],
    queryFn: () => fetchSubscriptions(userId),
    enabled: !!userId,
  })
}

export function useBulkAddSubscriptions() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: bulkAddSubscriptions,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [SUBSCRIPTIONS_QUERY_KEY] })
    },
  })
}

export function useBulkRemoveSubscriptions() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: bulkRemoveSubscriptions,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [SUBSCRIPTIONS_QUERY_KEY] })
    },
  })
}

export function useSupportedTickers() {
  return useQuery<SubscriptionResponse[]>({
    queryKey: [SUPPORTED_TICKERS_QUERY_KEY],
    queryFn: fetchSupportedTickers,
    staleTime: Infinity,
  })
}
