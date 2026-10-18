import { beforeEach, describe, expect, it, vi } from 'vitest'
import axiosInstance from '../../../shared/api/axiosInstance'
import { AGENT_SUMMARY_QUERY_KEY, fetchConversationSummary } from './fetchConversationSummary'

vi.mock('../../../shared/api/axiosInstance', () => ({
  default: {
    get: vi.fn(),
  },
}))

const mockGet = vi.mocked(axiosInstance.get)

describe('fetchConversationSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetchConversationSummary - summary present - returns the summary text from GET /v1/agent/summary', async () => {
    mockGet.mockResolvedValue({
      data: { summary: 'Earlier you reviewed your AAPL position and unrealised P&L.', name: null },
    })

    const result = await fetchConversationSummary()

    expect(mockGet).toHaveBeenCalledWith('/v1/agent/summary')
    expect(result).toEqual({
      summary: 'Earlier you reviewed your AAPL position and unrealised P&L.',
      name: null,
    })
  })

  it('fetchConversationSummary - no prior summary - returns the available user name', async () => {
    mockGet.mockResolvedValue({ data: { summary: null, name: 'Ada Lovelace' } })

    const result = await fetchConversationSummary()

    expect(mockGet).toHaveBeenCalledWith('/v1/agent/summary')
    expect(result).toEqual({ summary: null, name: 'Ada Lovelace' })
  })
})

describe('AGENT_SUMMARY_QUERY_KEY', () => {
  it('AGENT_SUMMARY_QUERY_KEY - is defined as the expected cache-key string', () => {
    expect(AGENT_SUMMARY_QUERY_KEY).toBe('agentSummary')
  })
})
