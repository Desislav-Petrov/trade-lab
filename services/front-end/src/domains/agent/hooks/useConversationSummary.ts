import { useQuery } from '@tanstack/react-query'
import { AGENT_SUMMARY_QUERY_KEY, fetchConversationSummary } from '../api/fetchConversationSummary'
import type { AgentSummaryResponse } from '../types/agent'
import { useSessionStore } from '../../user/hooks/useSessionStore'
import { useAgentStore } from './useAgentStore'

export interface UseConversationSummaryResult {
  summary: string | null
  name: string | null
  isLoading: boolean
  refetch: () => void
}

// Fetches the authenticated user's durable conversation summary when the
// assistant popup is open (Flow G). A read failure resolves both values to null
// and is never surfaced as an error. The name used for the opening greeting
// (Flow I) falls back to the signed-in user's first name from the session when
// the agent endpoint returns no name: the backend only populates `name` when no
// summary exists, so after "New conversation" (which still greets by name) the
// session is the reliable source. See decisions/2026-10-18-new-conversation-name-greeting.md.
export function useConversationSummary(): UseConversationSummaryResult {
  const isOpen = useAgentStore((state) => state.isOpen)
  const sessionFirstName = useSessionStore((state) => state.user?.firstName ?? null)

  const query = useQuery<AgentSummaryResponse, Error>({
    queryKey: [AGENT_SUMMARY_QUERY_KEY],
    queryFn: fetchConversationSummary,
    enabled: isOpen,
    staleTime: 0,
    retry: false,
  })

  return {
    summary: query.data?.summary ?? null,
    name: query.data?.name ?? sessionFirstName,
    isLoading: query.isLoading,
    refetch: () => {
      void query.refetch()
    },
  }
}
