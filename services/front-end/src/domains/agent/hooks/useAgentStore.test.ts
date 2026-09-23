import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act } from '@testing-library/react'
import { useAgentStore } from './useAgentStore'

describe('useAgentStore', () => {
  const conversationId = '11111111-1111-1111-1111-111111111111'
  const userTurnId = '22222222-2222-2222-2222-222222222222'
  const assistantTurnId = '33333333-3333-3333-3333-333333333333'

  beforeEach(() => {
    act(() => {
      useAgentStore.getState().reset()
    })
  })

  it('useAgentStore - ensureConversationId - generates once and reuses the id', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy.mockReturnValueOnce(conversationId)

    let firstId = ''
    let secondId = ''

    act(() => {
      firstId = useAgentStore.getState().ensureConversationId()
      secondId = useAgentStore.getState().ensureConversationId()
    })

    expect(firstId).toBe(conversationId)
    expect(secondId).toBe(conversationId)
    expect(randomUuidSpy).toHaveBeenCalledTimes(1)
  })

  it('useAgentStore - append and finalize turns - builds transcript with assistant reply', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy
      .mockReturnValueOnce(userTurnId)
      .mockReturnValueOnce(assistantTurnId)

    act(() => {
      useAgentStore.getState().appendUserTurn('What is my exposure?')
      useAgentStore.getState().startAssistantTurn()
      useAgentStore.getState().appendAssistantToken('You are ')
      useAgentStore.getState().appendAssistantToken('fully invested.')
      useAgentStore.getState().finalizeAssistantTurn()
    })

    expect(useAgentStore.getState().transcript).toEqual([
      {
        id: userTurnId,
        role: 'user',
        text: 'What is my exposure?',
        isStreaming: false,
      },
      {
        id: assistantTurnId,
        role: 'assistant',
        text: 'You are fully invested.',
        isStreaming: false,
      },
    ])
  })

  it('useAgentStore - close - preserves transcript while toggling visibility', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy.mockReturnValueOnce(userTurnId)

    act(() => {
      useAgentStore.getState().open()
      useAgentStore.getState().appendUserTurn('Hello')
      useAgentStore.getState().close()
    })

    expect(useAgentStore.getState().isOpen).toBe(false)
    expect(useAgentStore.getState().transcript).toHaveLength(1)
    expect(useAgentStore.getState().transcript[0].text).toBe('Hello')
  })

  it('useAgentStore - discardPendingAssistantTurn with empty text - removes placeholder turn', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy
      .mockReturnValueOnce(userTurnId)
      .mockReturnValueOnce(assistantTurnId)

    act(() => {
      useAgentStore.getState().appendUserTurn('Hello')
      useAgentStore.getState().startAssistantTurn()
      useAgentStore.getState().discardPendingAssistantTurn()
    })

    expect(useAgentStore.getState().transcript).toEqual([
      {
        id: userTurnId,
        role: 'user',
        text: 'Hello',
        isStreaming: false,
      },
    ])
  })

  it('useAgentStore - discardPendingAssistantTurn with partial text - keeps partial reply', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy
      .mockReturnValueOnce(userTurnId)
      .mockReturnValueOnce(assistantTurnId)

    act(() => {
      useAgentStore.getState().appendUserTurn('Hello')
      useAgentStore.getState().startAssistantTurn()
      useAgentStore.getState().appendAssistantToken('Partial')
      useAgentStore.getState().discardPendingAssistantTurn()
    })

    expect(useAgentStore.getState().transcript[1]).toEqual({
      id: assistantTurnId,
      role: 'assistant',
      text: 'Partial',
      isStreaming: false,
    })
  })

  it('useAgentStore - reset - clears assistant state', () => {
    const randomUuidSpy = vi.spyOn(globalThis.crypto, 'randomUUID')
    randomUuidSpy
      .mockReturnValueOnce(conversationId)
      .mockReturnValueOnce(userTurnId)

    act(() => {
      useAgentStore.getState().open()
      useAgentStore.getState().ensureConversationId()
      useAgentStore.getState().appendUserTurn('Hello')
      useAgentStore.getState().reset()
    })

    expect(useAgentStore.getState()).toMatchObject({
      isOpen: false,
      conversationId: null,
      transcript: [],
    })
  })
})
