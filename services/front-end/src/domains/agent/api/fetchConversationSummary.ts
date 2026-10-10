import axiosInstance from '../../../shared/api/axiosInstance'
import type { AgentSummaryResponse } from '../types/agent'

// TanStack Query cache key for the authenticated user's durable conversation
// summary (GET /api/v1/agent/summary). Consumed by useConversationSummary.
export const AGENT_SUMMARY_QUERY_KEY = 'agentSummary'

// Retrieves the authenticated user's previous-conversation summary used to
// greet them when the assistant popup opens (Flow G). Returns { summary: null }
// when no prior summary exists (first-ever conversation). The JWT is attached
// by the shared Axios request interceptor.
export async function fetchConversationSummary(): Promise<AgentSummaryResponse> {
  const response = await axiosInstance.get<AgentSummaryResponse>('/v1/agent/summary')
  return response.data
}
