import { create } from 'zustand'
import type { Turn } from '../types/agent'

interface AgentStoreState {
  isOpen: boolean
  transcript: Turn[]
  conversationId: string | null
  open: () => void
  close: () => void
  toggle: () => void
  ensureConversationId: () => string
  appendUserTurn: (text: string) => void
  startAssistantTurn: () => void
  appendAssistantToken: (token: string) => void
  finalizeAssistantTurn: () => void
  discardPendingAssistantTurn: () => void
  reset: () => void
}

function createTurn(role: Turn['role'], text: string, isStreaming: boolean): Turn {
  return {
    id: crypto.randomUUID(),
    role,
    text,
    isStreaming,
  }
}

export const useAgentStore = create<AgentStoreState>((set, get) => ({
  isOpen: false,
  transcript: [],
  conversationId: null,
  open: () => set({ isOpen: true }),
  close: () => set({ isOpen: false }),
  toggle: () => set((state) => ({ isOpen: !state.isOpen })),
  ensureConversationId: () => {
    const currentConversationId = get().conversationId
    if (currentConversationId) return currentConversationId

    const nextConversationId = crypto.randomUUID()
    set({ conversationId: nextConversationId })
    return nextConversationId
  },
  appendUserTurn: (text) =>
    set((state) => ({
      transcript: [...state.transcript, createTurn('user', text, false)],
    })),
  startAssistantTurn: () =>
    set((state) => ({
      transcript: [...state.transcript, createTurn('assistant', '', true)],
    })),
  appendAssistantToken: (token) =>
    set((state) => {
      const lastTurn = state.transcript[state.transcript.length - 1]
      if (!lastTurn || lastTurn.role !== 'assistant') return state

      return {
        transcript: [
          ...state.transcript.slice(0, -1),
          { ...lastTurn, text: lastTurn.text + token },
        ],
      }
    }),
  finalizeAssistantTurn: () =>
    set((state) => {
      const lastTurn = state.transcript[state.transcript.length - 1]
      if (!lastTurn || lastTurn.role !== 'assistant') return state

      return {
        transcript: [
          ...state.transcript.slice(0, -1),
          { ...lastTurn, isStreaming: false },
        ],
      }
    }),
  discardPendingAssistantTurn: () =>
    set((state) => {
      const lastTurn = state.transcript[state.transcript.length - 1]
      if (!lastTurn || lastTurn.role !== 'assistant') return state

      if (lastTurn.text.length > 0) {
        return {
          transcript: [
            ...state.transcript.slice(0, -1),
            { ...lastTurn, isStreaming: false },
          ],
        }
      }

      return { transcript: state.transcript.slice(0, -1) }
    }),
  reset: () => set({ isOpen: false, transcript: [], conversationId: null }),
}))
