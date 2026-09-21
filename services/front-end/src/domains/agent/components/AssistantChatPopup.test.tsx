import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AssistantChatPopup } from './AssistantChatPopup'
import { useAgentStore } from '../hooks/useAgentStore'

const mockUseAgentChat = vi.fn()

vi.mock('../hooks/useAgentChat', () => ({
  useAgentChat: () => mockUseAgentChat(),
}))

describe('AssistantChatPopup', () => {
  beforeEach(() => {
    useAgentStore.getState().reset()
    useAgentStore.getState().open()
    mockUseAgentChat.mockReturnValue({
      error: null,
      hasSelectedAccount: true,
      isSending: false,
      sendMessage: vi.fn(),
      clearError: vi.fn(),
    })
  })

  it('AssistantChatPopup - empty transcript - renders empty state and composer', () => {
    render(<AssistantChatPopup />)

    expect(screen.getByText('Start a conversation with the assistant.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Assistant message' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send' })).toBeInTheDocument()
  })

  it('AssistantChatPopup - streaming transcript - renders live assistant reply', () => {
    useAgentStore.setState({
      isOpen: true,
      conversationId: 'conv-1',
      transcript: [
        { id: '1', role: 'user', text: 'Hello', isStreaming: false },
        { id: '2', role: 'assistant', text: 'Partial', isStreaming: true },
      ],
    })

    render(<AssistantChatPopup />)

    expect(screen.getByText('Hello')).toBeInTheDocument()
    expect(screen.getByText('Partial')).toBeInTheDocument()
    expect(screen.getByText('Streaming…')).toBeInTheDocument()
  })

  it('AssistantChatPopup - no selected account - renders account prompt', () => {
    mockUseAgentChat.mockReturnValue({
      error: null,
      hasSelectedAccount: false,
      isSending: false,
      sendMessage: vi.fn(),
      clearError: vi.fn(),
    })

    render(<AssistantChatPopup />)

    expect(
      screen.getByText('Select an account in Portfolio before starting a chat.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Assistant message' })).toBeDisabled()
  })

  it('AssistantChatPopup - hook error - renders inline error state', () => {
    mockUseAgentChat.mockReturnValue({
      error: 'Assistant unavailable. Please try again.',
      hasSelectedAccount: true,
      isSending: false,
      sendMessage: vi.fn(),
      clearError: vi.fn(),
    })

    render(<AssistantChatPopup />)

    expect(screen.getByText('Assistant unavailable. Please try again.')).toBeInTheDocument()
  })
})
