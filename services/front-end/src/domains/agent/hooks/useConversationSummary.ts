import { useQuery } from '@tanstack/react-query'
import { AGENT_SUMMARY_QUERY_KEY, fetchConversationSummary } from '../api/fetchConversationSummary'
import type { AgentSummaryResponse } from '../types/agent'
import { useAgentStore } from './useAgentStore'

export interface UseConversationSummaryResult {
  summary: string | null
  name: string | null
  isLoading: boolean
  refetch: () => void
}

// Fetches the authenticated user's durable conversation summary and greeting name
// when the assistant popup is open. The backend returns both the summary (Flow G)
// and the name (Flow I) in a single response — the name is always populated when
// resolvable — so the opening greeting is decided from one settled payload with no
// client-side race. A read failure resolves both values to null and is never
// surfaced as an error.
export function useConversationSummary(): UseConversationSummaryResult {
  const isOpen = useAgentStore((state) => state.isOpen)

  const query = useQuery<AgentSummaryResponse, Error>({
    queryKey: [AGENT_SUMMARY_QUERY_KEY],
    queryFn: fetchConversationSummary,
    enabled: isOpen,
    staleTime: 0,
    retry: false,
  })

  return {
    summary: query.data?.summary ?? null,
    name: query.data?.name ?? null,
    isLoading: query.isLoading,
    refetch: () => {
      void query.refetch()
    },
  }
}
