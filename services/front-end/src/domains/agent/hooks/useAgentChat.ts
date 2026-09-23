import { useCallback, useState } from 'react'
import { queryAgent } from '../api/queryAgent'
import { useSelectedAccountStore } from '../../../shared/hooks/useSelectedAccountStore'
import { useAgentStore } from './useAgentStore'

export const NO_ACCOUNT_SELECTED_MESSAGE = 'Select a portfolio account to chat with the assistant.'
export const ASSISTANT_UNAVAILABLE_MESSAGE = 'Assistant unavailable. Please try again.'
export const STREAM_INTERRUPTED_MESSAGE =
  'The stream was interrupted. Partial reply kept — retry to continue.'

export interface UseAgentChatResult {
  error: string | null
  hasSelectedAccount: boolean
  isSending: boolean
  sendMessage: (text: string) => Promise<boolean>
  clearError: () => void
}

export function useAgentChat(): UseAgentChatResult {
  const selectedAccountId = useSelectedAccountStore((state) => state.selectedAccountId)
  const [error, setError] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)

  const sendMessage = useCallback(
    async (text: string): Promise<boolean> => {
      const message = text.trim()
      if (message.length === 0) return false

      if (!selectedAccountId) {
        setError(NO_ACCOUNT_SELECTED_MESSAGE)
        return false
      }

      setError(null)
      setIsSending(true)

      const agentStore = useAgentStore.getState()
      const conversationId = agentStore.ensureConversationId()
      agentStore.appendUserTurn(message)
      agentStore.startAssistantTurn()

      let receivedToken = false

      try {
        await queryAgent(
          { accountId: selectedAccountId, conversationId, message },
          (token) => {
            receivedToken = true
            useAgentStore.getState().appendAssistantToken(token)
          },
          () => {},
          () => {
            useAgentStore.getState().finalizeAssistantTurn()
          },
        )

        return true
      } catch {
        if (receivedToken) {
          useAgentStore.getState().finalizeAssistantTurn()
          setError(STREAM_INTERRUPTED_MESSAGE)
          return true
        }

        useAgentStore.getState().discardPendingAssistantTurn()
        setError(ASSISTANT_UNAVAILABLE_MESSAGE)
        return true
      } finally {
        setIsSending(false)
      }
    },
    [selectedAccountId],
  )

  const clearError = useCallback(() => {
    setError(null)
  }, [])

  return {
    error,
    hasSelectedAccount: selectedAccountId !== null,
    isSending,
    sendMessage,
    clearError,
  }
}
