import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  fetchSubscriptions,
  bulkAddSubscriptions,
  bulkRemoveSubscriptions,
  SUBSCRIPTIONS_QUERY_KEY,
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

export function useSupportedTickers(): SubscriptionResponse[] {
  const [tickers, setTickers] = useState<SubscriptionResponse[]>([])

  useEffect(() => {
    fetch('/supported-tickers.csv')
      .then((res) => res.text())
      .then((text) => {
        const parsed = text
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line.length > 0)
          .map((line) => {
            const commaIndex = line.indexOf(',')
            const ticker = line.slice(0, commaIndex).trim()
            const companyName = line.slice(commaIndex + 1).trim()
            return { ticker, companyName }
          })
          .filter((entry) => entry.ticker.length > 0 && entry.companyName.length > 0)
        setTickers(parsed)
      })
      .catch(() => {
        // silently return empty array on error
      })
  }, [])

  return tickers
}
