import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useConversationSummary } from './useConversationSummary'
import { useAgentStore } from './useAgentStore'

vi.mock('../api/fetchConversationSummary', () => ({
  fetchConversationSummary: vi.fn(),
  AGENT_SUMMARY_QUERY_KEY: 'agentSummary',
}))

import { fetchConversationSummary } from '../api/fetchConversationSummary'
const mockFetch = vi.mocked(fetchConversationSummary)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useConversationSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAgentStore.setState({ isOpen: false, transcript: [], conversationId: null })
  })

  it('useConversationSummary - popup closed - query is disabled and no fetch occurs', () => {
    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })

    expect(result.current.summary).toBeNull()
    expect(result.current.name).toBeNull()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('useConversationSummary - popup open with a stored summary - returns the summary text', async () => {
    useAgentStore.setState({ isOpen: true })
    mockFetch.mockResolvedValueOnce({
      summary: 'Earlier you reviewed your AAPL position.',
      name: null,
    })

    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })

    await waitFor(() =>
      expect(result.current.summary).toBe('Earlier you reviewed your AAPL position.'),
    )
    expect(result.current.name).toBeNull()
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('useConversationSummary - popup open with a name - returns the name', async () => {
    useAgentStore.setState({ isOpen: true })
    mockFetch.mockResolvedValueOnce({ summary: null, name: 'Ada Lovelace' })

    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.name).toBe('Ada Lovelace'))
    expect(result.current.summary).toBeNull()
  })

  it('useConversationSummary - popup open with no prior summary - returns null', async () => {
    useAgentStore.setState({ isOpen: true })
    mockFetch.mockResolvedValueOnce({ summary: null, name: null })

    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })

    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1))
    expect(result.current.summary).toBeNull()
    expect(result.current.name).toBeNull()
  })

  it('useConversationSummary - read fails - resolves to null with no error surfaced', async () => {
    useAgentStore.setState({ isOpen: true })
    mockFetch.mockRejectedValueOnce(new Error('network down'))

    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })

    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1))
    expect(result.current.summary).toBeNull()
    expect(result.current.name).toBeNull()
  })

  it('useConversationSummary - refetch - re-pulls the carried-forward summary', async () => {
    useAgentStore.setState({ isOpen: true })
    mockFetch.mockResolvedValue({ summary: 'First summary', name: null })

    const { result } = renderHook(() => useConversationSummary(), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.summary).toBe('First summary'))

    mockFetch.mockResolvedValue({ summary: 'Second summary', name: null })
    result.current.refetch()

    await waitFor(() => expect(result.current.summary).toBe('Second summary'))
  })
})
