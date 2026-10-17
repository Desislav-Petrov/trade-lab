import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AssistantChatPopup } from './AssistantChatPopup'
import { useAgentStore } from '../hooks/useAgentStore'

const mockUseAgentChat = vi.fn()

vi.mock('../hooks/useAgentChat', () => ({
  useAgentChat: () => mockUseAgentChat(),
}))

const { summaryState } = vi.hoisted(() => ({
  summaryState: { summary: null as string | null },
}))

vi.mock('../hooks/useConversationSummary', () => ({
  useConversationSummary: () => ({
    summary: summaryState.summary,
    isLoading: false,
    refetch: vi.fn(),
  }),
}))

describe('AssistantChatPopup', () => {
  beforeEach(() => {
    useAgentStore.getState().reset()
    useAgentStore.getState().open()
    summaryState.summary = null
    mockUseAgentChat.mockReturnValue({
      error: null,
      hasSelectedAccount: true,
      isSending: false,
      sendMessage: vi.fn(),
      clearError: vi.fn(),
    })
  })

  it('AssistantChatPopup - empty transcript and no summary - renders empty state and composer', () => {
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

  it('AssistantChatPopup - summary present on open - seeds the summary as an opening greeting', async () => {
    summaryState.summary = 'Earlier you reviewed your AAPL position and unrealised P&L.'

    render(<AssistantChatPopup />)

    expect(
      await screen.findByText('Earlier you reviewed your AAPL position and unrealised P&L.'),
    ).toBeInTheDocument()
    expect(
      screen.queryByText('Start a conversation with the assistant.'),
    ).not.toBeInTheDocument()
  })

  it('AssistantChatPopup - no prior summary on open - opens empty with no greeting', () => {
    summaryState.summary = null

    render(<AssistantChatPopup />)

    expect(screen.getByText('Start a conversation with the assistant.')).toBeInTheDocument()
  })

  it('AssistantChatPopup - summary present but transcript already has turns - does not seed a greeting', () => {
    summaryState.summary = 'A carried-forward summary.'
    useAgentStore.setState({
      isOpen: true,
      conversationId: 'conv-1',
      transcript: [{ id: '1', role: 'user', text: 'Hello', isStreaming: false }],
    })

    render(<AssistantChatPopup />)

    expect(screen.queryByText('A carried-forward summary.')).not.toBeInTheDocument()
    expect(screen.getByText('Hello')).toBeInTheDocument()
  })

  it('AssistantChatPopup - New conversation clicked - clears the transcript and opens a blank history', async () => {
    const user = userEvent.setup()
    summaryState.summary = 'A carried-forward summary.'
    useAgentStore.setState({
      isOpen: true,
      conversationId: 'conv-1',
      transcript: [{ id: '1', role: 'user', text: 'Hello', isStreaming: false }],
    })

    render(<AssistantChatPopup />)

    await user.click(screen.getByRole('button', { name: 'New conversation' }))

    expect(useAgentStore.getState().conversationId).toBeNull()
    expect(useAgentStore.getState().transcript).toHaveLength(0)
    expect(screen.queryByText('A carried-forward summary.')).not.toBeInTheDocument()
    expect(screen.getByText('Start a conversation with the assistant.')).toBeInTheDocument()
  })

  it('AssistantChatPopup - reopen after New conversation - greets with the carried-forward summary again', async () => {
    const user = userEvent.setup()
    summaryState.summary = 'A carried-forward summary.'
    useAgentStore.setState({
      isOpen: true,
      conversationId: 'conv-1',
      transcript: [{ id: '1', role: 'user', text: 'Hello', isStreaming: false }],
    })

    render(<AssistantChatPopup />)

    await user.click(screen.getByRole('button', { name: 'New conversation' }))
    expect(screen.queryByText('A carried-forward summary.')).not.toBeInTheDocument()

    act(() => {
      useAgentStore.getState().open()
    })

    expect(await screen.findByText('A carried-forward summary.')).toBeInTheDocument()
  })

  it('AssistantChatPopup - Enter key with a message - sends the trimmed message', async () => {
    const user = userEvent.setup()
    const sendMessage = vi.fn().mockResolvedValue(true)
    mockUseAgentChat.mockReturnValue({
      error: null,
      hasSelectedAccount: true,
      isSending: false,
      sendMessage,
      clearError: vi.fn(),
    })

    render(<AssistantChatPopup />)

    const input = screen.getByRole('textbox', { name: 'Assistant message' })
    await user.type(input, 'What is my P&L?{Enter}')

    expect(sendMessage).toHaveBeenCalledWith('What is my P&L?')
  })
})
