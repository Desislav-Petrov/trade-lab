import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { queryAgent } from '../api/queryAgent'
import { useAgentStore } from './useAgentStore'
import {
  ASSISTANT_UNAVAILABLE_MESSAGE,
  NO_ACCOUNT_SELECTED_MESSAGE,
  STREAM_INTERRUPTED_MESSAGE,
  useAgentChat,
} from './useAgentChat'
import { useSelectedAccountStore } from '../../../shared/hooks/useSelectedAccountStore'

vi.mock('../api/queryAgent', () => ({
  queryAgent: vi.fn(),
}))

const mockQueryAgent = vi.mocked(queryAgent)

describe('useAgentChat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    act(() => {
      useSelectedAccountStore.getState().clearSelectedAccountId()
      useAgentStore.getState().reset()
    })
  })

  it('useAgentChat - happy stream - appends user turn and streams assistant reply', async () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-1')
    })

    mockQueryAgent.mockImplementationOnce(async (_request, onToken, _onError, onDone) => {
      onToken('Hel')
      onToken('lo')
      onDone()
    })

    const { result } = renderHook(() => useAgentChat())

    await act(async () => {
      const sent = await result.current.sendMessage('Hello')
      expect(sent).toBe(true)
    })

    expect(useAgentStore.getState().conversationId).not.toBeNull()
    expect(useAgentStore.getState().transcript).toHaveLength(2)
    expect(useAgentStore.getState().transcript[0]).toMatchObject({
      role: 'user',
      text: 'Hello',
    })
    expect(useAgentStore.getState().transcript[1]).toMatchObject({
      role: 'assistant',
      text: 'Hello',
      isStreaming: false,
    })
    expect(result.current.error).toBeNull()
  })

  it('useAgentChat - no selected account - blocks request and shows prompt', async () => {
    const { result } = renderHook(() => useAgentChat())

    await act(async () => {
      const sent = await result.current.sendMessage('Hello')
      expect(sent).toBe(false)
    })

    expect(mockQueryAgent).not.toHaveBeenCalled()
    expect(result.current.error).toBe(NO_ACCOUNT_SELECTED_MESSAGE)
    expect(useAgentStore.getState().transcript).toEqual([])
  })

  it('useAgentChat - mid-stream drop - keeps partial reply and exposes retry error', async () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-1')
    })

    mockQueryAgent.mockImplementationOnce(async (_request, onToken) => {
      onToken('Partial ')
      throw new Error('Stream dropped')
    })

    const { result } = renderHook(() => useAgentChat())

    await act(async () => {
      const sent = await result.current.sendMessage('Hello')
      expect(sent).toBe(true)
    })

    expect(useAgentStore.getState().transcript[1]).toMatchObject({
      role: 'assistant',
      text: 'Partial ',
      isStreaming: false,
    })
    expect(result.current.error).toBe(STREAM_INTERRUPTED_MESSAGE)
  })

  it('useAgentChat - pre-stream failure - retains user turn and surfaces unavailable error', async () => {
    act(() => {
      useSelectedAccountStore.getState().setSelectedAccountId('acc-1')
    })

    mockQueryAgent.mockRejectedValueOnce(new Error('Unavailable'))

    const { result } = renderHook(() => useAgentChat())

    await act(async () => {
      const sent = await result.current.sendMessage('Hello')
      expect(sent).toBe(true)
    })

    expect(useAgentStore.getState().transcript).toHaveLength(1)
    expect(useAgentStore.getState().transcript[0]).toMatchObject({
      role: 'user',
      text: 'Hello',
    })
    expect(result.current.error).toBe(ASSISTANT_UNAVAILABLE_MESSAGE)
  })
})
