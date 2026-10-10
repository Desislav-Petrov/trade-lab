import { useQuery } from '@tanstack/react-query'
import { AGENT_SUMMARY_QUERY_KEY, fetchConversationSummary } from '../api/fetchConversationSummary'
import type { AgentSummaryResponse } from '../types/agent'
import { useAgentStore } from './useAgentStore'

export interface UseConversationSummaryResult {
  summary: string | null
  isLoading: boolean
  refetch: () => void
}

// Fetches the authenticated user's durable conversation summary when the
// assistant popup is open (Flow G). A read failure resolves to no greeting
// (summary treated as null) and is never surfaced as an error — continuity
// simply falls back to no summary until the next turn refreshes it.
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
    isLoading: query.isLoading,
    refetch: () => {
      void query.refetch()
    },
  }
}
